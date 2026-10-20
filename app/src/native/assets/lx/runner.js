
(function () {
  var handlers = {};
  var httpPending = {};
  var callPending = {};
  var reqSeq = 0;
  var ready = false;
  var sourceMap = {};

  function toBytes(value, encoding) {
    if (value == null) return new Uint8Array(0);
    if (value instanceof Uint8Array) return value;
    if (typeof ArrayBuffer !== 'undefined' && ArrayBuffer.isView && ArrayBuffer.isView(value)) {
      return new Uint8Array(value.buffer, value.byteOffset, value.byteLength);
    }
    if (value instanceof ArrayBuffer) return new Uint8Array(value);
    if (Array.isArray(value)) return new Uint8Array(value);
    if (typeof value === 'string') return encodeString(value, encoding || 'utf8');
    if (value.bytes) return new Uint8Array(value.bytes);
    throw new Error('不支持的 Buffer 输入');
  }

  function encodeString(text, encoding) {
    var enc = String(encoding || 'utf8').toLowerCase();
    if (enc === 'utf8' || enc === 'utf-8') return new TextEncoder().encode(text);
    if (enc === 'hex') {
      var out = new Uint8Array(Math.floor(text.length / 2));
      for (var i = 0; i < out.length; i++) out[i] = parseInt(text.substr(i * 2, 2), 16);
      return out;
    }
    if (enc === 'base64') {
      var bin = atob(text.replace(/\s+/g, ''));
      var arr = new Uint8Array(bin.length);
      for (var j = 0; j < bin.length; j++) arr[j] = bin.charCodeAt(j);
      return arr;
    }
    if (enc === 'latin1' || enc === 'binary') {
      var raw = new Uint8Array(text.length);
      for (var k = 0; k < text.length; k++) raw[k] = text.charCodeAt(k) & 0xff;
      return raw;
    }
    return new TextEncoder().encode(text);
  }

  function decodeBytes(bytes, encoding) {
    var enc = String(encoding || 'utf8').toLowerCase();
    var arr = toBytes(bytes);
    if (enc === 'hex') {
      var hex = '';
      for (var i = 0; i < arr.length; i++) hex += (arr[i] < 16 ? '0' : '') + arr[i].toString(16);
      return hex;
    }
    if (enc === 'base64') {
      var bin = '';
      for (var j = 0; j < arr.length; j++) bin += String.fromCharCode(arr[j]);
      return btoa(bin);
    }
    if (enc === 'latin1' || enc === 'binary') {
      var text = '';
      for (var k = 0; k < arr.length; k++) text += String.fromCharCode(arr[k]);
      return text;
    }
    return new TextDecoder('utf-8').decode(arr);
  }

  function BufferShim(bytes) { this.bytes = toBytes(bytes); }
  BufferShim.prototype.toString = function (encoding) { return decodeBytes(this.bytes, encoding); };
  BufferShim.prototype.toJSON = function () { return Array.from(this.bytes); };
  Object.defineProperty(BufferShim.prototype, 'length', { get: function () { return this.bytes.length; } });

  var bufferUtil = {
    from: function (value, encoding) { return new BufferShim(toBytes(value, encoding)); },
    alloc: function (size, fill) {
      var arr = new Uint8Array(size);
      if (fill != null) arr.fill(typeof fill === 'number' ? fill : 0);
      return new BufferShim(arr);
    },
    concat: function (list) {
      var total = 0, i;
      for (i = 0; i < list.length; i++) total += toBytes(list[i].bytes || list[i]).length;
      var out = new Uint8Array(total), at = 0;
      for (i = 0; i < list.length; i++) {
        var part = toBytes(list[i].bytes || list[i]);
        out.set(part, at);
        at += part.length;
      }
      return new BufferShim(out);
    },
    isBuffer: function (value) { return value instanceof BufferShim; },
    byteLength: function (value, encoding) { return toBytes(value, encoding).length; },
    bufToString: function (buffer, encoding) { return decodeBytes(buffer && buffer.bytes ? buffer.bytes : buffer, encoding); }
  };

  function md5(text) {
    var bytes = typeof text === 'string' ? new TextEncoder().encode(text) : toBytes(text);
    function add32(a, b) { return (a + b) & 0xffffffff; }
    function rol(n, c) { return (n << c) | (n >>> (32 - c)); }
    var S = [7,12,17,22,7,12,17,22,7,12,17,22,7,12,17,22,
             5,9,14,20,5,9,14,20,5,9,14,20,5,9,14,20,
             4,11,16,23,4,11,16,23,4,11,16,23,4,11,16,23,
             6,10,15,21,6,10,15,21,6,10,15,21,6,10,15,21];
    var K = [];
    for (var i = 0; i < 64; i++) K[i] = Math.floor(Math.abs(Math.sin(i + 1)) * 4294967296) & 0xffffffff;
    var len = bytes.length;
    var withPad = new Uint8Array((((len + 8) >> 6) + 1) * 64);
    withPad.set(bytes);
    withPad[len] = 0x80;
    var bitLen = len * 8;
    withPad[withPad.length - 8] = bitLen & 0xff;
    withPad[withPad.length - 7] = (bitLen >>> 8) & 0xff;
    withPad[withPad.length - 6] = (bitLen >>> 16) & 0xff;
    withPad[withPad.length - 5] = (bitLen >>> 24) & 0xff;
    var a0 = 0x67452301, b0 = 0xefcdab89, c0 = 0x98badcfe, d0 = 0x10325476;
    for (var off = 0; off < withPad.length; off += 64) {
      var M = [];
      for (var j = 0; j < 16; j++) {
        var p = off + j * 4;
        M[j] = withPad[p] | (withPad[p + 1] << 8) | (withPad[p + 2] << 16) | (withPad[p + 3] << 24);
      }
      var A = a0, B = b0, C = c0, D = d0;
      for (var k = 0; k < 64; k++) {
        var F, g;
        if (k < 16) { F = (B & C) | (~B & D); g = k; }
        else if (k < 32) { F = (D & B) | (~D & C); g = (5 * k + 1) % 16; }
        else if (k < 48) { F = B ^ C ^ D; g = (3 * k + 5) % 16; }
        else { F = C ^ (B | ~D); g = (7 * k) % 16; }
        F = add32(add32(F, A), add32(K[k], M[g]));
        A = D; D = C; C = B;
        B = add32(B, rol(F, S[k]));
      }
      a0 = add32(a0, A); b0 = add32(b0, B); c0 = add32(c0, C); d0 = add32(d0, D);
    }
    function le(n) {
      return String.fromCharCode(n & 0xff, (n >>> 8) & 0xff, (n >>> 16) & 0xff, (n >>> 24) & 0xff);
    }
    var raw = le(a0) + le(b0) + le(c0) + le(d0);
    var hex = '';
    for (var x = 0; x < raw.length; x++) {
      var code = raw.charCodeAt(x);
      hex += (code < 16 ? '0' : '') + code.toString(16);
    }
    return hex;
  }

  function randomBytes(size) {
    var arr = new Uint8Array(size);
    crypto.getRandomValues(arr);
    return new BufferShim(arr);
  }

  // ---- AES (ECB / CBC, 128/192/256) ----
  var SBOX = [
    0x63,0x7c,0x77,0x7b,0xf2,0x6b,0x6f,0xc5,0x30,0x01,0x67,0x2b,0xfe,0xd7,0xab,0x76,
    0xca,0x82,0xc9,0x7d,0xfa,0x59,0x47,0xf0,0xad,0xd4,0xa2,0xaf,0x9c,0xa4,0x72,0xc0,
    0xb7,0xfd,0x93,0x26,0x36,0x3f,0xf7,0xcc,0x34,0xa5,0xe5,0xf1,0x71,0xd8,0x31,0x15,
    0x04,0xc7,0x23,0xc3,0x18,0x96,0x05,0x9a,0x07,0x12,0x80,0xe2,0xeb,0x27,0xb2,0x75,
    0x09,0x83,0x2c,0x1a,0x1b,0x6e,0x5a,0xa0,0x52,0x3b,0xd6,0xb3,0x29,0xe3,0x2f,0x84,
    0x53,0xd1,0x00,0xed,0x20,0xfc,0xb1,0x5b,0x6a,0xcb,0xbe,0x39,0x4a,0x4c,0x58,0xcf,
    0xd0,0xef,0xaa,0xfb,0x43,0x4d,0x33,0x85,0x45,0xf9,0x02,0x7f,0x50,0x3c,0x9f,0xa8,
    0x51,0xa3,0x40,0x8f,0x92,0x9d,0x38,0xf5,0xbc,0xb6,0xda,0x21,0x10,0xff,0xf3,0xd2,
    0xcd,0x0c,0x13,0xec,0x5f,0x97,0x44,0x17,0xc4,0xa7,0x7e,0x3d,0x64,0x5d,0x19,0x73,
    0x60,0x81,0x4f,0xdc,0x22,0x2a,0x90,0x88,0x46,0xee,0xb8,0x14,0xde,0x5e,0x0b,0xdb,
    0xe0,0x32,0x3a,0x0a,0x49,0x06,0x24,0x5c,0xc2,0xd3,0xac,0x62,0x91,0x95,0xe4,0x79,
    0xe7,0xc8,0x37,0x6d,0x8d,0xd5,0x4e,0xa9,0x6c,0x56,0xf4,0xea,0x65,0x7a,0xae,0x08,
    0xba,0x78,0x25,0x2e,0x1c,0xa6,0xb4,0xc6,0xe8,0xdd,0x74,0x1f,0x4b,0xbd,0x8b,0x8a,
    0x70,0x3e,0xb5,0x66,0x48,0x03,0xf6,0x0e,0x61,0x35,0x57,0xb9,0x86,0xc1,0x1d,0x9e,
    0xe1,0xf8,0x98,0x11,0x69,0xd9,0x8e,0x94,0x9b,0x1e,0x87,0xe9,0xce,0x55,0x28,0xdf,
    0x8c,0xa1,0x89,0x0d,0xbf,0xe6,0x42,0x68,0x41,0x99,0x2d,0x0f,0xb0,0x54,0xbb,0x16
  ];

  function xtime(a) { return ((a << 1) ^ (a & 0x80 ? 0x1b : 0)) & 0xff; }
  function mul(a, b) {
    var res = 0;
    while (b) {
      if (b & 1) res ^= a;
      a = xtime(a);
      b >>= 1;
    }
    return res & 0xff;
  }

  function expandKey(keyBytes) {
    var nk = keyBytes.length / 4;
    if (nk !== 4 && nk !== 6 && nk !== 8) throw new Error('AES 密钥长度必须是 16 / 24 / 32 字节');
    var nr = nk + 6;
    var total = 4 * (nr + 1);
    var w = new Uint8Array(total * 4);
    w.set(keyBytes);
    var rcon = 1;
    for (var i = nk; i < total; i++) {
      var t0 = w[(i - 1) * 4], t1 = w[(i - 1) * 4 + 1], t2 = w[(i - 1) * 4 + 2], t3 = w[(i - 1) * 4 + 3];
      if (i % nk === 0) {
        var tmp = t0;
        t0 = SBOX[t1] ^ rcon; t1 = SBOX[t2]; t2 = SBOX[t3]; t3 = SBOX[tmp];
        rcon = xtime(rcon);
      } else if (nk > 6 && i % nk === 4) {
        t0 = SBOX[t0]; t1 = SBOX[t1]; t2 = SBOX[t2]; t3 = SBOX[t3];
      }
      w[i * 4] = w[(i - nk) * 4] ^ t0;
      w[i * 4 + 1] = w[(i - nk) * 4 + 1] ^ t1;
      w[i * 4 + 2] = w[(i - nk) * 4 + 2] ^ t2;
      w[i * 4 + 3] = w[(i - nk) * 4 + 3] ^ t3;
    }
    return { w: w, rounds: nr };
  }

  // 扁平下标 index = col * 4 + row，与 tiny-AES 的 state[col][row] 一致
  function encryptBlock(s, ks) {
    var i, c, r, tmp;
    for (i = 0; i < 16; i++) s[i] ^= ks.w[i];
    for (var round = 1; round <= ks.rounds; round++) {
      for (i = 0; i < 16; i++) s[i] = SBOX[s[i]];
      tmp = s[1]; s[1] = s[5]; s[5] = s[9]; s[9] = s[13]; s[13] = tmp;
      tmp = s[2]; s[2] = s[10]; s[10] = tmp;
      tmp = s[6]; s[6] = s[14]; s[14] = tmp;
      tmp = s[15]; s[15] = s[11]; s[11] = s[7]; s[7] = s[3]; s[3] = tmp;
      if (round !== ks.rounds) {
        for (c = 0; c < 4; c++) {
          var a0 = s[c * 4], a1 = s[c * 4 + 1], a2 = s[c * 4 + 2], a3 = s[c * 4 + 3];
          s[c * 4] = mul(a0, 2) ^ mul(a1, 3) ^ a2 ^ a3;
          s[c * 4 + 1] = a0 ^ mul(a1, 2) ^ mul(a2, 3) ^ a3;
          s[c * 4 + 2] = a0 ^ a1 ^ mul(a2, 2) ^ mul(a3, 3);
          s[c * 4 + 3] = mul(a0, 3) ^ a1 ^ a2 ^ mul(a3, 2);
        }
      }
      var base = round * 16;
      for (i = 0; i < 16; i++) s[i] ^= ks.w[base + i];
    }
    return s;
  }

  function pkcs7(bytes) {
    var pad = 16 - (bytes.length % 16);
    var out = new Uint8Array(bytes.length + pad);
    out.set(bytes);
    out.fill(pad, bytes.length);
    return out;
  }

  function aesEncrypt(buffer, mode, key, iv) {
    var data = toBytes(buffer && buffer.bytes ? buffer.bytes : buffer);
    var keyBytes = toBytes(key && key.bytes ? key.bytes : key);
    var ivBytes = iv == null ? null : toBytes(iv && iv.bytes ? iv.bytes : iv);
    var spec = String(mode || 'aes-128-cbc').toLowerCase();
    var useCbc = spec.indexOf('cbc') >= 0;
    var ks = expandKey(keyBytes);
    var padded = pkcs7(data);
    var out = new Uint8Array(padded.length);
    var prev = useCbc ? (ivBytes ? ivBytes.slice(0, 16) : new Uint8Array(16)) : null;
    var block = new Uint8Array(16);
    for (var off = 0; off < padded.length; off += 16) {
      for (var i = 0; i < 16; i++) block[i] = padded[off + i] ^ (prev ? prev[i] : 0);
      encryptBlock(block, ks);
      out.set(block, off);
      if (prev) prev.set(block);
    }
    return new BufferShim(out);
  }

  var INV_SBOX = (function () {
    var inv = new Array(256);
    for (var i = 0; i < 256; i++) inv[SBOX[i]] = i;
    return inv;
  })();

  function decryptBlock(s, ks) {
    var i, c, tmp;
    var base = ks.rounds * 16;
    for (i = 0; i < 16; i++) s[i] ^= ks.w[base + i];
    for (var round = ks.rounds - 1; round >= 0; round--) {
      tmp = s[13]; s[13] = s[9]; s[9] = s[5]; s[5] = s[1]; s[1] = tmp;
      tmp = s[2]; s[2] = s[10]; s[10] = tmp;
      tmp = s[6]; s[6] = s[14]; s[14] = tmp;
      tmp = s[3]; s[3] = s[7]; s[7] = s[11]; s[11] = s[15]; s[15] = tmp;
      for (i = 0; i < 16; i++) s[i] = INV_SBOX[s[i]];
      var rb = round * 16;
      for (i = 0; i < 16; i++) s[i] ^= ks.w[rb + i];
      if (round > 0) {
        for (c = 0; c < 4; c++) {
          var a0 = s[c * 4], a1 = s[c * 4 + 1], a2 = s[c * 4 + 2], a3 = s[c * 4 + 3];
          s[c * 4] = mul(a0, 14) ^ mul(a1, 11) ^ mul(a2, 13) ^ mul(a3, 9);
          s[c * 4 + 1] = mul(a0, 9) ^ mul(a1, 14) ^ mul(a2, 11) ^ mul(a3, 13);
          s[c * 4 + 2] = mul(a0, 13) ^ mul(a1, 9) ^ mul(a2, 14) ^ mul(a3, 11);
          s[c * 4 + 3] = mul(a0, 11) ^ mul(a1, 13) ^ mul(a2, 9) ^ mul(a3, 14);
        }
      }
    }
    return s;
  }

  function aesDecrypt(buffer, mode, key, iv) {
    var data = toBytes(buffer && buffer.bytes ? buffer.bytes : buffer);
    var keyBytes = toBytes(key && key.bytes ? key.bytes : key);
    var ivBytes = iv == null ? null : toBytes(iv && iv.bytes ? iv.bytes : iv);
    var spec = String(mode || 'aes-128-cbc').toLowerCase();
    var useCbc = spec.indexOf('cbc') >= 0;
    var ks = expandKey(keyBytes);
    var out = new Uint8Array(data.length);
    var prev = useCbc ? (ivBytes ? ivBytes.slice(0, 16) : new Uint8Array(16)) : null;
    var block = new Uint8Array(16);
    for (var off = 0; off < data.length; off += 16) {
      var cipher = data.subarray(off, off + 16);
      block.set(cipher);
      decryptBlock(block, ks);
      for (var i = 0; i < 16; i++) out[off + i] = block[i] ^ (prev ? prev[i] : 0);
      if (prev) prev.set(cipher);
    }
    var pad = out.length ? out[out.length - 1] : 0;
    if (pad > 0 && pad <= 16) out = out.subarray(0, out.length - pad);
    return new BufferShim(out);
  }

  var utils = {
    buffer: bufferUtil,
    crypto: {
      md5: function (text) { return md5(text); },
      aesEncrypt: aesEncrypt,
      aesDecrypt: aesDecrypt,
      randomBytes: randomBytes,
      rsaEncrypt: function () { throw new Error('当前不支持 rsaEncrypt'); }
    }
  };

  function resolvePath(obj, path) {
    var parts = String(path).split('.');
    var cur = obj;
    for (var i = 0; i < parts.length; i++) {
      if (cur == null) return undefined;
      cur = cur[parts[i]];
    }
    return cur;
  }

  window.lx = {
    version: 2,
    env: 'mobile',
    EVENT_NAMES: { request: 'request', inited: 'inited', updateAlert: 'updateAlert' },
    on: function (name, handler) { handlers[name] = handler; },
    send: function (name, data) {
      if (name === 'inited') {
        var status = !data || data.status !== false;
        var payload = data && data.sources ? data.sources : {};
        var message = data && data.message ? String(data.message) : '';
        try { window.__lxReportInited(JSON.stringify(payload), status, message); } catch (e) {}
        return;
      }
      try { OlbyineLx.send(String(name), JSON.stringify(data == null ? null : data)); } catch (e) {}
    },
    request: function (url, options, callback) {
      var id = ++reqSeq;
      httpPending[id] = callback;
      try { OlbyineLx.request(id, String(url), JSON.stringify(options || {})); } catch (e) {
        delete httpPending[id];
        if (callback) callback(e, null, null);
      }
      return function () { try { OlbyineLx.cancel(id); } catch (e) {} delete httpPending[id]; };
    },
    utils: utils
  };
  window.Buffer = BufferShim;
  window.__lxOnHttp = function (id, statusCode, bodyText, headersJson) {
    var cb = httpPending[id];
    if (!cb) return;
    delete httpPending[id];
    var body = bodyText;
    try { body = JSON.parse(bodyText); } catch (e) {}
    var headers = {};
    try { headers = JSON.parse(headersJson || '{}'); } catch (e) {}
    cb(null, { statusCode: statusCode, headers: headers, body: body, bytes: bodyText.length }, body);
  };
  window.__lxOnHttpError = function (id, message) {
    var cb = httpPending[id];
    if (!cb) return;
    delete httpPending[id];
    cb(new Error(message), null, null);
  };
  window.__lxReportInited = function (sourcesJson, status, message) {
    sourceMap = {};
    try {
      var map = JSON.parse(sourcesJson || '{}');
      for (var key in map) if (Object.prototype.hasOwnProperty.call(map, key)) sourceMap[key] = map[key];
    } catch (e) {}
    ready = status !== false;
    OlbyineLx.inited(sourcesJson, status !== false, String(message || ''));
  };
  window.onerror = function (message, source, line) {
    try { OlbyineLx.runtimeError(String(message) + (line ? '（第 ' + line + ' 行）' : '')); } catch (e) {}
    return false;
  };
  window.addEventListener('unhandledrejection', function (event) {
    var reason = event && event.reason;
    try { OlbyineLx.runtimeError('未处理的异常：' + ((reason && reason.message) || String(reason))); } catch (e) {}
  });
  window.__lxInited = function () { return ready; };
  window.__lxCallMusicUrl = function (callId, source, type, musicInfoJson) {
    var handler = handlers['request'];
    if (!handler) { OlbyineLx.reject(callId, '脚本没有注册 request 事件'); return; }
    var info;
    try { info = JSON.parse(musicInfoJson); } catch (e) { info = {}; }
    Promise.resolve()
      .then(function () { return handler({ source: source, action: 'musicUrl', info: { type: type, musicInfo: info } }); })
      .then(function (url) {
        if (typeof url === 'string' && url) OlbyineLx.resolve(callId, url);
        else OlbyineLx.reject(callId, '脚本没有返回播放地址');
      })
      .catch(function (err) { OlbyineLx.reject(callId, (err && err.message) || String(err)); });
  };
  window.__lxBoot = function (code) {
    try {
      (new Function(code))();
    } catch (e) {
      OlbyineLx.bootError((e && e.message) || String(e));
    }
  };
  OlbyineLx.ready();
})();
