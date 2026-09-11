# MuMu

> 一个住在手机里的桌宠，是一只像素小狐狸。桌面悬浮、会开心会闹脾气、记得你。

## 这是什么

MuMu 是一个 Android 桌面宠物应用：

- **外壳**：Kotlin + 前台服务 + 悬浮窗（WebView），常驻桌面不掉
- **身体**：像素小狐狸（canvas 逐像素绘制），待机 / 眨眼 / 开心 / 难过 / 累了 / 困了 / 被戳
- **灵魂**：本地情绪引擎 `MoodEngine`，心情值、精力值、亲密度随时间和互动自动变化
- **记忆**：Supabase 存状态与长期记忆，换设备也认得你

## 它怎么活着

MuMu 的情绪是“算”出来的，不是随机动画：

- 醒着就掉精力，夜里（23:00-06:00）会回精力
- 精力太低会委屈，心情跟着往下掉；被冷落超过 6 小时，心情按小时流失
- 戳它会开心、更黏人，但也会消耗一点精力
- 根据心情和精力，它在 `idle / happy / tired / sad / sleepy` 之间切换，桌面上的表情、滤镜和呼吸眨眼节奏都会跟着变
- 再次打开时，它会把离开这段时间的账一次性结算（单次最多按 24 小时算）

## 怎么用

1. 装好 APK，打开 MuMu
2. 注册或登录一个账号（不登录也能用，只是不同步）
3. 允许「显示在其他应用上层」
4. 点「叫 MuMu 出来」，小狐狸就待在桌面上了
5. 戳它一下，它会跳，心情和亲密度会涨，云端跟着更新；不理它，它会自己变累、变困

## 技术栈

| 层 | 选型 |
| --- | --- |
| Android | Kotlin, Foreground Service, WindowManager Overlay, WebView |
| 前端 | HTML / CSS / JS（跑在 WebView 里，离线优先） |
| 后端 | Supabase（Postgres + Auth + Edge Functions） |
| 构建 | Gradle + GitHub Actions（跑单测 + 自动出 APK） |

云同步层没有引第三方 SDK，直接用 `HttpURLConnection` 打 Supabase 的 Auth 和 PostgREST 接口，依赖面尽量小。
情绪引擎是纯函数，不依赖 Android，靠 JVM 单测保证行为。

## 目录规划

```
app/                 Android 应用（Kotlin）
  src/main/java/     悬浮窗服务、WebView 容器、云同步层、情绪引擎
  src/main/assets/   web/ 前端页面与素材
  src/test/java/     情绪引擎单元测试
web/                 前端源码（HTML/CSS/JS/SVG）
supabase/            数据库迁移与 Edge Functions
docs/                设计文档与路线图
```

## 路线图

见 [`docs/ROADMAP.md`](docs/ROADMAP.md)。五个阶段：骨架 → 后端同步 → 情绪引擎 → 像素素材 → CI/CD。目前五个阶段均已完成。

## 配置（可选）

客户端内置了 Supabase 的 URL 与 publishable key。需要指向别的项目时，在本地 `local.properties` 里写：

```properties
SUPABASE_URL=https://xxx.supabase.co
SUPABASE_PUBLISHABLE_KEY=sb_publishable_xxx
```

或直接给环境变量（CI 用这个）。`local.properties` 已被 `.gitignore` 排除。

## 安全约定

- 仓库内 **不存任何服务端密钥**。`service_role` key 与个人访问令牌绝不进仓库。
- 客户端只用 Supabase 的 publishable key，它本来就是设计给客户端公开使用的。
- 数据库侧所有表开启 RLS，策略统一为 `auth.uid() = user_id`。

## License

待定。
