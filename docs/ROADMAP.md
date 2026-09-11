# MuMu Roadmap

> 一个住在手机里的桌宠。Kotlin + WebView 悬浮窗做壳，Supabase 做同步，情绪引擎做魂。

## 分阶段计划

每阶段结束都会停下来 review，确认能看懂、想不想改，再往前走。

| 阶段 | 目标 | 产出 |
| --- | --- | --- |
| 1 | 骨架 | Android 工程 + WebView 悬浮窗，能加载一个本地 HTML 桌宠 |
| 2 | 后端同步 | Supabase 表结构（pet_state / pet_memory / pet_events）+ 同步逻辑 |
| 3 | 情绪引擎 | 情绪状态机（心情值 / 精力值 / 亲密度），随时间与互动变化 |
| 4 | 像素素材 | SVG 像素风素材，待机 / 开心 / 难过 / 睡觉 等动作 |
| 5 | CI/CD | GitHub Actions 自动构建并产出 APK |

## 约定

- 所有密钥只进 `.env` / GitHub Secrets，绝不写进仓库。
- 前端只使用 Supabase publishable key。
- 每阶段一个独立分支，走 PR 合并到 main。
