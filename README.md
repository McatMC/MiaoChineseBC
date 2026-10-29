# MiaoChineseBC

MiaoMC BungeeCord 控制台汉化与连接日志折叠插件。把 BungeeCord 核心输出的英文日志按类型改写为详细的中文，并把同一 IP 短时间内的重复请求折叠为一条汇总，让控制台不再被服务器列表 Ping、扫描器和断线日志刷屏。

由 QuietCord 重写而来：原插件只能隐藏 Ping 日志，本插件改为折叠，并覆盖全部连接相关日志。

## 功能

- **折叠重复日志**
  - 同一来源（优先按 IP，没有 IP 时按玩家名）在折叠窗口内只显示第一条，其余计数，窗口结束时输出一条汇总。
  - 持续刷屏的来源每个窗口只输出一条汇总。
  - 一个窗口内新出现的来源过多（默认超过 30 个）时，不再逐条显示，合并为一条，应对大量 IP 同时刷 Ping 或登录。
  - 跟踪的来源数量有上限，攻击期间不会占用过多内存。
- **按类型详细汉化**：19 种连接日志各有独立的中文格式，并可分别设置显示/隐藏、折叠/不折叠。
- **补充细节**：客户端版本（协议号 47–777，对应 1.8 至 26.3）、客户端填写的服务器地址、出错阶段（握手/登录、玩家客户端、子服），常见英文报错替换为中文，长类名自动缩短，扫描包中的十六进制数据解码为可读文本。
- **系统日志汉化**：监听端口、关闭流程、插件加载、缺少前置插件、事件耗时等，以及 Waterfall 系分支特有的日志；规则写在 `messages.yml` 中，可自行补充。
- **日志文件纯文本**：写入 `logs/proxy.log` 时去掉 `§` 颜色代码，控制台仍保留颜色。
- **兼容多种分支**：同时识别上游 BungeeCord 与 Waterfall 系分支的连接日志格式。

## 输出示例

```
[Ping] 127.0.0.1 刷新了服务器列表 | 客户端 1.21-1.21.1 | 地址 play.example.com
[登录] 127.0.0.1 发起登录请求 | 客户端 1.21-1.21.1 | 地址 play.example.com
[断开] Steve 被断开连接，原因：无法连接到默认或备用子服，请检查子服地址、端口与防火墙 AnnotatedConnectException
[下线] Steve 已断开与代理的连接 (127.0.0.1)
[错误] 127.0.0.1 处理连接时出错（握手/登录阶段）：QuietException: 登录阶段收到非 Minecraft 数据包（常见于端口扫描或 HTTP 访问） [内容: ET / HTTP/1.1..H]
[折叠] 127.0.0.1 在 11 秒内又产生了 3 条「服务器列表 Ping」
[折叠] 11 秒内另有 8 个来源产生了 8 条「服务器列表 Ping」，因来源过多已合并
正在停用插件
代理端已关闭，再见
```

## 运行环境

- Java 8 及以上
- BungeeCord 26.1（已测试）、Waterfall 1.21（已测试）及其他 BungeeCord 分支

## 安装

1. 将 `MiaoChineseBC-<版本>.jar` 放入 **BungeeCord** 的 `plugins` 目录。
2. 如果装有 QuietCord，请删除它，两者功能重叠。
3. 启动 BungeeCord，按需修改 `plugins/MiaoChineseBC/` 下的 `config.yml` 与 `messages.yml`，然后执行 `/miaochinesebc reload`。

Ping 日志需要 BungeeCord `config.yml` 中 `log_pings: true` 才会产生；如需折叠指令日志，需开启 `log_commands: true`。

## 指令与权限

| 指令 | 说明 |
|---|---|
| `/miaochinesebc reload`（别名 `/mcbc`） | 重载 `config.yml` 与 `messages.yml` |
| `/miaochinesebc status` | 查看正在跟踪的来源数、待汇总条数与累计折叠条数 |

| 权限 | 说明 |
|---|---|
| `miaochinesebc.admin` | 使用 `/miaochinesebc` |

## 日志类型

| 类型 | 说明 | 默认折叠 |
|---|---|---|
| `ping` | 刷新服务器列表 | 是 |
| `login` | 客户端发起登录请求 | 是 |
| `server-connect` | 玩家正在连接子服 | 否 |
| `server-disconnect` | 玩家与子服的连接关闭 | 否 |
| `proxy-disconnect` | 玩家断开与代理的连接 | 否 |
| `kick` | 玩家被断开连接，附带原因 | 否 |
| `command` | 玩家执行指令 | 否 |
| `read-timeout` / `write-timeout` | 读取 / 写入超时 | 是 |
| `corrupted-frame` / `bad-packet` / `overflow` | 数据帧损坏 / 无法解析的数据包 / 数据包溢出 | 是 |
| `network-error` | 网络异常（连接被重置等） | 是 |
| `exception` | 处理连接时的其他异常（扫描器、HTTP 请求等） | 是 |
| `packet-limit` | 超出发包速率限制 | 是 |
| `no-client` | 子服连接建立时玩家已离线 | 否 |
| `other-connect` / `other-disconnect` | 无法识别的连接建立 / 关闭 | 否 |
| `system` | 启动、关闭、插件加载等系统日志 | 否 |

## 配置说明

`config.yml` 主要配置项：

```yaml
translate: true          # 改写为中文；false 时保留英文原文，折叠仍然生效
colors: true             # 保留颜色；面板显示 § 乱码时设为 false
plain-file-log: true     # 写入 proxy.log 时去掉颜色代码
detail-max-length: 160   # 异常详情最大长度
fold:
  window-seconds: 10     # 折叠窗口（秒）
  max-keys: 5000         # 同时跟踪的来源上限
  flood-threshold: 30    # 窗口内新来源超过此数量时合并，0 为关闭
types:
  ping: { show: true, fold: true }   # show: false 完全隐藏，等同旧 QuietCord 的过滤
  kick: { show: true, fold: false }
```

`messages.yml` 包含：

| 配置节 | 内容 |
|---|---|
| `formats` | 每种日志的中文格式 |
| `who` / `stage` | `{who}` 来源描述与 `{stage}` 连接阶段的写法 |
| `fold-summary` / `flood-summary` | 折叠汇总格式 |
| `labels` | 汇总中显示的类型名称 |
| `errors` | 错误信息与断开原因中的英文片段替换 |
| `system` | 系统日志汉化规则，支持 `match`（完全匹配模板）、`prefix`（前缀替换）、`regex`（正则） |
| `command` | `/miaochinesebc` 指令的提示文本 |

格式中可用的占位符见 `messages.yml` 顶部注释，支持 `&` 颜色代码与 `&#RRGGBB` 十六进制颜色。

## 注意事项

- **只处理 BungeeCord 核心日志。** 其他插件通过各自的日志通道输出，不会被汉化或折叠。
- **启动最开头的几行仍是英文**（如 `Enabled BungeeCord version`），它们在插件加载之前就已输出。
- **排查连接问题时可临时关闭折叠**：将相关类型的 `fold` 设为 `false` 后执行 `/miaochinesebc reload`。被折叠的日志只保留汇总，不会写入日志文件。
- 关服时最后出现的 `java.util.logging.ErrorManager … NullPointerException` 是 BungeeCord 自身关闭日志文件后产生的，与本插件无关。
- 更新插件后，旧配置文件不会自动补充新配置项，请对照默认配置手动添加，或删除后重新生成。

## 下载

在 [Releases](https://github.com/McatMC/MiaoChineseBC/releases) 页面下载最新的 `MiaoChineseBC-<版本>.jar`。每次推送的构建产物也可以在 Actions 页面对应的运行记录中下载。

## 构建

```
mvn package
```

产物为 `target/MiaoChineseBC-<版本>.jar`。

### 自动发布

GitHub Actions 会在每次推送和 Pull Request 时自动构建。推送到 `main` 时，如果 `pom.xml` 中的版本号还没有对应的 Release，会自动创建 `v<版本>` 标签和 Release 并附上 jar；版本号以 `-SNAPSHOT` 结尾时不发布。发布新版本只需修改 `pom.xml` 中的 `<version>` 并推送。
