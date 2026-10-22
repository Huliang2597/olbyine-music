import { LyricPlayer } from "@applemusic-like-lyrics/core";
import { parseTTML } from "@applemusic-like-lyrics/lyric";
import "@applemusic-like-lyrics/core/style.css";
import "./style.css";

const host = document.querySelector("#lyrics");
const fontRule = document.createElement("style");
fontRule.id = "olbyine-custom-font";
document.head.appendChild(fontRule);
const player = new LyricPlayer();
host.appendChild(player.getElement());
player.setAlignPosition(0.5);
player.setWordFadeWidth(0.5);
player.setOptimizeOptions({
  normalizeSpaces: false,
  resetLineTimestamps: false,
  tryAdvanceStartTime: false,
  cleanUnintentionalOverlaps: false,
});
player.pause();

let documentVersion = 0;
let isSynchronized = true;
let playback = {
  positionMs: 0,
  receivedAt: performance.now(),
  isPlaying: false,
  playbackSpeed: 1,
};
let lastFrameAt = null;
let lastRenderedPosition = 0;
let frameRequest = 0;
let animateUntil = 0;
let reducedMotion = false;
let lineBlur = true;
let alignPosition = 0.5;
let showTranslation = true;
let showRoman = true;
/** The current document's lines as parsed, before the translation / romanization settings. */
let documentLines = null;

/** Lines with the sub lines the settings turn off emptied (an empty sub line takes no space). */
function withSubLines(lines) {
  if (showTranslation && showRoman) return lines;
  return lines.map((line) => ({
    ...line,
    translatedLyric: showTranslation ? line.translatedLyric : "",
    romanLyric: showRoman ? line.romanLyric : "",
  }));
}

function requestUpdate(settleMs = 0) {
  animateUntil = Math.max(animateUntil, performance.now() + settleMs);
  if (frameRequest === 0 && !document.hidden) {
    frameRequest = requestAnimationFrame(frame);
  }
}

function inputObject(value) {
  if (typeof value === "string") {
    try {
      return JSON.parse(value);
    } catch (error) {
      console.error("Invalid Olbyine lyrics payload", error);
      return {};
    }
  }
  return value && typeof value === "object" ? value : {};
}

function numberOr(value, fallback) {
  const n = Number(value);
  return Number.isFinite(n) ? n : fallback;
}

function lyricTime(now = performance.now()) {
  const elapsed = playback.isPlaying ? Math.max(0, now - playback.receivedAt) : 0;
  return Math.max(0, Math.round(playback.positionMs + elapsed * playback.playbackSpeed));
}

function normalizeLines(input) {
  const source = Array.isArray(input) ? input : [];
  const lines = source.map((line, index) => {
    const next = source[index + 1];
    const startTime = Math.max(0, Math.round(numberOr(line.startTime ?? line.startMs, 0)));
    const nextStart = numberOr(next?.startTime ?? next?.startMs, startTime + 4000);
    const rawWords = Array.isArray(line.words) ? line.words : [];
    const words = rawWords.map((entry) => {
      const word = String(entry.word ?? entry.text ?? "");
      const wordStart = Math.max(0, Math.round(numberOr(entry.startTime ?? entry.startMs, startTime)));
      const wordEnd = Math.max(
        wordStart + 1,
        Math.round(numberOr(entry.endTime ?? entry.endMs, nextStart)),
      );
      return { word, startTime: wordStart, endTime: wordEnd };
    }).filter((entry) => entry.word.length > 0);
    const lastWordEnd = words.reduce((end, word) => Math.max(end, word.endTime), startTime);
    const endTime = Math.max(
      startTime + 1,
      lastWordEnd,
      Math.round(numberOr(line.endTime ?? line.endMs, nextStart)),
    );
    if (words.length === 0) {
      words.push({
        word: String(line.text ?? line.word ?? ""),
        startTime,
        endTime,
      });
    }
    return {
      words,
      startTime,
      endTime,
      translatedLyric: String(line.translatedLyric ?? line.translation ?? ""),
      romanLyric: String(line.romanLyric ?? line.romanization ?? ""),
      isBG: Boolean(line.isBG),
      isDuet: Boolean(line.isDuet),
    };
  });
  return lines.sort((a, b) => a.startTime - b.startTime);
}

async function setDocument(value) {
  const data = inputObject(value);
  const version = ++documentVersion;
  isSynchronized = data.isSynchronized !== false;
  let lines;
  const rawTtml = data.rawTtml ?? data.ttml;
  if (typeof rawTtml === "string" && rawTtml.trim()) {
    try {
      const parsed = await Promise.resolve(parseTTML(rawTtml));
      lines = parsed.lines;
    } catch (error) {
      console.error("AMLL TTML parsing failed; using supplied lines", error);
    }
  }
  if (!Array.isArray(lines)) lines = normalizeLines(data.lines);
  if (version !== documentVersion) return;
  documentLines = lines;
  const now = lyricTime();
  player.setLyricLines(withSubLines(lines), now);
  player.setCurrentTime(now, true);
  player.update(0);
  lastRenderedPosition = now;
  requestUpdate(1200);
}

function setPlayback(value) {
  const data = inputObject(value);
  const now = performance.now();
  const previousPosition = lyricTime(now);
  const positionMs = Math.max(0, Math.round(numberOr(data.positionMs, previousPosition)));
  const speed = Math.max(0.01, numberOr(data.playbackSpeed, 1));
  const isPlaying = Boolean(data.isPlaying);
  const seek = Boolean(data.seek);
  const wasPlaying = playback.isPlaying;
  playback = { positionMs, receivedAt: now, isPlaying, playbackSpeed: speed };
  // DomLyricPlayer traverses all lyric lines on resume/pause. Only transition once.
  if (isPlaying !== wasPlaying) {
    if (isPlaying) player.resume();
    else player.pause();
  }
  player.setCurrentTime(positionMs, seek);
  lastRenderedPosition = positionMs;
  requestUpdate(seek ? 1200 : 0);
}

function setStyle(value) {
  const data = inputObject(value);
  const fontSize = Math.max(18, Math.min(64, numberOr(data.fontSize, 32)));
  const foreground = String(data.foreground ?? "#ffffff");
  const opacity = Math.max(0.08, Math.min(0.9, numberOr(data.inactiveOpacity, 0.28)));
  const weight = Math.max(100, Math.min(900, Math.round(numberOr(data.fontWeight, 700))));
  const version = String(data.fontVersion ?? "");
  host.style.setProperty("--olbyine-font-size", `${fontSize}px`);
  host.style.setProperty("--olbyine-font-weight", String(weight));
  if (/^[A-Za-z0-9._-]{1,100}$/.test(version)) {
    const collection = version.toLowerCase().endsWith(".ttc");
    const format = collection ? ' format("collection")' : "";
    fontRule.textContent = `@font-face { font-family: "Olbyine Custom"; src: url("./custom-font?v=${encodeURIComponent(version)}")${format}; font-style: normal; font-weight: 100 900; font-display: swap; }`;
    host.style.setProperty("--olbyine-font-family", '"Olbyine Custom", system-ui, sans-serif');
  } else {
    fontRule.textContent = "";
    host.style.removeProperty("--olbyine-font-family");
  }
  if (CSS.supports("color", foreground)) {
    host.style.setProperty("--olbyine-foreground", foreground);
  }
  host.style.setProperty("--olbyine-inactive-opacity", String(opacity));
  host.style.setProperty(
    "--olbyine-blend-mode",
    data.blendMode === "normal" ? "normal" : "plus-lighter",
  );
  // Reduced motion (the system's "remove animations", passed by the app): lines move by plain
  // transitions instead of springs and do not scale. Only touched when it changes.
  const reduced =
    data.reduceMotion === true || matchMedia("(prefers-reduced-motion: reduce)").matches;
  if (reduced !== reducedMotion) {
    reducedMotion = reduced;
    player.setEnableSpring(!reduced);
    player.setEnableScale(!reduced);
  }
  // Depth-of-field blur on the surrounding lines: a setting, off by default (each blurred line is
  // its own filtered layer, redrawn every frame — the main cost of word-by-word lyrics here).
  const blur = data.lineBlur === true;
  if (blur !== lineBlur) {
    lineBlur = blur;
    player.setEnableBlur(blur);
  }
  // Translation / romanization lines (settings): the document is laid out again without them.
  const translation = data.showTranslation !== false;
  const roman = data.showRoman !== false;
  if (translation !== showTranslation || roman !== showRoman) {
    showTranslation = translation;
    showRoman = roman;
    if (documentLines) {
      const now = lyricTime();
      player.setLyricLines(withSubLines(documentLines), now);
      player.setCurrentTime(now, true);
      player.update(0);
    }
  }
  // Translation / romanization size, relative to the main line (its own setting).
  const subScale = Math.max(0.3, Math.min(1, numberOr(data.subScale, 0.5)));
  host.style.setProperty("--olbyine-sub-scale", String(subScale));
  const lineGap = Math.max(1, Math.min(2.4, numberOr(data.lineGap, 1.2)));
  host.style.setProperty("--olbyine-line-gap", String(lineGap));
  const align = Math.max(0.15, Math.min(0.85, numberOr(data.alignPosition, 0.5)));
  if (align !== alignPosition) {
    alignPosition = align;
    player.setAlignPosition(align);
    const alignTime = lyricTime();
    player.setCurrentTime(alignTime, true);
    player.update(0);
    requestUpdate(1200);
  }
  tapSeek = data.tapSeek === "single" ? "single" : "double";
  requestUpdate(600);
}

function callAndroid(method, argument) {
  const bridge = window.OlbyineAndroid;
  try {
    if (typeof bridge?.[method] === "function") {
      if (argument === undefined) bridge[method]();
      else bridge[method](argument);
    }
  } catch (error) {
    console.error(`OlbyineAndroid.${method} failed`, error);
  }
}

// Seeking takes a double tap on the same line (a single tap is easy to land while scrolling);
// a lone tap still reaches the page after the double-tap window. "single" restores one-tap seek.
let tapSeek = "double";
let lastTap = null;
player.addEventListener("line-click", (event) => {
  // AMLL propagates this stop to the originating DOM click, avoiding a second blank tap.
  event.stopPropagation();
  if (!isSynchronized) {
    callAndroid("onTap");
    return;
  }
  const startTime = event.line?.getLine()?.startTime;
  if (!Number.isFinite(startTime)) {
    callAndroid("onTap");
    return;
  }
  if (tapSeek === "single") {
    callAndroid("seekTo", String(Math.round(startTime)));
    return;
  }
  const now = performance.now();
  if (lastTap && lastTap.t === startTime && now - lastTap.at < 420) {
    clearTimeout(lastTap.timer);
    lastTap = null;
    callAndroid("seekTo", String(Math.round(startTime)));
    return;
  }
  if (lastTap) clearTimeout(lastTap.timer);
  lastTap = {
    t: startTime,
    at: now,
    timer: setTimeout(() => {
      lastTap = null;
      callAndroid("onTap");
    }, 300),
  };
});

host.addEventListener("click", () => callAndroid("onTap"));

const touching = new Set();
const pointerStartY = new Map();
let restoreScrollStyle = 0;
host.addEventListener("pointerdown", (event) => {
  clearTimeout(restoreScrollStyle);
  touching.add(event.pointerId);
  pointerStartY.set(event.pointerId, event.clientY);
  requestUpdate(2000);
  if (touching.size === 1) callAndroid("touching", true);
});
host.addEventListener("pointermove", (event) => {
  const startY = pointerStartY.get(event.pointerId);
  if (startY !== undefined && Math.abs(event.clientY - startY) > 10) {
    host.classList.add("olbyine-scrolling");
    requestUpdate(2000);
  }
});
function releasePointer(event) {
  touching.delete(event.pointerId);
  pointerStartY.delete(event.pointerId);
  if (touching.size === 0) {
    callAndroid("touching", false);
    // Keep the inexpensive scroll style through AMLL's inertia animation.
    restoreScrollStyle = setTimeout(() => host.classList.remove("olbyine-scrolling"), 800);
    requestUpdate(2000);
  }
}
host.addEventListener("pointerup", releasePointer);
host.addEventListener("pointercancel", releasePointer);
window.addEventListener("blur", () => {
  touching.clear();
  pointerStartY.clear();
  host.classList.remove("olbyine-scrolling");
  callAndroid("touching", false);
});

function frame(frameTime) {
  frameRequest = 0;
  const delta = lastFrameAt === null ? 0 : Math.min(Math.max(frameTime - lastFrameAt, 0), 100);
  lastFrameAt = frameTime;
  const time = lyricTime(frameTime);
  if (time !== lastRenderedPosition) {
    player.setCurrentTime(time);
    lastRenderedPosition = time;
  }
  player.update(delta);
  if (playback.isPlaying || touching.size > 0 || frameTime < animateUntil) {
    frameRequest = requestAnimationFrame(frame);
  } else {
    lastFrameAt = null;
  }
}

document.addEventListener("visibilitychange", () => {
  lastFrameAt = null;
  if (document.hidden) {
    if (frameRequest !== 0) cancelAnimationFrame(frameRequest);
    frameRequest = 0;
  } else {
    player.setCurrentTime(lyricTime(), true);
    requestUpdate(1200);
  }
});

window.OlbyineLyrics = Object.freeze({ setDocument, setPlayback, setStyle });
requestUpdate(1200);
callAndroid("ready");
