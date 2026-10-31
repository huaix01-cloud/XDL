# XDL — X（Twitter）图片 / 视频下载器

自用 Android App。粘一条推文链接 → 解析 → 勾选 → 存进相册。无广告、无账号、无后端。

**成品：`D:\scrapkit\xdl\dist\XDL-1.2.1.apk`（8.1 MB，minSdk 29 / Android 10+）**

> 1.2.1 按 V5 设计稿对齐布局；App 内不再配置代理，只做只读网络检测。
> 源码在 `D:\scrapkit\xdl\`，接手开发先看 [交接文档.md](交接文档.md)。

---

## 一、先说「有没有现成的可以接着做」

| 项目 | 是什么 | 能不能直接用 | 坑 |
|---|---|---|---|
| [Seal](https://github.com/JunkFood02/Seal) | Kotlin + Compose 的 yt-dlp 图形壳，支持 X/YouTube/B站等上千站 | 能，装上就下 | 体积大（带 yt-dlp + ffmpeg/aria2 组件），X 解析跟着 yt-dlp 走 |
| [YTDLnis](https://github.com/deniscerri/ytdlnis) | 同类 yt-dlp 前端 | 能 | 配置项多 |
| [Dobby](https://github.com/echoeyecodes/Dobby) | 原生 Android，Twitter/Instagram/DeviantArt | 要自己编 | 停更，X 接口早变了 |
| Twitter Media Downloader（浏览器扩展，Elyx0） | 电脑端最成熟 | 电脑能用 | 不是 Android App |

结论：要「装上就能用」→ 装 Seal；要「轻、无依赖、自己掌控」→ 这个。yt-dlp 系在 X 上会被 graphql 变更集体打死，走公开解析 API 没有这个耦合。

---

## 二、界面（1.2 按设计稿 V4 重做）

五个屏幕，Material 3 Expressive 风格（紫调 + 大圆角 + 分层容器色）。

| 屏幕 | 内容 |
|---|---|
| **主页** | 顶栏（主题切换 / XDL 标签 / 代理开关）、网络状态条、链接输入、粘贴/清空/解析、平台选择、画质选择、解析结果卡片（2 列媒体网格）、下载 splitButton、右下 FAB 进设置 |
| **下载中** | 分页（下载中/已完成/失败）、任务行带环形百分比、尾部 ✕ 删除、底部「处理」（重试/删除记录/批量暂停/批量开始） |
| **已完成** | 任务行显示文件类型与大小，尾部 ↗ 用 ACTION_VIEW 打开文件，底部「管理」（删除记录/打开文件位置） |
| **失败** | 红色错误原因（截断 90 字），尾部 ✕ 删除，底部「处理」（重试/删除记录） |
| **设置** | 下载通知 / 仅 WiFi 下载 / 最多同时下载 / 保存位置；主题（浅/深/跟随系统）/ 动态颜色；HTTP 代理；关于与版本 |

**环形百分比进度**：解析时显示 60dp 环形，中间直接写百分比；下载中每行 44dp 小环。

**主题**：浅色 / 深色 / 跟随系统三态，可在设置页选，也可点主页左上角图标快速切。
**动态颜色**：Android 12+ 可开，跟随壁纸取色。

截图见 `docs/10-home-light.png`、`docs/11-home-dark.png`、`docs/12-settings.png`、`docs/13-tasks.png`、`docs/14-failed.png`。

---

## 三、网络状态检测（只读，1.2.1 起不配置代理）

不是「访问一个域名看看通不通」，而是三层信号各用最合适的来源：

| 层 | 手段 | 特点 |
|---|---|---|
| 在线状态 | `ConnectivityManager.NetworkCallback` + `NET_CAPABILITY_VALIDATED` | 系统级、零流量、被动实时，能区分 WiFi / 移动网络 |
| 代理是否在跑 | 对代理端口做一次 TCP 握手（1200ms 超时） | 毫秒级、不产生 HTTP 流量，代理软件挂了立刻能看出来 |
| 能不能真正解析 | 对 `api.fxtwitter.com` 发一次 GET（8s 超时） | 只在启动 / 点检测 / 回前台时跑 |

为什么不用「只访问域名」那一种：每次都占流量、一个 RTT 起步，而且分不清「没网 / 代理没开 / 代理开了但走不通」——这三种故障的处置方式完全不同。

状态条直接给结论：`无网络` / `代理未响应` / `代理已连接` / `直连可用` / `解析不可达`，并附本次探测耗时。

---

## 四、平台区分（仅 UI，暂不接新平台）

`Platform` 枚举：**X**（可用）/ 哔哩哔哩（搁置）/ 抖音（搁置）/ 快手（搁置）。

选非 X 时：解析按钮禁用 + 提示「接口还没接，先做平台区分」。下载任务里也带平台字段，文件名会加平台前缀（如 `b站_xxx_1.mp4`），方便以后扩展。

---

## 五、功能

- **四种进入方式**：X 分享菜单 / 点开推文链接 / 选中文字「处理文本」/ 直接粘贴
- **解析**：头像、昵称、正文、媒体数量
- **多图**：2 列网格、默认全选、点选取消
- **图片**：抓 `?name=orig` 原图
- **视频**：按画质档位挑 mp4（最佳/1080p/720p/480p），勾了「保持选择最佳画质」就强制最高
- **下载队列**：并发数可调（1~4），支持暂停/继续/重试/删除，前台服务保活（可选通知）
- **保存**：MediaStore，图片→`Pictures/XDL/`，视频→`Movies/XDL/`，同名自动 `_1` `_2`
- **代理**：内置 HTTP 代理 + 总开关 + 按域名自动降级
- **日志**：查看 / 复制 / 导出到 `Download/XDL/`

## 六、装法

```
adb install -r D:\scrapkit\xdl\dist\XDL-1.2.apk
```

release 用 debug 签名，覆盖安装不会冲突。

## 七、代理怎么填

X 的媒体在 `video.twimg.com`（Fastly）和 `pbs.twimg.com`（Cloudflare），前者国内直连基本不通。

- **手机开 Clash / V2Ray 的 VPN（TUN）模式** → 代理留空
- **只有 Clash 的 HTTP 端口** → 填 `127.0.0.1:7890`

全角冒号/句号会被自动纠正。

> **实测坑 1**：Android 的全局 `http_proxy` 对 Java 网络栈不生效，`HttpURLConnection` 根本不读，必须 App 内自己带 `Proxy`。
>
> **实测坑 2**：代理软件按域名分流。遇到过 `api.fxtwitter.com` 被分到死节点而 `pbs.twimg.com` 正常；反过来直连时 fx 通、pbs 被 DNS 污染到 `31.13.95.48`。所以按域名降级：某域名走代理失败一次，就标记为直连，本次运行不再走代理，其他域名不受影响。

## 八、自己改 / 重新编译

不用 Android Studio，工具链在 `D:\scrapkit\xtools\`：

```
D:\scrapkit\xdl\build.ps1 assembleDebug     # 调试包（21.7 MB）
D:\scrapkit\xdl\build.ps1 assembleRelease   # 发布包，编完自动归档到 dist\
```

**1.2 起 release 开了 R8**（`isMinifyEnabled + isShrinkResources`），体积从 17.3 MB 降到 8.1 MB。

**代码结构**（全 Kotlin，零第三方网络库）：

```
java/com/yg/xdl/
├── MainActivity.kt     入口 + 导航 + 代理弹窗
├── Theme.kt            M3 配色（紫）/ Expressive 形状 / 排版 / 动态色
├── Settings.kt         全局设置（持久化 + Compose state）
├── Platform.kt         平台枚举（X / B站 / 抖音 / 快手）
├── NetworkState.kt     网络 + 代理三层检测
├── DownloadCenter.kt   下载队列（任务模型 + 并发执行）
├── DownloadNotifier.kt 通知 + 前台服务启动
├── DownloadService.kt  只负责保活
├── XParser.kt          链接提取 + 双源解析 + 画质选择 + 降级重试
├── Downloader.kt       单文件落盘（MediaStore）
├── NetConfig.kt        代理读写 + 按域名降级
├── Log.kt              日志落盘/轮转/导出
├── ImageCache.kt       LruCache 缩略图
└── ui/
    ├── HomeScreen.kt    屏幕 1（状态提升到 App，切页不丢结果）
    ├── TasksScreen.kt   屏幕 2/3/4
    ├── SettingsScreen.kt 屏幕 5
    └── Widgets.kt       RingProgress / SelectField / SplitActionButton / TaskRow
```

依赖只有 Compose + coroutines。**图标不用 material-icons-extended**（2000+ 图标、几十 MB、下载还会卡），只自绘 8 个 vector，其余用 `material-icons-core` 自带的基础图标。

## 九、已知限制

- 只吃单条推文，不做时间线批量抓（要批量用 gallery-dl）
- 不合并 m3u8 分片：`formats[]` 里有 mp4 就用 mp4，没有的超长视频会失败
- 不登录：受保护账号 / 已删除推文解析不了
- 不解析引用推文的媒体
- 代理只支持 HTTP，不支持 SOCKS5
- 「仅 WiFi 下载」目前只存了开关状态，还没在下载前拦截
- 日志含完整 URL，分享前留意

## 十、验收记录（模拟器 Pixel 10 Pro / Android 16）

| 项目 | 结果 |
|---|---|
| 解析 | SpaceX 推文 → 头像/正文/2 张缩略图/尺寸角标全对 |
| 环形进度 | 解析时显示 92% → 100%，下载行显示 0~100% |
| 网络检测 | 「代理已连接 1556ms」绿点；代理坏时显示「代理未响应」 |
| 下载队列 | 2 个任务入队 → 并发执行 → 失败进失败页（当时网络原因） |
| 状态保留 | 进设置再返回，解析结果与勾选状态都还在 |
| 主题 | 浅/深切换正常，切完写进 SharedPreferences |
| release | R8 裁剪后 8.1 MB，安装启动无 crash、无 FATAL |

## 十一、版本

| 版本 | 内容 |
|---|---|
| 1.2 | UI 全量重做（M3 Expressive / 5 屏 / 紫调 / 深浅 / 动态色）、环形百分比进度、网络+代理三层检测、平台区分、下载队列（并发/暂停/重试）、前台服务通知、画质档位、R8 裁剪 8.1 MB |
| 1.1 | 日志（查看/复制/导出/清空）、按域名代理降级、下载字节校验 |
| 1.0 | 首版（APK 已按用户要求删除） |

每版归档在 `dist\XDL-<版本>.apk`。
