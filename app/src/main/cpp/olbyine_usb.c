#include <jni.h>
#include <errno.h>
#include <fcntl.h>
#include <math.h>
#include <pthread.h>
#include <stdatomic.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/resource.h>
#include <unistd.h>
#include <linux/usbdevice_fs.h>
#include <linux/usb/ch9.h>
#include <android/log.h>

#define TAG "OlbyineUsb"
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)

#define DATA_URBS 6
#define FEEDBACK_URBS 2
#define MAX_PACKETS_PER_URB 64

enum { IN_S16 = 1, IN_S24 = 2, IN_S32 = 3, IN_FLOAT = 4 };

typedef struct {
    struct usbdevfs_urb *urb;
    uint8_t *buffer;
    int real_frames;
    int epoch;
    int is_feedback;
} slot_t;

typedef struct {
    int fd;
    int speed;

    int ep;
    int max_packet;
    int packets_per_sec;
    int packets_per_urb;
    int frame_bytes;
    int dev_subslot;
    int dev_bits;
    int dev_channels;
    int rate;
    int in_rate;
    double rs_pos;
    int32_t rs_prev[8];
    int rs_have_prev;
    int fb_ep;
    int fb_size;
    int fb_high_speed;
    int fb_microframes;
    int dither;
    uint32_t rng;

    uint8_t *ring;
    size_t ring_frames;
    _Atomic size_t write_pos;
    _Atomic size_t read_pos;

    double nominal_fpp;
    _Atomic double feedback_fpp;
    double accumulator;

    _Atomic int running;
    _Atomic int paused;
    _Atomic int failed;
    _Atomic long long frames_played;
    _Atomic int underruns;
    _Atomic int flush_request;
    _Atomic size_t flush_target;
    _Atomic int epoch;

    pthread_t thread;
    int thread_started;
    slot_t data[DATA_URBS];
    slot_t feedback[FEEDBACK_URBS];
    char error[160];
} stream_t;

static void set_error(stream_t *s, const char *what, int err) {
    snprintf(s->error, sizeof(s->error), "%s: %s (%d)", what, strerror(err), err);
    LOGW("%s", s->error);
}

static inline int32_t read_sample(const uint8_t *p, int encoding) {
    switch (encoding) {
        case IN_S16: return (int32_t)((uint32_t)(uint16_t)(p[0] | (p[1] << 8)) << 16);
        case IN_S24: return (int32_t)(((uint32_t)p[0] << 8) | ((uint32_t)p[1] << 16) | ((uint32_t)p[2] << 24));
        case IN_S32: return (int32_t)((uint32_t)p[0] | ((uint32_t)p[1] << 8) | ((uint32_t)p[2] << 16) | ((uint32_t)p[3] << 24));
        case IN_FLOAT: {
            float f;
            memcpy(&f, p, 4);
            double v = (double)f * 2147483648.0;
            if (v >= 2147483647.0) return INT32_MAX;
            if (v <= -2147483648.0) return INT32_MIN;
            return (int32_t)lrint(v);
        }
        default: return 0;
    }
}

static inline int in_bytes(int encoding) {
    switch (encoding) {
        case IN_S16: return 2;
        case IN_S24: return 3;
        default: return 4;
    }
}

static inline int32_t apply_gain(int32_t v, float gain) {
    if (gain >= 0.99999f) return v;
    double scaled = (double)v * (double)gain;
    return (int32_t)lrint(scaled);
}

static inline uint32_t next_random(uint32_t *state) {
    uint32_t x = *state;
    x ^= x << 13;
    x ^= x >> 17;
    x ^= x << 5;
    *state = x;
    return x;
}

static inline int32_t quantize(stream_t *s, int32_t v) {
    int bits = s->dev_bits;
    if (bits <= 0 || bits >= 32) return v;
    uint32_t low = 0xFFFFFFFFu >> bits;
    if (((uint32_t)v & low) == 0) return v;
    int64_t lsb = (int64_t)1 << (32 - bits);
    int64_t out = (int64_t)v + lsb / 2;
    if (s->dither) {
        int64_t a = (int64_t)(next_random(&s->rng) & (uint32_t)(lsb - 1));
        int64_t b = (int64_t)(next_random(&s->rng) & (uint32_t)(lsb - 1));
        out += a - b;
    }
    if (out > INT32_MAX) out = INT32_MAX;
    if (out < INT32_MIN) out = INT32_MIN;
    return (int32_t)out;
}

static inline void write_sample(uint8_t *out, int32_t v, int subslot, int bits) {
    if (bits > 0 && bits < 32) {
        uint32_t mask = 0xFFFFFFFFu << (32 - bits);
        v = (int32_t)((uint32_t)v & mask);
    }
    uint32_t u = (uint32_t)v;
    switch (subslot) {
        case 2:
            out[0] = (uint8_t)(u >> 16);
            out[1] = (uint8_t)(u >> 24);
            break;
        case 3:
            out[0] = (uint8_t)(u >> 8);
            out[1] = (uint8_t)(u >> 16);
            out[2] = (uint8_t)(u >> 24);
            break;
        default:
            out[0] = (uint8_t)u;
            out[1] = (uint8_t)(u >> 8);
            out[2] = (uint8_t)(u >> 16);
            out[3] = (uint8_t)(u >> 24);
            break;
    }
}

static int frames_for_next_packet(stream_t *s) {
    double fpp = atomic_load(&s->feedback_fpp);
    if (fpp <= 0.0) fpp = s->nominal_fpp;
    s->accumulator += fpp;
    int frames = (int)s->accumulator;
    s->accumulator -= frames;
    int max_frames = s->max_packet / s->frame_bytes;
    if (frames > max_frames) frames = max_frames;
    if (frames < 0) frames = 0;
    return frames;
}

static void fill_data_urb(stream_t *s, slot_t *slot) {
    struct usbdevfs_urb *urb = slot->urb;
    int paused = atomic_load(&s->paused);
    size_t read = atomic_load_explicit(&s->read_pos, memory_order_relaxed);
    if (atomic_exchange(&s->flush_request, 0)) {
        size_t target = atomic_load(&s->flush_target);
        if (target > read) read = target;
    }
    size_t write = atomic_load_explicit(&s->write_pos, memory_order_acquire);
    size_t available = paused ? 0 : write - read;
    int offset = 0;
    int real = 0;
    for (int i = 0; i < s->packets_per_urb; i++) {
        int frames = frames_for_next_packet(s);
        int bytes = frames * s->frame_bytes;
        uint8_t *dst = slot->buffer + offset;
        int copy = 0;
        while (copy < frames && available > 0) {
            size_t index = (read % s->ring_frames);
            size_t run = s->ring_frames - index;
            size_t want = (size_t)(frames - copy);
            if (run > want) run = want;
            if (run > available) run = available;
            memcpy(dst + copy * s->frame_bytes, s->ring + index * s->frame_bytes, run * s->frame_bytes);
            copy += (int)run;
            read += run;
            available -= run;
        }
        if (copy < frames) {
            memset(dst + copy * s->frame_bytes, 0, (size_t)(frames - copy) * s->frame_bytes);
            if (!paused && copy < frames && write != read) atomic_fetch_add(&s->underruns, 1);
        }
        real += copy;
        urb->iso_frame_desc[i].length = (unsigned int)bytes;
        urb->iso_frame_desc[i].actual_length = 0;
        urb->iso_frame_desc[i].status = 0;
        offset += bytes;
    }
    atomic_store_explicit(&s->read_pos, read, memory_order_release);
    slot->real_frames = real;
    slot->epoch = atomic_load(&s->epoch);
    urb->type = USBDEVFS_URB_TYPE_ISO;
    urb->endpoint = (unsigned char)s->ep;
    urb->flags = USBDEVFS_URB_ISO_ASAP;
    urb->buffer = slot->buffer;
    urb->buffer_length = offset;
    urb->number_of_packets = s->packets_per_urb;
    urb->actual_length = 0;
    urb->status = 0;
    urb->usercontext = slot;
}

static void fill_feedback_urb(stream_t *s, slot_t *slot) {
    struct usbdevfs_urb *urb = slot->urb;
    memset(slot->buffer, 0, 8);
    urb->type = USBDEVFS_URB_TYPE_ISO;
    urb->endpoint = (unsigned char)s->fb_ep;
    urb->flags = USBDEVFS_URB_ISO_ASAP;
    urb->buffer = slot->buffer;
    urb->buffer_length = s->fb_size;
    urb->number_of_packets = 1;
    urb->iso_frame_desc[0].length = (unsigned int)s->fb_size;
    urb->iso_frame_desc[0].actual_length = 0;
    urb->iso_frame_desc[0].status = 0;
    urb->actual_length = 0;
    urb->status = 0;
    urb->usercontext = slot;
}

static void apply_feedback(stream_t *s, const uint8_t *data, int length) {
    if (length < 3) return;
    uint32_t raw = (uint32_t)data[0] | ((uint32_t)data[1] << 8) | ((uint32_t)data[2] << 16);
    if (length >= 4) raw |= (uint32_t)data[3] << 24;
    double candidates[2];
    int count = 0;
    if (s->fb_high_speed) {
        candidates[count++] = (double)raw / 65536.0 * s->fb_microframes;
        candidates[count++] = (double)raw / 65536.0;
    } else {
        candidates[count++] = (double)(raw & 0xFFFFFF) / 16384.0;
        if (length >= 4) candidates[count++] = (double)raw / 65536.0;
    }
    for (int i = 0; i < count; i++) {
        double v = candidates[i];
        if (v > s->nominal_fpp * 0.9 && v < s->nominal_fpp * 1.1) {
            double previous = atomic_load(&s->feedback_fpp);
            double smoothed = previous > 0.0 ? previous + (v - previous) * 0.25 : v;
            atomic_store(&s->feedback_fpp, smoothed);
            return;
        }
    }
}

static int submit(stream_t *s, slot_t *slot) {
    if (ioctl(s->fd, USBDEVFS_SUBMITURB, slot->urb) < 0) {
        int err = errno;
        set_error(s, "submit", err);
        return -err;
    }
    return 0;
}

static void *stream_thread(void *arg) {
    stream_t *s = (stream_t *)arg;
    int in_flight = 0;
    setpriority(PRIO_PROCESS, 0, -19);
    for (int i = 0; i < DATA_URBS; i++) {
        fill_data_urb(s, &s->data[i]);
        if (submit(s, &s->data[i]) < 0) { atomic_store(&s->failed, 1); goto done; }
        in_flight++;
    }
    if (s->fb_ep) {
        for (int i = 0; i < FEEDBACK_URBS; i++) {
            fill_feedback_urb(s, &s->feedback[i]);
            if (submit(s, &s->feedback[i]) < 0) { s->fb_ep = 0; break; }
            in_flight++;
        }
    }
    while (atomic_load(&s->running)) {
        struct usbdevfs_urb *done_urb = NULL;
        if (ioctl(s->fd, USBDEVFS_REAPURB, &done_urb) < 0) {
            int err = errno;
            if (err == EINTR) continue;
            set_error(s, "reap", err);
            atomic_store(&s->failed, 1);
            break;
        }
        if (!done_urb) continue;
        in_flight--;
        slot_t *slot = (slot_t *)done_urb->usercontext;
        if (!atomic_load(&s->running)) break;
        if (slot->is_feedback) {
            if (done_urb->status == 0 && done_urb->iso_frame_desc[0].status == 0) {
                apply_feedback(s, slot->buffer, (int)done_urb->iso_frame_desc[0].actual_length);
            }
            if (!s->fb_ep) continue;
            fill_feedback_urb(s, slot);
            if (submit(s, slot) < 0) { s->fb_ep = 0; continue; }
            in_flight++;
            continue;
        }
        if (slot->epoch == atomic_load(&s->epoch)) atomic_fetch_add(&s->frames_played, slot->real_frames);
        fill_data_urb(s, slot);
        if (submit(s, slot) < 0) {
            atomic_store(&s->failed, 1);
            break;
        }
        in_flight++;
    }
done:
    for (int i = 0; i < DATA_URBS; i++) ioctl(s->fd, USBDEVFS_DISCARDURB, s->data[i].urb);
    for (int i = 0; i < FEEDBACK_URBS; i++) if (s->feedback[i].urb) ioctl(s->fd, USBDEVFS_DISCARDURB, s->feedback[i].urb);
    for (int spins = 0; in_flight > 0 && spins < 400;) {
        struct usbdevfs_urb *done_urb = NULL;
        if (ioctl(s->fd, USBDEVFS_REAPURBNDELAY, &done_urb) == 0) {
            in_flight--;
            continue;
        }
        if (errno != EAGAIN) break;
        usleep(1000);
        spins++;
    }
    return NULL;
}
static slot_t make_slot(int packets, int bytes, int feedback) {
    slot_t slot;
    memset(&slot, 0, sizeof(slot));
    slot.urb = (struct usbdevfs_urb *)calloc(1, sizeof(struct usbdevfs_urb) + packets * sizeof(struct usbdevfs_iso_packet_desc));
    slot.buffer = (uint8_t *)calloc(1, (size_t)(bytes > 8 ? bytes : 8));
    slot.is_feedback = feedback;
    return slot;
}

static void free_stream(stream_t *s) {
    if (!s) return;
    for (int i = 0; i < DATA_URBS; i++) { free(s->data[i].urb); free(s->data[i].buffer); }
    for (int i = 0; i < FEEDBACK_URBS; i++) { free(s->feedback[i].urb); free(s->feedback[i].buffer); }
    free(s->ring);
    if (s->fd >= 0) close(s->fd);
    free(s);
}

#define JNI(name) Java_com_olbyine_music_playback_usb_UsbAudioNative_##name

JNIEXPORT jint JNICALL JNI(nativeSpeed)(JNIEnv *env, jclass clazz, jint fd) {
    int speed = ioctl(fd, USBDEVFS_GET_SPEED, 0);
    return speed < 0 ? -errno : speed;
}

JNIEXPORT jint JNICALL JNI(nativeReattach)(JNIEnv *env, jclass clazz, jint fd, jint interface_number) {
    struct usbdevfs_ioctl command;
    memset(&command, 0, sizeof(command));
    command.ifno = interface_number;
    command.ioctl_code = USBDEVFS_CONNECT;
    command.data = NULL;
    return ioctl(fd, USBDEVFS_IOCTL, &command) < 0 ? -errno : 0;
}

JNIEXPORT jlong JNICALL JNI(nativeStart)(
    JNIEnv *env, jclass clazz, jint fd, jint endpoint, jint max_packet, jint packets_per_sec,
    jint subslot, jint bits, jint channels, jint rate, jint in_rate, jint fb_endpoint, jint fb_size,
    jint fb_high_speed, jint fb_microframes, jint ring_ms, jint dither) {
    if (subslot < 2 || subslot > 4 || channels < 1 || channels > 8 || rate < 8000 || rate > 768000 ||
        packets_per_sec < 1000 || max_packet <= 0) {
        return 0;
    }
    stream_t *s = (stream_t *)calloc(1, sizeof(stream_t));
    if (!s) return 0;
    s->fd = dup(fd);
    if (s->fd < 0) { free(s); return 0; }
    s->ep = endpoint;
    s->max_packet = max_packet;
    s->packets_per_sec = packets_per_sec;
    s->dev_subslot = subslot;
    s->dev_bits = bits;
    s->dev_channels = channels;
    s->frame_bytes = subslot * channels;
    s->rate = rate;
    s->in_rate = in_rate > 0 ? in_rate : rate;
    s->fb_ep = fb_endpoint;
    s->fb_size = fb_size > 0 ? fb_size : 3;
    s->fb_high_speed = fb_high_speed;
    s->fb_microframes = fb_microframes > 0 ? fb_microframes : 1;
    s->dither = dither ? 1 : 0;
    s->rng = 0x9E3779B9u ^ (uint32_t)rate;
    s->nominal_fpp = (double)rate / (double)packets_per_sec;
    atomic_store(&s->feedback_fpp, 0.0);
    if (s->nominal_fpp * s->frame_bytes > max_packet + s->frame_bytes) {
        snprintf(s->error, sizeof(s->error), "packet too small for %d Hz", rate);
        free_stream(s);
        return 0;
    }
    int ppu = packets_per_sec / 125;
    if (ppu < 1) ppu = 1;
    if (ppu > MAX_PACKETS_PER_URB) ppu = MAX_PACKETS_PER_URB;
    s->packets_per_urb = ppu;
    int urb_bytes = ppu * max_packet;
    for (int i = 0; i < DATA_URBS; i++) {
        s->data[i] = make_slot(ppu, urb_bytes, 0);
        if (!s->data[i].urb || !s->data[i].buffer) { free_stream(s); return 0; }
    }
    if (s->fb_ep) {
        for (int i = 0; i < FEEDBACK_URBS; i++) {
            s->feedback[i] = make_slot(1, 8, 1);
            if (!s->feedback[i].urb || !s->feedback[i].buffer) { free_stream(s); return 0; }
        }
    }
    s->ring_frames = (size_t)((long long)rate * (ring_ms > 40 ? ring_ms : 40) / 1000);
    s->ring = (uint8_t *)calloc(s->ring_frames, (size_t)s->frame_bytes);
    if (!s->ring) { free_stream(s); return 0; }
    atomic_store(&s->running, 1);
    atomic_store(&s->paused, 1);
    if (pthread_create(&s->thread, NULL, stream_thread, s) != 0) {
        free_stream(s);
        return 0;
    }
    s->thread_started = 1;
    return (jlong)(intptr_t)s;
}

JNIEXPORT jint JNICALL JNI(nativeWrite)(
    JNIEnv *env, jclass clazz, jlong handle, jobject buffer, jint offset, jint length,
    jint encoding, jint in_channels, jfloat gain) {
    stream_t *s = (stream_t *)(intptr_t)handle;
    if (!s || atomic_load(&s->failed)) return -1;
    uint8_t *base = (uint8_t *)(*env)->GetDirectBufferAddress(env, buffer);
    if (!base) return -2;
    int sample_bytes = in_bytes(encoding);
    int in_frame = sample_bytes * in_channels;
    if (in_frame <= 0) return -3;
    size_t frames_in = (size_t)length / (size_t)in_frame;
    size_t write = atomic_load_explicit(&s->write_pos, memory_order_relaxed);
    size_t read = atomic_load_explicit(&s->read_pos, memory_order_acquire);
    size_t space = s->ring_frames - (write - read);
    if (s->in_rate != s->rate) {
        double step = (double)s->in_rate / (double)s->rate;
        size_t max_in = space > 2 ? (size_t)((double)(space - 2) * step) : 0;
        if (frames_in > max_in) frames_in = max_in;
        if (frames_in == 0) return 0;
        const uint8_t *src = base + offset;
        size_t out = 0;
        double pos = s->rs_pos;
        for (;;) {
            long i0 = (long)floor(pos);
            double frac = pos - (double)i0;
            if (i0 + 1 >= (long)frames_in) break;
            if (i0 < 0 && !s->rs_have_prev) { pos += step; continue; }
            uint8_t *dst = s->ring + ((write + out) % s->ring_frames) * s->frame_bytes;
            for (int c = 0; c < s->dev_channels; c++) {
                int ch = in_channels == 1 ? 0 : (c < in_channels ? c : -1);
                int32_t a, b;
                if (ch < 0) { a = 0; b = 0; }
                else {
                    a = i0 < 0 ? s->rs_prev[ch] : read_sample(src + (size_t)i0 * in_frame + ch * sample_bytes, encoding);
                    b = read_sample(src + (size_t)(i0 + 1) * in_frame + ch * sample_bytes, encoding);
                }
                int32_t v = (int32_t)lrint((double)a + ((double)b - (double)a) * frac);
                write_sample(dst + c * s->dev_subslot, quantize(s, apply_gain(v, gain)), s->dev_subslot, s->dev_bits);
            }
            out++;
            pos += step;
        }
        const uint8_t *last = src + (frames_in - 1) * in_frame;
        for (int c = 0; c < in_channels && c < 8; c++) s->rs_prev[c] = read_sample(last + c * sample_bytes, encoding);
        s->rs_have_prev = 1;
        s->rs_pos = pos - (double)frames_in;
        atomic_store_explicit(&s->write_pos, write + out, memory_order_release);
        return (jint)(frames_in * in_frame);
    }
    size_t frames = frames_in < space ? frames_in : space;
    const uint8_t *src = base + offset;
    for (size_t f = 0; f < frames; f++) {
        uint8_t *dst = s->ring + ((write + f) % s->ring_frames) * s->frame_bytes;
        const uint8_t *in = src + f * in_frame;
        for (int c = 0; c < s->dev_channels; c++) {
            int32_t v;
            if (in_channels == 1) {
                v = read_sample(in, encoding);
            } else if (c < in_channels) {
                v = read_sample(in + c * sample_bytes, encoding);
            } else {
                v = 0;
            }
            if (s->dev_channels == 1 && in_channels >= 2) {
                int64_t mix = ((int64_t)read_sample(in, encoding) + read_sample(in + sample_bytes, encoding)) / 2;
                v = (int32_t)mix;
            }
            write_sample(dst + c * s->dev_subslot, quantize(s, apply_gain(v, gain)), s->dev_subslot, s->dev_bits);
        }
    }
    atomic_store_explicit(&s->write_pos, write + frames, memory_order_release);
    return (jint)(frames * in_frame);
}

JNIEXPORT void JNICALL JNI(nativeSetPaused)(JNIEnv *env, jclass clazz, jlong handle, jboolean paused) {
    stream_t *s = (stream_t *)(intptr_t)handle;
    if (s) atomic_store(&s->paused, paused ? 1 : 0);
}

JNIEXPORT void JNICALL JNI(nativeFlush)(JNIEnv *env, jclass clazz, jlong handle) {
    stream_t *s = (stream_t *)(intptr_t)handle;
    if (!s) return;
    s->rs_pos = 0.0;
    s->rs_have_prev = 0;
    atomic_store(&s->flush_target, atomic_load(&s->write_pos));
    atomic_fetch_add(&s->epoch, 1);
    atomic_store(&s->frames_played, 0);
    atomic_store(&s->flush_request, 1);
}

JNIEXPORT jlong JNICALL JNI(nativeFramesPlayed)(JNIEnv *env, jclass clazz, jlong handle) {
    stream_t *s = (stream_t *)(intptr_t)handle;
    return s ? (jlong)atomic_load(&s->frames_played) : 0;
}

JNIEXPORT jlong JNICALL JNI(nativeFramesQueued)(JNIEnv *env, jclass clazz, jlong handle) {
    stream_t *s = (stream_t *)(intptr_t)handle;
    if (!s) return 0;
    return (jlong)(atomic_load(&s->write_pos) - atomic_load(&s->read_pos));
}

JNIEXPORT jlong JNICALL JNI(nativeCapacityFrames)(JNIEnv *env, jclass clazz, jlong handle) {
    stream_t *s = (stream_t *)(intptr_t)handle;
    return s ? (jlong)s->ring_frames : 0;
}

JNIEXPORT jint JNICALL JNI(nativeUnderruns)(JNIEnv *env, jclass clazz, jlong handle) {
    stream_t *s = (stream_t *)(intptr_t)handle;
    return s ? atomic_load(&s->underruns) : 0;
}

JNIEXPORT jboolean JNICALL JNI(nativeFailed)(JNIEnv *env, jclass clazz, jlong handle) {
    stream_t *s = (stream_t *)(intptr_t)handle;
    return (jboolean)(s ? atomic_load(&s->failed) : 1);
}

JNIEXPORT jdouble JNICALL JNI(nativeMeasuredRate)(JNIEnv *env, jclass clazz, jlong handle) {
    stream_t *s = (stream_t *)(intptr_t)handle;
    if (!s) return 0;
    double fpp = atomic_load(&s->feedback_fpp);
    return fpp > 0 ? fpp * s->packets_per_sec : 0;
}

JNIEXPORT jstring JNICALL JNI(nativeError)(JNIEnv *env, jclass clazz, jlong handle) {
    stream_t *s = (stream_t *)(intptr_t)handle;
    return (*env)->NewStringUTF(env, s ? s->error : "");
}

JNIEXPORT void JNICALL JNI(nativeStop)(JNIEnv *env, jclass clazz, jlong handle) {
    stream_t *s = (stream_t *)(intptr_t)handle;
    if (!s) return;
    atomic_store(&s->running, 0);
    if (s->thread_started) {
        for (int i = 0; i < DATA_URBS; i++) ioctl(s->fd, USBDEVFS_DISCARDURB, s->data[i].urb);
        for (int i = 0; i < FEEDBACK_URBS; i++) if (s->feedback[i].urb) ioctl(s->fd, USBDEVFS_DISCARDURB, s->feedback[i].urb);
        pthread_join(s->thread, NULL);
    }
    free_stream(s);
}
