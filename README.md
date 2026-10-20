# Olbyine

Olbyine 是一个 Android 本地与流媒体音乐播放器，使用 Kotlin、Jetpack Compose 和 AndroidX Media3 构建。

> **关于本项目**：Olbyine 基于 [Olbyine Music](https://github.com/Huliang2597/olbyine-music) 继续维护，沿用 Olbyine 这个名称。
> 上游项目的全部功能、架构与代码基础都来自 Olbyine Music，本分支在此基础上继续维护与调整，上游若继续更新会按需同步。
> 遇到的问题请先确认是否在本分支上出现，再决定反馈给上游还是这里。

## 功能

- 本地音乐库：文件夹扫描、专辑（网格 / Cover Flow / 球形）、歌单、收藏、字母索引、WAV / AIFF / ALAC 等格式
- 网易云音乐账号：我的音乐、发现、排行榜、搜索、私信、评论、MV、播客、听歌排行、收藏专辑、歌单管理、听歌记录同步
- SoundCloud：发现、搜索、动态、私信、喜欢、转发、关注、加入歌单
- Navidrome / Subsonic 服务器：专辑、艺人、歌单、收藏、搜索、在线播放与播放记录
- WebDAV / NAS 在线播放与导入
- 逐字歌词（AMLL 歌词引擎）、翻译与罗马音、MV 歌词动画（跃动 / 倾斜 / 排版）
- 桌面悬浮歌词、状态栏歌词、锁屏与通知歌词、桌面歌词小组件
- 四种界面风格：iOS、ColorOS、澎湃 OS、原生 Android，每种风格有独立的图标集
- 10 段均衡器、歌曲过渡、USB 独占输出（位完美）

## 构建

需要 Android SDK 36 和 JDK 17（Windows 上使用 `gradlew.bat`，其他平台使用 `./gradlew`）：

```bash
./gradlew :app:assembleRelease
```

歌词引擎静态资源位于 `app/src/native/assets/amll/`。如需从源码重建，需要 Node.js 和 pnpm：

```bash
cd lyrics-engine
pnpm install --frozen-lockfile
pnpm run build
```

USB 音频引擎的原生库源码位于 `app/src/main/cpp/`，预编译产物在 `app/src/main/jniLibs/`。

### 签名

源码不包含签名密钥。要构建签名后的 release 包，在仓库根目录放一个 `keystore.properties`（已被 `.gitignore` 忽略）：

```properties
storeFile=keystore/olbyine-x-release.jks
storePassword=你的密码
keyAlias=olbyine-x
keyPassword=你的密码
```

没有该文件时 `assembleRelease` 会回退到 debug 签名。

## 许可

本项目内置并修改了 AMLL（Apple Music-like Lyrics，AGPL-3.0），整体以 **GNU AGPL-3.0** 发布，见 `LICENSE`。
第三方组件的来源与许可见 `THIRD_PARTY_NOTICES.md` 与 `licenses/`，应用内「设置 → 关于」也列出了全部参考与使用的开源项目。

音乐、歌词与封面的权利属于其权利人。本项目与网易云音乐、SoundCloud、Navidrome 均无关联。
