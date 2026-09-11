# MuMu

> 一个住在手机里的桌宠。桌面悬浮、会开心会闹脾气、记得你。

## 这是什么

MuMu 是一个 Android 桌面宠物应用：

- **外壳**：Kotlin + 前台服务 + 悬浮窗（WebView），常驻桌面不掉
- **身体**：像素风 SVG 素材，待机 / 开心 / 难过 / 睡觉 / 被戳
- **灵魂**：本地情绪引擎，心情值、精力值、亲密度随时间和互动变化
- **记忆**：Supabase 存状态与长期记忆，换设备也认得你

## 技术栈

| 层 | 选型 |
| --- | --- |
| Android | Kotlin, Foreground Service, WindowManager Overlay, WebView |
| 前端 | HTML / CSS / JS（跑在 WebView 里，离线优先） |
| 后端 | Supabase（Postgres + Auth + Edge Functions） |
| 构建 | Gradle + GitHub Actions（自动出 APK） |

## 目录规划

```
app/                 Android 应用（Kotlin）
  src/main/java/     悬浮窗服务、WebView 容器、情绪引擎
  src/main/assets/   web/ 前端页面与素材
web/                 前端源码（HTML/CSS/JS/SVG）
supabase/            数据库迁移与 Edge Functions
docs/                设计文档与路线图
```

## 路线图

见 [`docs/ROADMAP.md`](docs/ROADMAP.md)。五个阶段：骨架 → 后端同步 → 情绪引擎 → 像素素材 → CI/CD。

## 安全约定

- 仓库内 **不存任何密钥**。密钥走 `.env`（本地）和 GitHub Secrets（CI）。
- 客户端只使用 Supabase 的 publishable key，不使用 service role key。

## License

待定。
