# AMLL-DroidMate source notice

The AMLL catalog search and mirror retrieval flow in
`app/src/main/kotlin/com/auramusic/player/data/OnlineLyricsRepository.kt` is adapted
from `LyricsRepository.kt` in [AMLL-DroidMate](https://github.com/Zeehan2005/AMLL-DroidMate),
commit `4f525252c70f0e0e0cb3c4c96c6ab4c0459f4a20`.

The upstream project is licensed under GNU Affero General Public License,
version 3. A copy is included in `AMLL-DroidMate-LICENSE.md`.

The adapted portion uses the upstream Bikonoo `api/search-lyrics` request and
its `platform`/`file` response fields, then checks the upstream mirror paths.
The HTTP client, result mapping, cancellation and Android storage integration
were changed to fit this application. The official AMLL API is an additional
fallback.
