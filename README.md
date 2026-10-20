# Olbyine

Olbyine 是一个 Android 本地与流媒体音乐播放器，使用 Kotlin、Jetpack Compose 和 AndroidX Media3 构建。

Android 10+ · arm64-v8a / armeabi-v7a · AGPL-3.0

## 下载

最新版本见 [Releases](https://github.com/Huliang2597/olbyine-music/releases)。

- 安装包：`Olbyine-1.1-release.apk`
- 包名：`com.olbyine.music`
- 需要 Android 10（API 29）及以上

## 功能

### 音乐库

- 通过系统文件选择器扫描文件夹，按文件夹、专辑、歌单、收藏浏览，支持字母索引与多种排序
- 专辑三种视图：网格、Cover Flow、球形（可旋转）
- **作曲人分类**：读取音频文件内嵌的作曲人标签（ID3 多个 TCOM 帧、FLAC Vorbis Comment），同一位作曲人自动合并；没有标签的由网易云补全，卡片显示艺名、详情页显示本名
- 歌曲信息编辑，以及从网易云 / QQ 音乐 / Apple Music 匹配封面、简介与发行日期
- 多选、批量收藏、批量加入歌单、批量补全信息
- 播放记忆：队列、歌单与进度持久化，重启后可以回到当初播放它的歌单 / 专辑 / 文件夹

### 在线音源

- **网易云音乐**：登录后可使用我的音乐、每日推荐、排行榜、搜索、歌单 / 专辑 / 艺人页、评论、MV、播客、私信，听歌记录同步回账号
- **洛雪音乐（LX Music）**：搜索、推荐歌单、排行榜、我的歌单四个分页；支持 `.lxmc` 歌单导入导出、手动新建歌单、自定义源脚本；音质 128k / 192k / 320k / FLAC / FLAC 24bit（无损依赖自定义源）
- **SoundCloud**：发现、搜索、动态、私信、喜欢、转发、关注、加入歌单
- **Navidrome / Subsonic**：浏览专辑、艺人、歌单与收藏，在线播放并记录播放次数
- **WebDAV / NAS**：Alist、群晖、Nextcloud、坚果云等，可在线播放或导入音乐库

所有来源共用同一个播放队列，可以在播放页直接切换。

### 下载

- 网易云歌曲一键下载，带进度通知
- 自动写入标题、艺术家、专辑等标签，内嵌封面
- 歌词保存在音频同目录，文件名与音频一致
- 文件统一存放在 `Download/Olbyine`
- 下载管理页可查看已下载歌曲、占用空间，支持删除单曲或清空

### 歌词

- 逐字歌词（内置 AMLL 歌词引擎），支持 TTML / LRC / QRC
- 翻译、罗马音独立开关；可调字号、字重、时间偏移与高亮位置
- MV 歌词动画：跃动、倾斜、排版三种形态，横竖屏自动适配
- 歌词外显：桌面悬浮歌词（可拖动、缩放、控制播放）、状态栏、锁屏、通知、桌面小组件、车载歌词
- 在线歌词自动匹配，也可以手动导入本地歌词文件

### 播放与音频

- 10 段均衡器：预设、手动调节、前级增益、频响曲线、声道平衡
- 歌曲过渡：淡入淡出与自动衔接，切歌没有空白
- USB 独占输出：绕过系统混音，按原采样率输出，支持位完美
- 输出模式可选标准 / 高解析
- 播放队列、下一首播放、随机与循环、倍速播放
- 动态封面、律动背景、玻璃质感播放控件

### 外观

- 四种界面风格：iOS、ColorOS、澎湃 OS、原生 Android，每种风格有独立的图标集
- 明暗主题、玻璃材质强度、自定义背景、应用图标
- 手机与平板两套布局，平板侧边栏包含音乐库分类、洛雪音乐与各在线音源入口

### 其他

- 备份与恢复：把配置和登录信息导出为文件，或备份到 WebDAV
- 自动检查更新：从本仓库的 Releases 检查新版本
- 触感反馈：可逐项选择哪些操作带震动
- 监听其他播放器：读取通知，在 Olbyine 里显示外部播放器的歌词

## 构建

需要 Android SDK 36 和 JDK 17（Windows 上使用 `gradlew.bat`，其他平台使用 `./gradlew`）：

```bash
./gradlew :app:assembleRelease
```

产物在 `app/build/outputs/apk/release/`。

歌词引擎静态资源位于 `app/src/native/assets/amll/`。如需从源码重建，需要 Node.js 和 pnpm：

```bash
cd lyrics-engine
pnpm install --frozen-lockfile
pnpm run build
```

USB 音频引擎的原生库源码位于 `app/src/main/cpp/`，预编译产物在 `app/src/main/jniLibs/`。

## 许可

本项目内置并修改了 AMLL（Apple Music-like Lyrics，AGPL-3.0），整体以 **GNU AGPL-3.0** 发布，见 `LICENSE`。
第三方组件的来源与许可见 `THIRD_PARTY_NOTICES.md` 与 `licenses/`，应用内「设置 → 关于」也列出了全部参考与使用的开源项目。

音乐、歌词与封面的权利属于其权利人。本项目与网易云音乐、SoundCloud、Navidrome 均无关联。
