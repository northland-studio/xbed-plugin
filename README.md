# XBedSync

XBed 玄剑起床战争赛事平台的**服务器数据采集插件**。

监听 [BedWars1058](https://www.spigotmc.org/resources/bedwars1058.50922/) 的比赛事件，聚合单场比赛数据，
通过 **HMAC-SHA256 签名**异步上报到 XBed 网站（`POST /api/ingest/match`），
本地持久化队列 + 指数退避重试，保证断网/重启/崩溃不丢数据。

- 目标环境：PaperSpigot **1.8.8**（协议 47）+ Java 11 运行 + BedWars1058 **23.1.1**
- 插件字节码：Java 8（`sourceCompatibility/targetCompatibility = 1.8`）
- 独立公开仓库（不与网站仓库混合），CI 构建产物见 GitHub Actions / Releases

---

## 1. 功能特性

| 能力 | 实现 |
|---|---|
| 比赛识别 | `GameStateChangeEvent → playing` 开赛；`GameEndEvent` 正常结束；状态兜底 |
| 单场数据聚合 | 玩家 UUID/游戏名/队伍/胜负/击杀/最终击杀/死亡/最终死亡/破坏床/名次 |
| 事件时间线 | `kill` / `death` / `bed_break` / `join` / `leave` / `rejoin` / `team_eliminated` |
| 异常比赛标记 | 竞技场重启/禁用、关服、崩溃快照、无胜者 → `abnormal=true`，不自动结算 |
| 本地队列 | `queue/<matchId>.json`，临时文件 + rename 原子写 |
| 崩溃恢复 | 进行中比赛每 N 秒写 `queue/<matchId>.partial.json` 快照，重启后升级为异常比赛补报 |
| HMAC 签名 | `METHOD\nPATH\nTIMESTAMP\nNONCE\nSHA256_HEX(body)`，头部 `X-XBed-*` |
| 重试 | 异步线程，指数退避 1s→2s→4s…上限 300s；2xx 或 `duplicate:true` 才删除队列 |
| 管理命令 | `/xbedsync status` / `retry` / `ping` |
| 安全 | 密钥不打印、日志脱敏、生产强制 HTTPS（http 仅 localhost） |

字段级细节与「不支持/近似」清单见 **[docs/DATA-MAPPING.md](docs/DATA-MAPPING.md)**；
上报契约与签名示例见 **[docs/API.md](docs/API.md)**。

---

## 2. 兼容性

| 项 | 支持情况 |
|---|---|
| PaperSpigot 1.8.8 | ✅ 目标环境（服务端 NMS 实现 `v1_8_R3`） |
| Spigot 1.8.8 | ✅ API 二进制一致 |
| BedWars1058 23.1.1 | ✅ 事件/API 已用 `javap` 逐项核对；`depend: [BedWars1058]` |
| BedWars1058 其他版本 | ⚠️ 未验证。升级前请重新核对事件签名（见 DATA-MAPPING 第 8 节），并用真实 jar 构建验证 |
| 1.9+ 服务端 | ❌ 未适配（本插件按 1.8.8 运行环境开发） |

> 说明：`bedwars-plugin-23.1.1.jar` 的 `plugin.yml` 写着 `api-version: '1.13'`，
> 但 jar 内含 `v1_8_R3` 版本支持类，实测可在 PaperSpigot 1.8.8 运行；本插件依赖的是其公开事件 API。

---

## 3. 安装（PaperSpigot 1.8.8 + BedWars1058 23.1.1 分步说明）

### 3.1 前置条件

- 服务端：**PaperSpigot 1.8.8**（协议 47），使用 **Java 11** 启动
- 核心插件：**BedWars1058 23.1.1** 已安装且能正常开赛（`plugins/Bedwars-plugin-23.1.1.jar`）
- 服务器能访问 XBed 域名（`curl -I https://xbed.xuanjian.top/api/health` 应返回 200）
- 已在 XBed 后台登记该服务器，拿到 `serverKey`（S1 固定 `s1`）与 `hmacSecret`

### 3.2 安装步骤

1. **确认 BedWars1058 版本**
   ```
   plugins/Bedwars-plugin-23.1.1.jar
   ```
   确认 `plugins/BedWars1058/` 已生成、竞技场可正常开局。本插件按 23.1.1 的事件 API 开发，其他版本未验证。

2. **放入插件 jar**
   把 `XBedSync-1.0.0.jar` 复制到服务器 `plugins/` 目录：
   ```
   plugins/XBedSync-1.0.0.jar
   ```

3. **首次启动，生成 config.yml**
   启动服务器。首次启动会生成：
   ```
   plugins/XBedSync/config.yml
   ```
   此时 `hmacSecret` 为空，控制台会出现「配置错误 / 已停止启用」日志——这是**预期行为**
   （插件拒绝用空密钥上报）。看到该日志后**停止服务器**。

4. **填写配置**（编辑 `plugins/XBedSync/config.yml`）
   ```yaml
   apiBaseUrl: "https://xbed.xuanjian.top/api"
   serverKey: "s1"
   hmacSecret: "后台生成的真实密钥"
   ```
   - `apiBaseUrl`：生产必须 `https`；本地联调才允许 `http://127.0.0.1:3939/api`
   - `serverKey`：必须与网站后台登记的服务器标识一致
   - `hmacSecret`：从 XBed 后台服务器管理页面获取，**不要提交到仓库/群里**
   - 建议：Linux `chmod 600 plugins/XBedSync/config.yml`；Windows 设为仅管理员可读

5. **重启服务器并确认启用**
   控制台应出现类似日志：
   ```
   [XBedSync] 已启用。serverKey=s1 season=S1 queue=.../plugins/XBedSync/queue pending=0 ...
   ```
   若仍提示配置错误，按提示检查 `apiBaseUrl`（是否 https/本地地址）、`serverKey`、`hmacSecret`（长度 ≥16）。

6. **联调签名与网络：`/xbedsync ping`**
   在游戏内或控制台执行：
   ```
   /xbedsync ping
   ```
   成功：`ping 成功: HTTP 200: {"ok":true,...}`
   失败：按控制台/聊天提示排查（401 多为密钥不匹配或服务器时钟偏差 >300s）。

7. **打一场测试局，查看上报结果**
   正常开赛并打完一局，结束后执行：
   ```
   /xbedsync status
   ```
   应看到「最近成功」与 `matchId`；队列应为 `0 场`。

8. **到 XBed 后台确认**
   比赛应出现在后台「待审核比赛」列表中；若为异常结束（如竞技场重启），会带 `abnormal=true` 交给后台裁定。

### 3.3 队列与补发

- 上报失败（断网/后端不可用/签名错误）时，数据保存在：
  ```
  plugins/XBedSync/queue/<matchId>.json
  ```
  插件会自动按指数退避重试（1s→2s→4s…上限 300s），**恢复网络后自动补发**。
- 服务器重启后，启动时会自动扫描 `queue/*.json` 继续补发，无需人工操作。
- 需要立即重发时执行：
  ```
  /xbedsync retry
  ```
- `plugins/XBedSync/queue/<matchId>.partial.json` 是「进行中比赛」的崩溃恢复快照；
  服务器异常崩溃/被强杀后，下次启动会把它升级为 `abnormal=true, endReason=server_crash` 的待上报数据自动补发。
- **不要手工删除队列文件**，除非确认该场数据要丢弃（删除后无法恢复）。

> 安全提醒：`config.yml` 含 `hmacSecret`，禁止提交到公开仓库或发到群里。


---

## 4. 配置说明（`plugins/XBedSync/config.yml`）

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `apiBaseUrl` | `https://xbed.xuanjian.top/api` | 不含末尾 `/`；本地联调可用 `http://127.0.0.1:3939/api` |
| `serverKey` | `s1` | 服务器身份标识 |
| `hmacSecret` | 空 | HMAC 共享密钥；为空时插件拒绝启用 |
| `season` | `S1` | 赛季标识，仅日志用途；当前上报契约无 `season` 字段 |
| `queueDir` | `queue` | 队列目录（相对插件数据目录或绝对路径） |
| `modeAllowlist` | `[]` | 只采集这些 BedWars1058 `group`（如 `4v4v4v4`）；空 = 全部 |
| `arenaAllowlist` | `[]` | 只采集这些竞技场名（如 `castle`）；空 = 全部 |
| `stats.preferArenaCounters` | `true` | 结束时用核心单场计数器校准事件累计值（仅取更大值） |
| `stats.includeDespawnableKills` | `true` | 铁傀儡/蠹虫等召唤物击杀是否计入 `kills` |
| `retry.initialDelaySeconds` | `1` | 首次重试延迟 |
| `retry.maxDelaySeconds` | `300` | 退避上限（5 分钟） |
| `retry.httpTimeoutSeconds` | `10` | 单次 HTTP 超时 |
| `retry.scanIntervalSeconds` | `1` | 队列扫描间隔 |
| `snapshotIntervalSeconds` | `10` | 进行中比赛快照间隔（崩溃恢复用），0 = 关闭 |
| `debug` | `false` | 输出事件明细与统计口径对比日志 |

配置非法（`apiBaseUrl`/`serverKey`/`hmacSecret` 缺失或非 HTTPS 非本地地址）时，
插件会打印错误并**拒绝启用**，避免向 XBed 发送错误数据。

---

## 5. 命令与权限

| 命令 | 说明 |
|---|---|
| `/xbedsync status` | 队列长度、崩溃快照数、退避中数量、进行中比赛、最近成功/失败原因、下次重试倒计时 |
| `/xbedsync retry` | 清空退避计时，立即重发本地队列 |
| `/xbedsync ping` | 调用 `GET /api/ingest/ping` 联调（带 HMAC 签名） |

权限：`xbedsync.admin`（默认 OP）。

---

## 6. 字段映射摘要

| 上报字段 | 来源 |
|---|---|
| `matchId` | 插件开赛时生成 UUID，全程固定 |
| `arena` / `arenaDisplay` / `mode` | `IArena#getArenaName()` / `getDisplayName()` / `getGroup()` |
| `startedAt` / `endedAt` | 进入 `playing` / `GameEndEvent` 时刻（UTC ISO8601） |
| `endReason` | 正常 `team_eliminated`；异常 `arena_restart`/`arena_disabled`/`server_shutdown`/`server_crash`/`no_winner` |
| `winnerTeam` | `GameEndEvent#getTeamWinner()` → 语言文件显示名（如 `红队`） |
| `players[]` | `PlayerKillEvent`、`PlayerBedBreakEvent`、`PlayerJoinArenaEvent` 等聚合，结束时用 `IArena` 单场计数器校准 |
| `events[]` | 上述事件的时间线（比赛结束后停止记录） |

完整映射表、队伍名/名次/异常判定规则见 **[docs/DATA-MAPPING.md](docs/DATA-MAPPING.md)**。

### 不支持 / 近似字段（摘要）

- 玩家**个体**淘汰先后：核心只有队伍级淘汰顺序，`placement` 是**队伍名次**，未知为 `null`。
- `isDraw`：BedWars1058 无平局机制，当前恒 `false`；无法确定胜者时 `abnormal=true`。
- `endReason`：`GameEndEvent` 无原因字段，只能推断。
- 解围/召唤物击杀归属、`finalKills` 定义、中途退出玩家统计：近似，见 DATA-MAPPING 第 6 节。
- 玩家经验/商店/伤害/搭桥等：不采集（契约无字段，遵循需求 9.3 不做不必要复杂开发）。
- `season`：契约无该字段，不随 payload 发送。
- 插件热重载时正在进行的比赛无法接管（会以关服快照异常上报），**请勿在比赛进行中 reload**。

---

## 7. 上报与重试机制

```
比赛结束
  └─ 序列化 payload → queue/<matchId>.json（原子写，写入后立即尝试上报）
       ├─ 异步线程 POST /api/ingest/match（HMAC 签名）
       ├─ 2xx 或 duplicate:true → 删除队列文件，记录“最近成功”
       └─ 失败 → 保留文件，指数退避重试（1s→2s→…→300s）
启动时
  ├─ queue/*.partial.json（崩溃快照）→ 升级为 *.json（abnormal=true, server_crash）
  └─ 扫描 queue/*.json → 自动补发
```

- 所有网络 IO 在异步线程执行，不阻塞服务器主线程。
- `matchId` 固定，重复上报由网站侧幂等（`duplicate:true`）。
- 长期失败不会丢数据；`/xbedsync status` 显示队列长度、最近失败原因、下次重试时间。

---

## 8. 构建

### 8.1 依赖说明

| 依赖 | 选择 | 原因 |
|---|---|---|
| Bukkit/Spigot API | `org.spigotmc:spigot-api:1.8.8-R0.1-SNAPSHOT`（compileOnly） | PaperMC 官方 Maven 仓库**不发布** 1.8.8 的 `paper-api`（`com.destroystokyo.paper:paper-api:1.8.8-R0.1-SNAPSHOT` 不存在）；PaperSpigot 1.8.8 与 Spigot 1.8.8 API 二进制一致。若你有私服仓库的 paper-api 1.8.8，可自行替换坐标。 |
| BedWars1058 API | `compileOnly files('libs/Bedwars-plugin-23.1.1.jar')` | 付费闭源插件，无公开 Maven 仓库 |

### 8.2 两种编译模式（`build.gradle` 自动选择）

1. **真实 jar 模式（推荐，最准确）**
   把 BedWars1058 的 jar 放到 `libs/Bedwars-plugin-23.1.1.jar`（已被 `.gitignore` 忽略，不会进仓库）。
   Gradle 会用它作为 `compileOnly` 依赖编译。

2. **API 存根模式（公开 CI 默认）**
   没有 `libs/Bedwars-plugin-23.1.1.jar` 时，自动改用 `src/bedwarsStub/java` 下的
   **最小 API 存根**（仅 XBedSync 用到的方法签名，`javap` 核对后手写）参与编译。
   存根不参与打包，运行期始终加载真实 BedWars1058 插件。

> 两种模式均已用本机 `javac`（非 Gradle）验证通过。

### 8.3 本地构建

> 本项目按用户要求**不在开发机运行 gradle/maven**，因此仓库不含 Gradle Wrapper。
> 需要在本地构建时，请自行安装 Gradle 7.6.4+ 或执行 `gradle wrapper` 生成 wrapper 后使用：

```bash
# 有 gradle 命令时
gradle build

# 产物
build/libs/XBedSync-1.0.0.jar
```

### 8.4 用真实 BedWars1058 jar 构建（推荐）

公开仓库不能提交付费的 BedWars1058 jar，因此默认 CI 使用 API 存根编译。
需要「与真实 API 完全对齐」的构建时（发版前建议做一次），按下面任一方式操作：

**方式 A：本地/构建机（最简单）**

1. 复制真实插件 jar 到项目目录（文件名必须一致）：
   ```bash
   mkdir -p libs
   cp /path/to/plugins/Bedwars-plugin-23.1.1.jar libs/Bedwars-plugin-23.1.1.jar
   ```
   Windows PowerShell：
   ```powershell
   New-Item -ItemType Directory -Force libs | Out-Null
   Copy-Item "C:\...\plugins\Bedwars-plugin-23.1.1.jar" "libs\Bedwars-plugin-23.1.1.jar"
   ```
2. 执行构建：
   ```bash
   gradle build
   ```
3. 构建日志若出现
   `[XBedSync] 编译依赖：真实 BedWars1058 jar -> .../libs/Bedwars-plugin-23.1.1.jar`
   即表示用的是真实 jar（否则会打印「使用 src/bedwarsStub 下的 API 存根编译」）。

`libs/*.jar` 已被 `.gitignore` 忽略，不会进入仓库。

**方式 B：GitHub Actions（通过 secret）**

1. 把 jar 转成 base64（单行、无换行）：
   ```bash
   # Linux
   base64 -w0 plugins/Bedwars-plugin-23.1.1.jar > bw.b64
   ```
   ```powershell
   # Windows PowerShell
   [Convert]::ToBase64String([IO.File]::ReadAllBytes("plugins\Bedwars-plugin-23.1.1.jar")) |
     Set-Content -NoNewline bw.b64
   ```
2. 仓库 → `Settings` → `Secrets and variables` → `Actions` → `New repository secret`：
   - Name：`BEDWARS1058_JAR_BASE64`
   - Secret：上一步 `bw.b64` 的完整内容（一行）
3. 重新运行 `Build` workflow。日志会显示
   `已从 secret 还原真实 BedWars1058 jar`，产物即真实 jar 模式编译。
4. 若不再需要，删除该 secret 即回到存根模式（CI 仍可正常出 jar）。

### 8.5 CI（GitHub Actions）

- `.github/workflows/build.yml`：push / PR / 手动触发
  - `actions/setup-java@v4`（Temurin **Java 11**）
  - `gradle/actions/setup-gradle@v4`（`gradle-version: '7.6.4'`，自动下载 Gradle；仓库按要求不带 wrapper）
  - **默认存根模式**（未配置 `BEDWARS1058_JAR_BASE64`）：直接用 `src/bedwarsStub` 编译，能正常产出 jar
  - 若配置了 secret 则自动还原真实 jar 再编译（见 8.4 方式 B）
  - `gradle build --no-daemon` + 上传 `build/libs/XBedSync-*.jar` artifact；测试失败时额外上传 `build/reports/tests/`
- `.github/workflows/release.yml`：推送 `v*` tag 时构建并创建 GitHub Release（附 jar + sha256sums.txt）

### 8.6 CI 排障（首次构建可能遇到的问题）

| # | 现象 | 原因 / 处理 |
|---|---|---|
| 1 | `setup-gradle` 或 Gradle 启动失败（JDK/版本不兼容） | 当前固定 Java 11 + Gradle 7.6.4。若该组合在 runner 上异常，可改为 `actions/setup-java` Java **17** + `gradle-version: '8.7'`；**只影响 CI 使用的 JDK，产物仍为 Java 8 字节码**（`sourceCompatibility/targetCompatibility=1.8` 不受影响） |
| 2 | spigot-api 解析失败：`Could not resolve org.spigotmc:spigot-api:1.8.8-R0.1-SNAPSHOT`，或依赖传递缺失 | ① 重跑（Spigot snapshot 仓库偶发 5xx）；② 在 `build.gradle` 的 `repositories` 增加镜像：`maven { url = uri('https://repo.md-5.net/content/groups/public/') }`；③ 若仍不稳定，可固定到某个带时间戳的 snapshot 版本 |
| 3 | 存根模式下编译报 `package com.andrei1058.bedwars... does not exist` | 说明 `compileBedwarsStubJava` 未先执行。确认 `build.gradle` 中 `compileJava.dependsOn 'compileBedwarsStubJava'` 存在；必要时把 `sourceSets.bedwarsStub.output` 同时加入 `sourceSets.main.compileClasspath` |
| 4 | 测试偶发失败（如 `fifoOrderByModifiedTime`） | 该用例依赖文件 mtime 精度（sleep 20ms）。GitHub ubuntu 的 ext4 通常没问题；若偶发失败可重跑，或去掉该断言（不影响产物）。若 JUnit 未执行任何用例，检查 `test { useJUnitPlatform() }` 与 `testRuntimeOnly 'org.junit.platform:junit-platform-launcher'` |
| 5 | 配置了 `BEDWARS1058_JAR_BASE64` 仍按存根构建，或解码后编译报错 | 检查 secret 是否为**单行纯 base64**（不能带 `data:` 前缀/换行/文件名）；解码后文件必须是完整可用的 `Bedwars-plugin-23.1.1.jar`（不是 zip 包或改名文件）。确认失败就删掉 secret 回到存根模式，再在本地按 8.4 方式 A 验证 |

> 排障入口：Actions 运行页 → 对应 job 日志；失败时下载 `test-reports` artifact（`build/reports/tests/`）
> 查看具体失败用例；jar artifact 名为 `XBedSync-jar`。


---

## 9. 错误排查

| 现象 | 可能原因 | 处理 |
|---|---|---|
| 插件启动即报配置错误并禁用 | `apiBaseUrl`/`serverKey`/`hmacSecret` 未填，或 http 非 localhost | 修正 `config.yml` 后重启 |
| `/xbedsync ping` 返回 401 | `hmacSecret`/`serverKey` 不匹配；服务器时钟偏差 > 300s | 核对后台密钥；开启 NTP 校时 |
| 401 `stale_timestamp` | 服务器时间不准 | 同步系统时间 |
| 401 `nonce_reused` | 反向代理重放了请求 | 检查代理/Nginx 配置 |
| 400 `invalid_payload` | 上报体与后端 schema 不一致 | 对比 `docs/API.md` 与网站侧实现 |
| 一直重试、`status` 显示网络错误 | 域名不可达/DNS/防火墙/HTTPS 证书 | 服务器上 `curl -v https://xbed.xuanjian.top/api/health` 排查 |
| 队列文件持续增长 | 长期失败（密钥错误/后端不可用） | 修复后 `/xbedsync retry`；数据不会丢 |
| 比赛未被采集 | `modeAllowlist`/`arenaAllowlist` 过滤；或插件热重载时比赛已在进行 | 检查配置与 `debug: true` 日志；避免比赛中 reload |
| 队伍名显示为 `Red` 而非 `红队` | BedWars1058 语言文件缺少 `team-name-<arena>-<team>` 键 | 补语言键，或接受英文回退并用 `debug` 核对 |
| 玩家统计与核心记分板不一致 | 解围击杀口径、中途退出、最终击杀定义差异 | 见 DATA-MAPPING 第 6 节；可用 `debug: true` 查看口径对比 |

`/xbedsync status` 输出示例：

```
[XBedSync] XBedSync 状态
  API 地址: https://xbed.xuanjian.top/api
  serverKey: s1  赛季: S1
  hmacSecret: de****et
  待上报队列: 1 场  崩溃快照: 0 个  退避中: 1 场
  下次重试: 4 秒后
  最近成功: 12 分钟前  matchId=3f2c8a1e-...
  最近失败: 3 秒前  matchId=4a3d9b2f-...  HTTP 401
    原因: HTTP 401: {"error":{"code":"bad_signature"}}
  进行中比赛: 1 场
    - castle / 4v4v4v4 / 5b1d... / 玩家 16
```

---

## 10. 安全说明

- `hmacSecret` 只存在于服务器本地 `config.yml` 与内存；日志/命令输出均为脱敏值。
- 生产强制 HTTPS；`http://` 仅允许 `localhost` / `127.0.0.1` / `::1`。
- 建议 `chmod 600 plugins/XBedSync/config.yml`（或 Windows 下仅管理员可读）。
- 密钥轮换：网站后台轮换后，同步更新本插件 `config.yml` 并重启。
- 本仓库不含任何真实密钥；`libs/*.jar` 与 `config.yml` 均被 `.gitignore` 忽略。

---

## 11. 项目结构

```
xbed-plugin/
├─ .github/workflows/build.yml      # CI 构建 + artifact
├─ .github/workflows/release.yml    # tag 触发 Release
├─ build.gradle / settings.gradle / gradle.properties
├─ docs/
│  ├─ API.md                        # 上报契约 + HMAC 签名示例 + curl
│  └─ DATA-MAPPING.md               # 事件→字段映射、生命周期时序、不支持清单
├─ libs/                            # 放真实 BedWars1058 jar（不入库）
├─ src/main/java/cn/xuanjian/xbedsync/
│  ├─ XBedSync.java                 # 主类
│  ├─ command/XBedSyncCommand.java  # /xbedsync status|retry|ping
│  ├─ config/PluginConfig.java      # 配置加载与校验
│  ├─ listener/BedWarsListener.java # BedWars1058 事件监听
│  ├─ match/                        # Match / MatchPlayer / MatchEvent / MatchManager
│  ├─ report/                       # Json / HmacSigner / PayloadBuilder / QueueManager / ReportSender
│  └─ util/TimeUtil.java
├─ src/main/resources/plugin.yml / config.yml
├─ src/bedwarsStub/java/            # 无真实 jar 时的最小 API 存根（仅编译）
└─ src/test/java/                   # JUnit 5 单元测试（Json/HMAC/Payload/Queue）
```

---

## 12. 许可

[MIT](LICENSE)
