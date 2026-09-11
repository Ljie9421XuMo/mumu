# MuMu Roadmap

> 一个住在手机里的桌宠。Kotlin + WebView 悬浮窗做壳，Supabase 做同步，情绪引擎做魂。

## 进度

| 阶段 | 目标 | 状态 |
| --- | --- | --- |
| 1 | 骨架 | ✅ 完成（编译通过） |
| 2 | 后端同步 | ✅ 完成（三张表 + RLS + 登录 + 状态读写 + 事件上报） |
| 3 | 情绪引擎 | ✅ 完成（心情/精力/亲密度随时间与互动变化，五种表情） |
| 4 | 像素素材 | ✅ 完成（橘色像素小狐狸） |
| 5 | CI/CD | ✅ 完成（Actions 跑单测 + 自动出 debug APK） |

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

## 阶段 3 已落地的东西

`MoodEngine.kt`：纯函数状态机，不碰网络不碰界面。输入一个 `PetState` 和一个 `Instant`，输出新状态。

三条线：

- **精力 energy**：醒着每小时 -4，夜里（23:00-06:00）每小时 +6，限 0-100
- **心情 moodValue**：精力低于 30 时每小时 -3；被冷落超过 6 小时，超出部分每小时 -2
- **亲密度 intimacy**：只在真实互动里涨，不自然掉

单次结算最多按 24 小时算，免得长期离线一次性掉到底。

表情推导 `deriveMood(moodValue, energy)`，优先级从高到低：

| 条件 | 表情 |
| --- | --- |
| energy ≤ 12 | `sleepy` 困了 |
| moodValue < 30 | `sad` 难过 |
| energy ≤ 35 | `tired` 累了 |
| moodValue ≥ 70 | `happy` 开心 |
| 其余 | `idle` 待机 |

互动：`onTap`（+3 心情 / +1 亲密 / -1 精力）、`onPet`（+6 / +3 / -2）。

接入方式：

- `PetOverlayService.handleTap()` 走 `MoodEngine.onTap`
- 拉取云端状态后先跑一次 `MoodEngine.settle` 再把结果存回去
- 服务存活时每 5 分钟跑一次周期结算，未登录/离线直接跳过
- 网页端 `tired` / `sleepy` 有独立的滤镜与呼吸、眨眼节奏

验证：`app/src/test/java/com/mumu/pet/MoodEngineTest.kt`，纯 JVM 单测，CI 每次 push 都跑。

## 约定

- 仓库内不存任何服务端密钥。客户端只用 Supabase publishable key（本就是公开的）。
- 需要覆盖配置时，在本地 `local.properties` 写 `SUPABASE_URL` / `SUPABASE_PUBLISHABLE_KEY`，或走环境变量。
