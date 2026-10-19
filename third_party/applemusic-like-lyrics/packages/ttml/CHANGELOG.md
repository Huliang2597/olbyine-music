## 1.0.1 (2026-05-17)

### Patch Changes

- **fix:** 修复因仅匹配起始时间导致的逐字音译错位 ([#533](https://github.com/amll-dev/applemusic-like-lyrics/pull/533))
- **chore:** 更正 package.json 协议声明 ([#534](https://github.com/amll-dev/applemusic-like-lyrics/pull/534))

  仓库根目录的 LICENSE 文件为 AGPL v3.0 协议，但是 package.json 中的 `license` 字段为 `GPL-3.0`。经与原开发者确认，package.json 中的 `license` 字段有误。仓库与其所有产出的 npm 包均应为 AGPL v3 only 协议，SPDX: `AGPL-3.0-only`。因此，更正各包 `package.json` 的 `license` 字段为 `AGPL-3.0-only`。

### Contributors

- apoint123 [@apoint123](https://github.com/apoint123)
- Linho [@Linho1219](https://github.com/Linho1219)

# 1.0.0 (2026-04-23)

### Minor Changes

- **chore(ttml):** 同步上游 ttml 包的更新 ([#489](https://github.com/amll-dev/applemusic-like-lyrics/pull/489))

  https://github.com/apoint123/ttml-processor/compare/86d7dd66a461c8fcd7e5a6b09b6d60daabee752e..af6d35a2404e4b2a85fa36086a39e333c7c3ba07


### Patch Changes

- **feat:** 识别同名 songPart 但不同 div 的情况 ([#490](https://github.com/amll-dev/applemusic-like-lyrics/pull/490))

### Contributors

- apoint123 [@apoint123](https://github.com/apoint123)

## 1.0.0-alpha.0 (2026-04-14)

### Major Changes

- **refactor:** 重构 TTML 解析和生成器 ([#471](https://github.com/amll-dev/applemusic-like-lyrics/pull/471))

### Patch Changes

- **chore:** 更换工具链 ([#476](https://github.com/amll-dev/applemusic-like-lyrics/pull/476))

### Contributors

- apoint123 [@apoint123](https://github.com/apoint123)
- Linho [@Linho1219](https://github.com/Linho1219)
