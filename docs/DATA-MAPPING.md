# XBedSync 数据映射与插件能力分析

本文记录「BedWars1058 23.1.1 事件/API → XBed 上报字段」的完整映射，以及**不支持/近似**的数据清单。

所有结论都不是凭记忆写的，而是用 `javap` 对实际运行的
`Bedwars-plugin-23.1.1.jar` 逐个核对得到的（命令见文末「复现方式」）。

---

## TL;DR 支持 / 不支持（评审速览）

| 结论 | 数据 | 说明 |
|---|---|---|
| ✅ **支持**（来源可靠） | 比赛 ID、竞技场（名/显示名）、模式、开始/结束时间、获胜队伍、玩家 UUID/游戏名/队伍、击杀、最终击杀、死亡、最终死亡、破坏床、事件时间线（kill/death/bed_break/join/leave/rejoin/team_eliminated） | 来自 BedWars1058 公开事件 + `IArena` **单场**计数器；不使用任何累计统计 |
| ⚠️ **近似**（可用但有口径差异） | `placement`（只能到队伍名次）、`endReason`（无原因字段需推断）、`finalKills`/`finalDeaths`（依赖核心定义）、召唤物/解围击杀归属、中途退出玩家统计、崩溃时 `endedAt` | 详见第 6 节「不支持 / 近似字段清单」；不为此伪造数据 |
| ❌ **不支持**（不采集或恒默认） | 玩家**个体**淘汰先后、`isDraw`（恒 `false`）、经验/商店/伤害/搭桥/回放、`season` 字段、热重载接管进行中比赛 | 契约无字段或核心无数据；留空/`false`，绝不编造 |

---

## 1. 目标环境（实测）

| 项 | 值 |
|---|---|
| 服务端 | PaperSpigot 1.8.8（协议 47），Java 11 运行 |
| 起床核心 | BedWars1058 23.1.1（汉化版），jar 内 `plugin.yml`：`name: BedWars1058`、`version: 23.1.1` |
| 事件包 | `com.andrei1058.bedwars.api.events.*` |
| 服务器 | 唯一一台比赛服（`serverKey = s1`） |
| 竞技场示例 | `castle`（`group: 4v4v4v4`、`display-name: Castle`） |
| 语言 | `BedWars1058/Languages/messages_zh.yml`，队伍显示名如 `team-name-cave-Red: 红队` |

`BedWars1058/config.yml` 的 `arenaGroups` 实测值：`Solo`、`4v4`、`3v3v3v3`、`4v4v4v4`、`Doubles`、`1v1`。
因此 `mode` 字段的取值由服务器管理员在竞技场配置中决定，插件按原值上报（`modeAllowlist` 可按此过滤）。

---

## 2. 事件/API 可用性（javap 核对结果）

### 2.1 实际存在且被使用的事件

| 事件类（`com.andrei1058.bedwars.api.events.`） | 关键方法 | 用途 |
|---|---|---|
| `gameplay.GameStateChangeEvent` | `getArena()`, `getOldState()`, `getNewState()` | 开赛检测（→ `playing`）与结束兜底（→ `restarting`） |
| `gameplay.GameEndEvent` | `getArena()`, `getWinners()`, `getLosers()`, `getAliveWinners()`, `getTeamWinner()` | 正常结束、胜者 |
| `player.PlayerKillEvent` | `getArena()`, `getVictim()`, `getKiller()`, `getCause()` | kill / death / finalKills / finalDeaths |
| `player.PlayerBedBreakEvent` | `getPlayer()`, `getPlayerTeam()`, `getVictimTeam()`, `getArena()` | 破坏床（bedsBroken / bed_break 事件） |
| `team.TeamEliminatedEvent` | `getArena()`, `getTeam()` | 队伍淘汰顺序（名次） |
| `player.PlayerJoinArenaEvent` | `getArena()`, `getPlayer()`, `isSpectator()` | 比赛中加入（join 事件/roster） |
| `player.PlayerLeaveArenaEvent` | `getPlayer()`, `getArena()`, `getLastDamager()` | 比赛中离开（leave 事件） |
| `player.PlayerReJoinEvent` | `getArena()`, `getPlayer()`, `getRespawnTime()` | 断线重连（rejoin 事件） |
| `player.PlayerFirstSpawnEvent` | `getArena()`, `getTeam()`, `getPlayer()` | 队伍归属确认 |
| `gameplay.TeamAssignEvent` | `getPlayer()`, `getTeam()`, `getArena()` | 队伍分配（TeamAssign） |
| `server.ArenaDisableEvent` | `getArenaName()`, `getWorldName()` | 竞技场被禁用 → 异常结束 |
| `server.ArenaRestartEvent` | `getArenaName()`, `getWorldName()` | 竞技场重启 → 异常结束 |

`PlayerKillEvent.PlayerKillCause` 枚举实测值（节选）：
`UNKNOWN`、`UNKNOWN_FINAL_KILL`、`EXPLOSION`、`EXPLOSION_FINAL_KILL`、`VOID`、`VOID_FINAL_KILL`、
`PVP`、`PVP_FINAL_KILL`、`PLAYER_SHOOT`、`PLAYER_SHOOT_FINAL_KILL`、`SILVERFISH`、`SILVERFISH_FINAL_KILL`、
`IRON_GOLEM`、`IRON_GOLEM_FINAL_KILL`、`PLAYER_PUSH`、`PLAYER_PUSH_FINAL`、
`PLAYER_DISCONNECT`、`PLAYER_DISCONNECT_FINAL`；
并提供 `isFinalKill()`、`isDespawnable()`、`isPvpLogOut()`。

### 2.2 不存在 / 不可用的事件

- **`GameStartEvent` 不存在**（23.1.1 的 gameplay 包中没有该类）。开赛必须用
  `GameStateChangeEvent` 的 `waiting/starting → playing` 判定。
- **`GameEndEvent` 不提供结束原因**：构造器签名只有
  `(IArena, List<UUID> winners, List<UUID> losers, ITeam teamWinner, List<UUID> aliveWinners)`，
  没有 cause/endReason 字段。`endReason` 只能由插件按生命周期推断。
- 没有「比赛异常/崩溃」专用事件；异常只能由插件检测（见第 5 节）。

### 2.3 可获取「单场」统计的核心 API

`IArena`（实测方法）：

| 方法 | 语义（已核对实现字节码） |
|---|---|
| `int getPlayerKills(Player, boolean)` | `boolean=true` 返回 **finalKills**（`playerFinalKills`，按 Player 键）；`false` 返回 **kills**（`playerKills`，按玩家名键） |
| `int getPlayerDeaths(Player, boolean)` | `true` 返回 **finalDeaths**（`playerFinalKillDeaths`）；`false` 返回 **deaths** |
| `int getPlayerBedsDestroyed(Player)` | 本场破坏床数 |
| `String getArenaName()` / `String getDisplayName()` / `String getGroup()` | 竞技场名 / 显示名 / 模式组 |
| `GameState getStatus()` | `waiting` / `starting` / `playing` / `restarting` |
| `List<Player> getPlayers()`, `List<ITeam> getTeams()`, `ITeam getTeam(Player)` | 参赛玩家 / 队伍 |
| `java.time.Instant getStartTime()` | 竞技场开始时间（**未采用**，见 4.1） |

`ITeam`：`String getName()`、`String getDisplayName(Language)`。
`Language.getDefaultLanguage()` 配合汉化语言文件可得到 `红队 / 蓝队` 这类显示名。

> 注意：`BedWars$IStats`（`getPlayerKills(UUID)` 等）是**累计统计**，不能当单场统计使用。
> 本插件只使用 `IArena` 的**单场**计数器与事件累计值。

---

## 3. 事件 → 字段映射表

### 3.1 比赛级字段

| 上报字段 | 来源 | 限制/说明 |
|---|---|---|
| `schemaVersion` | 常量 | 固定 `1` |
| `matchId` | 插件生成 `UUID.randomUUID()`，在 `GameStateChangeEvent → playing` 时创建 | 开赛即固定；队列文件、重试、重启补发均复用；同时写入崩溃快照 |
| `arena` | `IArena#getArenaName()` | 竞技场配置名（如 `castle`） |
| `arenaDisplay` | `IArena#getDisplayName()`，为空回退 `getArenaName()` | 去除 `§` 颜色代码 |
| `mode` | `IArena#getGroup()` | 由竞技场 yml `group` 决定（如 `4v4v4v4`、`Solo`），非插件推导 |
| `startedAt` | 插件在 `GameStateChangeEvent → playing` 时的 `Instant.now()` | 不直接用 `IArena#getStartTime()`：23.1.1 中 `startTime` 在 `changeStatus()` 内被刷新，语义不够稳定 |
| `endedAt` | `GameEndEvent` 时的 `Instant.now()` | 异常结束时为兜底时刻；崩溃恢复时为**最后一次快照**时间（默认每 10s 一次） |
| `endReason` | 推断（见第 5 节） | 正常固定 `team_eliminated` |
| `winnerTeam` | `GameEndEvent#getTeamWinner()` → `ITeam#getDisplayName(defaultLang)`，缺失回退 `getName()` | 例：`红队`；无法确定时 `null` |
| `isDraw` | 固定 `false` | BedWars1058 23.1.1 没有平局流程（见 4.3） |
| `abnormal` | 推断（第 5 节） | 异常比赛不会自动结算，交后台人工裁定 |
| `abnormalReason` | 推断（第 5 节） | 正常时为 `null` |

### 3.2 玩家级字段

| 上报字段 | 来源 | 限制/说明 |
|---|---|---|
| `uuid` | `Player#getUniqueId()` | — |
| `gameId` | `Player#getName()` | 1.8.8 玩家名即游戏 ID |
| `team` | `IArena#getTeam(Player)` / `PlayerFirstSpawnEvent#getTeam()` / `TeamAssignEvent#getTeam()` → `ITeam#getDisplayName(defaultLang)`，缺失回退 `getName()` | 语言文件缺键时回退 `Red/Blue` 等；`teamNameMap` 未实现（见第 6 节） |
| `isWinner` | `GameEndEvent#getWinners()` 的 UUID 列表；列表为空时用 `winnerTeam` 匹配队伍兜底 | 兜底数据仍来自事件，不伪造 |
| `kills` | ①事件累计：`PlayerKillEvent` 且 `killer != null`；②结束时用 `IArena#getPlayerKills(p,false)` 校准 | 默认含召唤物击杀（`stats.includeDespawnableKills: true`，与核心 `addPlayerKill` 行为一致）；校准只取「核心值 > 事件值」 |
| `finalKills` | ①`PlayerKillEvent#getCause().isFinalKill()` 为真的击杀；②`IArena#getPlayerKills(p,true)` 校准 | 同上 |
| `deaths` | ①每个 `PlayerKillEvent` 的 victim 计数一次；②`IArena#getPlayerDeaths(p,false)` 校准 | 虚空/爆炸/掉线等死亡由核心统一触发 `PlayerKillEvent`（掉线由 `Arena#removePlayer()` 以 `PLAYER_DISCONNECT[_FINAL]` 触发，killer 为最后伤害来源） |
| `finalDeaths` | ①`PlayerKillEvent#getCause().isFinalKill()` 为真的 victim；②`IArena#getPlayerDeaths(p,true)` 校准 | 核心另有 `PLAYER_DISCONNECT_FINAL` 等枚举 |
| `bedsBroken` | ①`PlayerBedBreakEvent` 计数；②`IArena#getPlayerBedsDestroyed(p)` 校准 | — |
| `placement` | 由「队伍数 + `TeamEliminatedEvent` 顺序 + 冠军队」计算 | **队伍名次**；无法确定时 `null` |

### 3.3 事件时间线 `events[]`

| `type` | 触发事件 | `actor` | `target` | `team` |
|---|---|---|---|---|
| `kill` | `PlayerKillEvent`（killer 非空且非自杀） | 击杀者游戏名 | 被击杀者游戏名 | 击杀者队伍显示名 |
| `death` | `PlayerKillEvent`（每次死亡） | 被击杀者游戏名 | 击杀者游戏名（可能为 `null`，如虚空） | 死亡者队伍显示名 |
| `bed_break` | `PlayerBedBreakEvent` | 破坏者游戏名 | 受害队伍显示名（`getVictimTeam()`） | 破坏者队伍显示名 |
| `join` | `PlayerJoinArenaEvent`（非旁观、未取消、比赛进行中） | 玩家游戏名 | `null` | 玩家队伍 |
| `leave` | `PlayerLeaveArenaEvent`（比赛进行中） | 玩家游戏名 | `null` | 玩家队伍 |
| `rejoin` | `PlayerReJoinEvent` | 玩家游戏名 | `null` | 玩家队伍 |
| `team_eliminated` | `TeamEliminatedEvent` | `null` | `null` | 被淘汰队伍显示名 |

> 比赛结束后（`GameEndEvent` 已捕获或状态进入 `restarting`）不再记录事件，
> 避免把赛后清理/传送产生的 `leave` 混进时间线。

---

## 4. 生命周期判定（含关键时序坑）

### 4.1 开赛

`GameStateChangeEvent#getNewState() == GameState.playing` 时创建 `Match` 并快照参赛名单
（`IArena#getPlayers()`，旁观者不在其内）。

### 4.2 结束（关键！）

对 `com.andrei1058.bedwars.arena.Arena#checkWinner()` 的字节码核对显示执行顺序为：

```
... 判定只剩 1 支队伍 ...
1046: changeStatus(GameState.restarting)        // ← 先切状态，触发 GameStateChangeEvent
...   构建 winners/losers/aliveWinners ...
1290: callEvent(new GameEndEvent(...))          // ← 后触发 GameEndEvent
```

因此 **`GameStateChangeEvent(restarting)` 早于 `GameEndEvent`**。插件实现策略：

1. 收到 `restarting`：停止记录事件，启动 **2 秒（40 ticks）**兜底定时器；
2. 2 秒内收到 `GameEndEvent`：按正常结束处理，取消兜底；
3. 2 秒内未收到：判定异常 `arena_restart`（生命周期不完整）。

### 4.3 名次计算

`checkWinner()` 只有在「队伍总数 - 空队伍数 == 1」时才结束比赛，因此正常结束必然有唯一冠军队，
不存在平局分支。名次由「队伍数 + 淘汰顺序」推算：

- 冠军队 = 1；
- 第一支被淘汰的队伍 = 队伍数；第二支 = 队伍数-1……最后一支被淘汰 = 2；
- 玩家名次 = 队伍名次；无法确定（如异常结束、淘汰事件缺失）→ `null`。

### 4.4 最终死亡 ≠ 离开比赛

`DamageDeathMove` 字节码显示：最终死亡后玩家通过 `IArena#addSpectator()` 转为旁观者，
**不会**触发 `PlayerLeaveArenaEvent`；`PlayerLeaveArenaEvent` 只在 `Arena#removePlayer()` 路径触发。
因此「比赛进行中收到 leave 事件」= 玩家真正离开/退出，不会把最终死亡误记为 leave。

### 4.5 死亡事件不会重复计数

对 jar 内所有 class 做 `new PlayerKillEvent` 扫描，只有两处构造：

1. `com.andrei1058.bedwars.listeners.DamageDeathMove`（`PlayerDeathEvent` 处理，正常死亡/最终死亡）；
2. `com.andrei1058.bedwars.arena.Arena#removePlayer()`（比赛中玩家断线，cause 为
   `PLAYER_DISCONNECT` / `PLAYER_DISCONNECT_FINAL`，killer 取最后伤害者，可能为 `null`）。

其余 `SpectatorListeners`、`HalloweenListener`、`TNTListener`、各版本 `vX_Y_RZ`
只**监听**该事件或引用 cause 枚举，不会重复触发。因此按 `PlayerKillEvent` 计 death/kill 不会重复。

> 玩家断线会同时产生一次 `death`（来自 `PLAYER_DISCONNECT` 的 `PlayerKillEvent`）与一条
> `leave` 事件（来自 `PlayerLeaveArenaEvent`），这是符合游戏语义的，不是重复统计。

---

## 5. 异常检测规则

| 情形 | 检测方式 | `endReason` / `abnormalReason` |
|---|---|---|
| 正常分出胜负 | 收到 `GameEndEvent` 且有胜者 | `team_eliminated` / `null` |
| 状态进入 `restarting` 但 2s 内无 `GameEndEvent` | 兜底定时器 | `arena_restart` |
| 结束但无胜者（`teamWinner == null` 且 winners 空） | `GameEndEvent` 校验 | `no_winner` |
| 比赛进行中竞技场被禁用 | `ArenaDisableEvent` | `arena_disabled` |
| 比赛进行中竞技场重启 | `ArenaRestartEvent` | `arena_restart` |
| 服务器正常关服时比赛仍在进行 | 插件 `onDisable()` | `server_shutdown` |
| 服务器崩溃/被强杀 | 启动时发现 `*.partial.json` 快照 | `server_crash` |

异常比赛一律 `abnormal=true`，**不自动判定胜负**，保留已采集原始数据，交由后台人工裁定
（与需求规格书第十一部分一致）。

---

## 6. 不支持 / 近似字段清单（重要）

| 数据 | 状态 | 原因 / 处理 |
|---|---|---|
| 玩家**个体**淘汰先后 | 不支持 | 核心只有队伍级 `TeamEliminatedEvent`；`placement` 为队伍名次，个体顺序无法得知 |
| `isDraw` | 恒 `false` | 23.1.1 无平局机制；无法确定胜者时用 `abnormal=true` + `no_winner`，不虚构平局 |
| `endReason` | 近似（推断） | `GameEndEvent` 无原因字段；只能根据生命周期与兜底规则推断 |
| 解围/召唤物击杀（铁傀儡、蠹虫）归属 | 近似 | 默认并入 `kills`（与核心 `addPlayerKill` 行为一致），可配置关闭；核心不区分展示 |
| `finalKills` / `finalDeaths` 口径 | 近似 | 依赖核心 `PlayerKillCause#isFinalKill()`；不同服务器/统计插件的定义可能有差异 |
| 中途退出玩家的最终统计 | 近似 | 结束时不在线的玩家无法读取 `IArena` 计数器（Bukkit `Player` 实例已失效），只能使用事件累计值 |
| 崩溃时的 `endedAt` | 近似 | 取最后一次本地快照时间（默认 10s 一次），不是精确崩溃时刻 |
| 队伍显示名 | 依赖配置/语言 | 来自 BedWars1058 语言文件；地图缺少 `team-name-<arena>-<team>` 键时回退 `Red/Blue`。网站侧应按字符串映射，或后续新增 `teamNameMap` 配置 |
| 赛季归属 `season` | 不随 payload 发送 | 上报契约无该字段；网站按 `startedAt` 归属赛季（需 Lead 与后端确认） |
| 玩家经验/等级/资源/商店购买 | 不采集 | `PlayerXpGainEvent`、`ShopBuyEvent` 可用，但契约无对应字段；不扩大采集范围（需求 9.3：禁止不必要复杂开发） |
| 伤害量、护甲、搭桥、占用时间等 | 不支持 | 核心无对应单场 API/事件，契约也未要求 |
| 旁观者、等待期玩家 | 不采集 | 比赛从 `playing` 开始；旁观者不计入 `players[]` |
| 插件热重载时正在进行的比赛 | 无法接管 | 热重载后无法得知 `startedAt`/开赛名单；该场由关服快照（`server_crash`）覆盖。**建议不要在比赛进行中 reload** |
| 回放（ReplayMod） | 不采集 | 由玩家自行上传 `.mcpr`（网站侧功能） |

---

## 7. 后续扩展点

1. **`teamNameMap` 配置**：把语言显示名映射为稳定英文 id（如 `红队→red`），避免网站依赖中文显示名。
   因当前上报契约 `players[].team` 只有字符串字段，需要网站侧先确认是否需要独立 `teamId`。
2. **`ShopBuyEvent` / `PlayerXpGainEvent`**：若契约增加经济/经验字段，可按同样方式采集。
3. **`PlayerReSpawnEvent`**：可用于统计复活次数（当前契约无字段）。
4. **`GameStateChangeEvent → starting/waiting`**：可用于记录「等待时长」，当前不需要。

---

## 8. 复现方式（javap 核对命令）

```powershell
$jar = "C:\Users\A\Desktop\BedWars1058端汉化7.9\起床战争\plugins\Bedwars-plugin-23.1.1.jar"

# 1) 列出全部事件类
Add-Type -AssemblyName System.IO.Compression.FileSystem
$z = [System.IO.Compression.ZipFile]::OpenRead($jar)
$z.Entries | Where-Object { $_.FullName -like 'com/andrei1058/bedwars/api/events/*' } |
  Select-Object -ExpandProperty FullName
$z.Dispose()

# 2) 逐个核对事件方法签名
javap -classpath $jar -p com.andrei1058.bedwars.api.events.gameplay.GameEndEvent
javap -classpath $jar -p com.andrei1058.bedwars.api.events.player.PlayerKillEvent
javap -classpath $jar -p 'com.andrei1058.bedwars.api.events.player.PlayerKillEvent$PlayerKillCause'
javap -classpath $jar -p com.andrei1058.bedwars.api.events.player.PlayerBedBreakEvent
javap -classpath $jar -p com.andrei1058.bedwars.api.events.team.TeamEliminatedEvent
javap -classpath $jar -p com.andrei1058.bedwars.api.events.player.PlayerJoinArenaEvent
javap -classpath $jar -p com.andrei1058.bedwars.api.events.player.PlayerLeaveArenaEvent
javap -classpath $jar -p com.andrei1058.bedwars.api.events.player.PlayerReJoinEvent
javap -classpath $jar -p com.andrei1058.bedwars.api.events.player.PlayerFirstSpawnEvent

# 3) 核对 IArena 单场计数器与生命周期时序
javap -classpath $jar -p com.andrei1058.bedwars.api.arena.IArena
javap -classpath $jar -p com.andrei1058.bedwars.api.arena.GameState
javap -classpath $jar -p -c com.andrei1058.bedwars.arena.Arena > arena.txt
# 在 arena.txt 中查看 checkWinner：changeStatus(restarting) 先于 callEvent(GameEndEvent)
# 以及 DamageDeathMove 中 addPlayerKill / addPlayerDeath / addSpectator 的调用
```

核心代码里与本插件结论直接相关的两处（可搜索 `arena.txt`）：

- `public void checkWinner();` —— `changeStatus(restarting)`（约偏移 1046）在
  `callEvent(GameEndEvent)`（约偏移 1290）之前；
- `public int getPlayerKills(Player, boolean);` —— `iload_2` 为真时读 `playerFinalKills`，
  否则读 `playerKills`；`getPlayerDeaths` 同理。
