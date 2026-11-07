# XBed 服务器上报契约（插件视角 v1）

> 本文是 `XBedSync` 插件实现所依据的上报契约，与网站仓库 `xbed-website/docs/API.md`
> 中「服务器上报（HMAC）」一节一致。若两者冲突，以网站后端实际实现为准，并请同步修订本文。

- 生产 Base URL：`https://xbed.xuanjian.top/api`
- 本地联调 Base URL：`http://127.0.0.1:3939/api`（仅限 localhost）
- 编码：UTF-8；请求与响应均为 JSON，`Content-Type: application/json`
- 时间：ISO8601 UTC（毫秒精度，如 `2026-11-08T12:00:00.000Z`）

---

## 1. 认证头

每次请求必须携带：

| 头 | 示例 | 说明 |
|---|---|---|
| `X-XBed-Server` | `s1` | 服务器身份标识，与后台登记的 serverKey 一致 |
| `X-XBed-Timestamp` | `1730000000` | 秒级 Unix 时间戳，服务端允许 ±300 秒 |
| `X-XBed-Nonce` | `0123456789abcdef0123456789abcdef` | 随机 hex；插件每次请求生成 16 字节 = 32 位 hex，5 分钟内不可重复 |
| `X-XBed-Signature` | `a8e64bf9...` | HMAC-SHA256 小写 hex |

`Content-Type: application/json; charset=utf-8`（POST 请求体必须）。

## 2. 签名规则

### 2.1 签名串（canonical string）

```
{METHOD}\n{PATH}\n{TIMESTAMP}\n{NONCE}\n{SHA256_HEX(body)}
```

- `METHOD`：大写，如 `POST` / `GET`
- `PATH`：不含域名的完整请求路径，**包含 `/api` 前缀**，如 `/api/ingest/match`
- `TIMESTAMP`：与 `X-XBed-Timestamp` 完全一致
- `NONCE`：与 `X-XBed-Nonce` 完全一致
- `SHA256_HEX(body)`：请求体原始字节的 SHA-256 小写 hex；GET 无请求体时使用空字节数组

空请求体的 SHA-256 固定为：

```
e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
```

### 2.2 签名值

```
signature = HMAC-SHA256(key = hmacSecret, message = canonical) 的小写 hex
```

### 2.3 固定示例（用于联调自查）

参数：

```
secret    = demo-secret
method    = POST
path      = /api/ingest/match
timestamp = 1730000000
nonce     = 0123456789abcdef0123456789abcdef
```

请求体（`body`）：

```json
{"schemaVersion":1,"matchId":"3f2c8a1e-6c2a-4a1e-9f3e-1a2b3c4d5e6f","arena":"castle","arenaDisplay":"Castle","mode":"4v4v4v4","startedAt":"2026-11-08T12:00:00.000Z","endedAt":"2026-11-08T12:18:00.000Z","endReason":"team_eliminated","winnerTeam":"红队","isDraw":false,"abnormal":false,"abnormalReason":null,"players":[],"events":[]}
```

`SHA256_HEX(body)`：

```
ad3c184e55ee6f137bc7bdab54c6c0f3fc3f171b97681c2911a37744c016618c
```

canonical（`\n` 为真实换行）：

```
POST
/api/ingest/match
1730000000
0123456789abcdef0123456789abcdef
ad3c184e55ee6f137bc7bdab54c6c0f3fc3f171b97681c2911a37744c016618c
```

签名：

```
0390927d3d557d5e6f8b7707a76b0d86a13ea14dca5e472420b4f8f453f444bb
```

> 该向量同时写进了单元测试 `HmacSignerTest`，并用 Python 独立实现交叉验证过。

## 3. POST `/ingest/match`

### 3.1 请求体（schemaVersion = 1）

```json
{
  "schemaVersion": 1,
  "matchId": "3f2c8a1e-6c2a-4a1e-9f3e-1a2b3c4d5e6f",
  "arena": "castle",
  "arenaDisplay": "Castle",
  "mode": "4v4v4v4",
  "startedAt": "2026-11-08T12:00:00.000Z",
  "endedAt": "2026-11-08T12:18:00.000Z",
  "endReason": "team_eliminated",
  "winnerTeam": "红队",
  "isDraw": false,
  "abnormal": false,
  "abnormalReason": null,
  "players": [
    { "uuid": "…", "gameId": "Steve", "team": "红队", "isWinner": true,
      "kills": 8, "finalKills": 2, "deaths": 3, "finalDeaths": 1,
      "bedsBroken": 1, "placement": 1 }
  ],
  "events": [
    { "type": "kill", "at": "2026-11-08T12:05:00.000Z",
      "actor": "Steve", "target": "Alex", "team": "红队" }
  ]
}
```

字段说明：

| 字段 | 类型 | 可空 | 说明 |
|---|---|---|---|
| `schemaVersion` | int | 否 | 固定 `1` |
| `matchId` | string(uuid) | 否 | 插件开赛时生成，重试始终复用同一个值 |
| `arena` | string | 是 | BedWars1058 竞技场配置名（如 `castle`） |
| `arenaDisplay` | string | 是 | 竞技场显示名（如 `Castle`，已去除颜色代码） |
| `mode` | string | 是 | BedWars1058 竞技场 `group` 字段（如 `4v4v4v4`） |
| `startedAt` | string(ISO) | 是 | 比赛进入 `playing` 的时刻 |
| `endedAt` | string(ISO) | 是 | 比赛结束时刻；崩溃恢复时为最后一次快照时间 |
| `endReason` | string | 是 | 见 3.2 |
| `winnerTeam` | string | 是 | 冠军队显示名（如 `红队`）；无法确定时 `null` |
| `isDraw` | boolean | 否 | BedWars1058 无平局机制，当前恒 `false` |
| `abnormal` | boolean | 否 | 是否异常比赛（异常比赛由后台人工裁定） |
| `abnormalReason` | string | 是 | 异常原因，见 3.3 |
| `players[]` | array | 否 | 本场参赛玩家（不含旁观者） |
| `players[].uuid` | string(uuid) | 是 | 玩家 UUID |
| `players[].gameId` | string | 是 | 玩家游戏内名字 |
| `players[].team` | string | 是 | 队伍显示名（来自 BedWars1058 语言文件，如 `红队`） |
| `players[].isWinner` | boolean | 否 | 是否冠军队成员 |
| `players[].kills` | int | 否 | 击杀数（含夺命击杀，口径见 DATA-MAPPING） |
| `players[].finalKills` | int | 否 | 最终击杀数 |
| `players[].deaths` | int | 否 | 死亡数 |
| `players[].finalDeaths` | int | 否 | 最终死亡数 |
| `players[].bedsBroken` | int | 否 | 破坏床数 |
| `players[].placement` | int \| null | 是 | 名次：冠军队 = 1；其余为**队伍**名次；无法确定时 `null` |
| `events[]` | array | 否 | 事件时间线 |
| `events[].type` | string | 否 | `kill` / `death` / `bed_break` / `join` / `leave` / `rejoin` / `team_eliminated` |
| `events[].at` | string(ISO) | 是 | 事件发生时间 |
| `events[].actor` | string | 是 | 动作发起者（玩家名/队伍名） |
| `events[].target` | string | 是 | 动作目标（玩家名/队伍名） |
| `events[].team` | string | 是 | actor 所属队伍显示名 |

### 3.2 `endReason` 取值

| 值 | 含义 | `abnormal` |
|---|---|---|
| `team_eliminated` | 正常结束：最后一支队伍存活（BedWars1058 `checkWinner()` 唯一正常路径） | false |
| `arena_restart` | 竞技场重启/状态被打断，且未收到 `GameEndEvent` | true |
| `arena_disabled` | 竞技场被禁用 | true |
| `server_shutdown` | 服务器正常关服时比赛仍在进行 | true |
| `server_crash` | 由崩溃快照恢复（last snapshot） | true |
| `no_winner` | 结束但无法确定胜者（生命周期完整但数据冲突） | true |

### 3.3 `abnormalReason` 取值

与 `endReason` 的异常值一一对应（`team_eliminated` 时为 `null`）。

### 3.4 响应

```json
{ "ok": true, "matchId": 12, "state": "pending", "duplicate": false }
```

- 成功：HTTP 200
- 重复 `matchId`（同一服务器）：HTTP 200 + `"duplicate": true`，服务端不重复写入
- 未绑定/未知 `gameId`：仍写入 `match_players`，`user_id = null`
- 校验失败：HTTP 400 `invalid_payload`
- 签名失败：HTTP 401 `bad_signature` / `stale_timestamp` / `nonce_reused`

插件侧判定：**HTTP 2xx 或响应体包含 `"duplicate":true`** 才删除本地队列文件。

## 4. GET `/ingest/ping`

用于联调。与上报使用同一套 HMAC 头（无请求体，`SHA256_HEX("")` 使用 2.1 的空串哈希）。

响应：

```json
{ "ok": true, "server": "s1", "time": 1730000000 }
```

插件命令：`/xbedsync ping`。

## 5. 幂等与重试规则（插件实现）

1. `matchId` 在开赛时生成后固定；重试、重启补发均使用同一 `matchId`。
2. 本地队列：`plugins/XBedSync/queue/<matchId>.json`，原子写（临时文件 + rename）。
3. 发送在异步线程执行，不阻塞主线程。
4. 失败按指数退避重试：`1s → 2s → 4s …`，上限 `retry.maxDelaySeconds`（默认 300s）。
5. 只有 HTTP 2xx 或 `duplicate:true` 才删除队列文件；否则数据永久保留，`/xbedsync status` 可见。
6. 启动时扫描 `queue/*.json` 自动补发；`*.partial.json`（崩溃快照）先升级为 `*.json` 再补发。
7. 长期失败（例如密钥错误、域名不可达）不会丢数据，需要管理员排查后 `/xbedsync retry`。

## 6. curl 联调示例

### 6.1 自动计算签名（推荐，Bash + openssl）

```bash
#!/usr/bin/env bash
set -euo pipefail

API_BASE="${API_BASE:-https://xbed.xuanjian.top/api}"
SERVER_KEY="${SERVER_KEY:-s1}"
HMAC_SECRET="${HMAC_SECRET:?请通过环境变量提供 hmacSecret}"
BODY_FILE="${1:?用法: $0 payload.json}"

TS="$(date +%s)"
NONCE="$(openssl rand -hex 16)"
BODY_SHA="$(openssl dgst -sha256 -hex "$BODY_FILE" | sed 's/^.*= //')"
CANONICAL="$(printf 'POST\n/api/ingest/match\n%s\n%s\n%s' "$TS" "$NONCE" "$BODY_SHA")"
SIG="$(printf '%s' "$CANONICAL" | openssl dgst -sha256 -hmac "$HMAC_SECRET" -hex | sed 's/^.*= //')"

curl -sS -X POST "$API_BASE/ingest/match" \
  -H "Content-Type: application/json; charset=utf-8" \
  -H "X-XBed-Server: $SERVER_KEY" \
  -H "X-XBed-Timestamp: $TS" \
  -H "X-XBed-Nonce: $NONCE" \
  -H "X-XBed-Signature: $SIG" \
  --data-binary @"$BODY_FILE"
```

### 6.2 Python 参考实现

```python
import hashlib, hmac, json, os, secrets, time, urllib.request

api_base = os.environ.get("API_BASE", "https://xbed.xuanjian.top/api")
server_key = os.environ["SERVER_KEY"]
secret = os.environ["HMAC_SECRET"].encode()

body = open("payload.json", "rb").read()
ts = str(int(time.time()))
nonce = secrets.token_hex(16)
canonical = "\n".join([
    "POST", "/api/ingest/match", ts, nonce,
    hashlib.sha256(body).hexdigest(),
])
signature = hmac.new(secret, canonical.encode(), hashlib.sha256).hexdigest()

req = urllib.request.Request(
    f"{api_base}/ingest/match",
    data=body,
    method="POST",
    headers={
        "Content-Type": "application/json; charset=utf-8",
        "X-XBed-Server": server_key,
        "X-XBed-Timestamp": ts,
        "X-XBed-Nonce": nonce,
        "X-XBed-Signature": signature,
    },
)
with urllib.request.urlopen(req) as resp:
    print(resp.status, resp.read().decode())
```

### 6.3 固定示例（完整 payload + 签名值）

以下为文档示例（`secret=demo-secret`、`timestamp=1730000000`、`nonce=0123…cdef`），
签名已由 Java 插件实现与 Python 独立实现双重校验：

payload：

```json
{"schemaVersion":1,"matchId":"3f2c8a1e-6c2a-4a1e-9f3e-1a2b3c4d5e6f","arena":"castle","arenaDisplay":"Castle","mode":"4v4v4v4","startedAt":"2026-11-08T12:00:00.000Z","endedAt":"2026-11-08T12:18:00.000Z","endReason":"team_eliminated","winnerTeam":"红队","isDraw":false,"abnormal":false,"abnormalReason":null,"players":[{"uuid":"aaaaaaaa-1111-2222-3333-444444444444","gameId":"Steve","team":"红队","isWinner":true,"kills":8,"finalKills":2,"deaths":3,"finalDeaths":1,"bedsBroken":1,"placement":1},{"uuid":"bbbbbbbb-1111-2222-3333-444444444444","gameId":"Alex","team":"蓝队","isWinner":false,"kills":2,"finalKills":0,"deaths":5,"finalDeaths":2,"bedsBroken":0,"placement":4}],"events":[{"type":"kill","at":"2026-11-08T12:05:00.000Z","actor":"Steve","target":"Alex","team":"红队"}]}
```

`SHA256_HEX(body)`：

```
329dd01911c2fce847e2cd39332a8eedd78ff4b813b95666f85b5bce4a6512fd
```

`X-XBed-Signature`：

```
a8e64bf91b17b5d3578564dbe9a184807383f3e1aab676a4dfce01d5b18fd0de
```

> 注意：时间戳有 ±300s 窗口，线上联调请用 6.1/6.2 的动态脚本，不要直接复制固定值。

## 7. 错误码（服务端）

| code | HTTP | 说明 |
|---|---|---|
| `invalid_payload` | 400 | 请求体字段缺失/类型错误/不符合 schema |
| `bad_signature` | 401 | 签名不匹配（serverKey 与 secret 不配对、拼串错误等） |
| `stale_timestamp` | 401 | 时间戳超出 ±300s（检查服务器时钟/NTP） |
| `nonce_reused` | 401 | Nonce 5 分钟内重复 |
| `conflict` | 409 | 状态冲突 |
| `rate_limited` | 429 | 频率限制 |

## 8. 版本兼容策略

- `schemaVersion` 当前为 `1`。
- 服务端接受「未知字段忽略、缺失可空字段按 null」的向前兼容策略；插件升级前请与网站侧确认契约版本。
- 若契约升级到 `2`，插件需同步调整 `PayloadBuilder.SCHEMA_VERSION` 并回归本文示例与单元测试。
