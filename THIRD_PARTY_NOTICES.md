# Open source notices

## AMLL lyric renderer and TTML parser

- Upstream: https://github.com/amll-dev/applemusic-like-lyrics (author: Steve-xmh and contributors)
- Release tag: `core-bundle@0.6.0` (commit `28e2ba92c57ca5671ee3b5f53c6c289584951578`)
- Packages bundled in the APK: `@applemusic-like-lyrics/core` 0.6.0, `@applemusic-like-lyrics/lyric` 1.1.0, `@applemusic-like-lyrics/ttml` 1.0.1
- License: AGPL-3.0-only; see `LICENSE` and `third_party/applemusic-like-lyrics/LICENSE`
- Upstream source for these packages is under `third_party/applemusic-like-lyrics/packages/`. The Android entry point and styles are under `lyrics-engine/` (exact versions in `lyrics-engine/pnpm-lock.yaml`). The generated bundle in `app/src/native/assets/amll/` carries small edits (reduced motion, double-tap seek, sub-line scale) mirrored in `lyrics-engine/src/main.js`.

## AMLL-DroidMate

- Upstream: https://github.com/Zeehan2005/AMLL-DroidMate (commit `4f525252c70f0e0e0cb3c4c96c6ab4c0459f4a20`)
- License: AGPL-3.0; see `licenses/AMLL-DroidMate-LICENSE.md` and `licenses/AMLL-DroidMate-NOTICE.md`
- `ui/AmllLyricsView.kt` adapts its WebView host; `data/OnlineLyricsRepository.kt` adapts its catalog search and mirror retrieval.

## UniLyric

- Upstream: https://github.com/apoint123/Unilyric — MIT; see `licenses/UniLyric-MIT.txt`
- Lyric provider logic (QQ Music, NetEase, Kugou, AMLL-TTML-DB) re-implemented in Kotlin.

## LDDC

- Upstream: https://github.com/chenmozhijin/LDDC — GPL-3.0
- QQ Music QRC decryption (`data/QrcDecrypter.kt`) ports its Triple-DES variant.

## ALAC audio decoder

- Media3 renderer and adapter from Gramophone (https://github.com/FoedusProgramme/Gramophone, module `misc/alacdecoder`), GPL-3.0-or-later; see `licenses/GPL-3.0.txt`.
- Bitstream decoder from Java-Apple-Lossless-decoder (https://github.com/soiaf/Java-Apple-Lossless-decoder, Peter McQuillan), BSD-3-Clause. Original notices are kept in `alac-decoder/`.

## Liquid glass

- Kyant0/AndroidLiquidGlass (https://github.com/Kyant0/AndroidLiquidGlass), Apache-2.0 — lens, refraction and highlight shaders ported.

## Lyricify

- WXRIW/Lyricify-Lyrics-Helper (Apache-2.0) — lyric matching and Apple Music lyric flow.
- WXRIW/Lyricify-Backgrounds (Apache-2.0) — animated background.

## Icons

- Framework7 Icons (https://github.com/framework7io/framework7-icons), MIT — iOS style.
- Lucide (https://github.com/lucide-icons/lucide), ISC — ColorOS style.
- MingCute (https://github.com/mingcute-design/mingcute-icons), Apache-2.0 — HyperOS style.
- Material Icons (Google), Apache-2.0 — stock Android style.

## Interface references

- React Bits (https://github.com/DavidHDev/react-bits), MIT + Commons Clause — interaction effects ported to Compose.
- shadcn/ui (https://github.com/shadcn-ui/ui), MIT — design reference.
- Folia (https://github.com/chthollyphile/folia-major), AGPL-3.0 — design reference for the MV lyric animations (no code copied).

## Service APIs (reference only)

- NeteaseCloudMusicApi (https://github.com/Binaryify/NeteaseCloudMusicApi) and api-enhanced (https://github.com/NeteaseCloudMusicApiEnhanced/api-enhanced), MIT.
- soundcloud.py (https://github.com/7x11x13/soundcloud.py), MIT.
- Navidrome / OpenSubsonic (https://github.com/navidrome/navidrome), GPL-3.0.

## Libraries

- AndroidX Media3, Jetpack Compose, AndroidX (Apache-2.0), OkHttp (Apache-2.0), ZXing (Apache-2.0), Guava (Apache-2.0), Kotlin coroutines (Apache-2.0).
