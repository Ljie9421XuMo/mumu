# MuMu Roadmap

> 一个住在手机里的桌宠。Kotlin + WebView 悬浮窗做壳，Supabase 做同步，情绪引擎做魂。

## 进度

| 阶段 | 目标 | 状态 |
| --- | --- | --- |
| 1 | 骨架 | ✅ 完成（编译通过） |
| 2 | 后端同步 | ✅ 完成（三张表 + RLS + 登录 + 状态读写 + 事件上报） |
| 3 | 情绪引擎 | ⏳ 待开始 |
| 4 | 像素素材 | ✅ 完成（橘色像素小狐狸） |
| 5 | CI/CD | ✅ 完成（Actions 自动出 debug APK） |

## 分阶段计划

| 阶段 | 目标 | 产出 |
| --- | --- | --- |
| 1 | 骨架 | Android 工程 + WebView 悬浮窗，能加载一个本地 HTML 桌宠 |
| 2 | 后端同步 | Supabase 表结构（pet_state / pet_memory / pet_events）+ 同步逻辑 |
| 3 | 情绪引擎 | 情绪状态机（心情值 / 精力值 / 亲密度），随时间与互动变化 |
| 4 | 像素素材 | 像素小狐狸，待机 / 眨眼 / 开心 / 难过 等动作 |
| 5 | CI/CD | GitHub Actions 自动构建并产出 APK |

## 阶段 2 已落地的东西

数据库（migration `init_pet_schema`，项目 `qhbdbkttnmevgsovrset`）：

- `pet_state`：每用户一行，name / mood / mood_value / energy / intimacy / last_seen_at
- `pet_memory`：长期记忆条目，kind / content / weight
- `pet_events`：事件流水，type + payload(jsonb)
- 三张表均开启 RLS，策略统一为 `auth.uid() = user_id`
- `pet_state.updated_at` 由触发器自动维护

客户端：

- `PetStore.kt`：直接用 HttpURLConnection 打 Auth + PostgREST，不引第三方 SDK
- 登录方式：Supabase Auth 邮箱密码（项目已开邮箱自动确认，注册即可用）
- 未登录时 MuMu 仍会出来，只是不同步
- `PetOverlayService` 通过 `addJavascriptInterface` 把状态推给 `window.MuMu.applyState()`，点击回报 `pet_events`

## 约定

- 仓库内不存任何服务端密钥。客户端只用 Supabase publishable key（本就是公开的）。
- 需要覆盖配置时，在本地 `local.properties` 写 `SUPABASE_URL` / `SUPABASE_PUBLISHABLE_KEY`，或走环境变量。
