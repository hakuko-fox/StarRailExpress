# StarRailExpress 全部指令文档

> 本文档涵盖 `starrailexpress` 模组包中所有注册的指令及其详细用法。

---

## 目录

- [一、核心命令 (`io.wifi.starrailexpress.content.command`)](#一核心命令-iowifistarrailexpresscontentcommand)
- [二、HarpyModLoader 命令 (`org.agmas.harpymodloader.commands`)](#二harpymodloader-命令-orgagmasharpymodloadercommands)
- [三、Noelle's Roles 命令 (`org.agmas.noellesroles.commands`)](#三noelles-roles-命令-orgagmasnoellesrolescommands)
- [四、`tmm:game` 聚合命令](#四tmmgame-命令跨多个包注册)
- [五、已知别名列表](#五已知别名列表)
- [六、投票 & 播报命令](#六投票--播报命令)
- [七、Noelle's Roles 额外命令](#七noelles-roles-额外命令)
- [八、名签系统 (NameTag)](#八名签系统-nametag)
- [九、模组白名单 (Mod Whitelist)](#九模组白名单-mod-whitelist)
- [十、皮肤同步 (Skin Sync)](#十皮肤同步-skin-sync)
- [十一、其他命令](#十一其他命令)

---

## 一、核心命令 (`io.wifi.starrailexpress.content.command`)

### `tmm:afk` — AFK 管理
- **权限**: `2`
- **结构**:
  - `reset` — 重置 AFK 计时器
  - `status` — 查看 AFK 状态
  - `setTime <seconds>` — 设置自己的 AFK 时间
  - `setTime <targets> <seconds>` — 设置指定玩家的 AFK 时间
- **用途**: 管理玩家挂机检测系统

---

### `stop_when_over` — 游戏结束自动关服
- **权限**: `4`
- **结构**: `<enabled>` (bool)
- **用途**: 启用/禁用游戏结束后自动关闭服务器

---

### `tmm:autoStart` — 自动开始
- **权限**: `2`
- **结构**: `<seconds>` (0-60)
- **用途**: 设置倒计时自动开始游戏，0 禁用

---

### `tmm:config` — 运行时配置
- **权限**: `3`
- **结构**:
  - (无参) — 显示当前配置概览
  - `config <config> <entry> get` — 查看配置项
  - `config <config> <entry> set <value>` — 修改配置项
  - `reload` — 重载所有配置文件
  - `auto_present <flag>` (bool) — 启用/禁用回合制自动预设
  - `set_round <round>` (int) — 设置当前回合数
  - `reset` — 重置为默认值
- **用途**: 支持 `SREConfig`, `HarpyModLoaderConfig`, `NoellesRolesConfig`, `StupidExpressConfig` 的运行时管理

---

### `tmm:createpoint` — 创建路径点
- **权限**: 无
- **结构**: `<pos> <path>` (BlockPos + greedy String)
- **用途**: 创建路径点，格式 `path/name`

---

### `sre:custom_replay` — 自定义回放事件
- **权限**: `2`
- **结构**:
  - `record <message>` — 记录回放事件
  - `record_hidden <message>` — 记录隐藏的回放事件
- **用途**: 在游戏回放中记录自定义事件标记

---

### `sre:show_replay` — 展示游戏回放
- **权限**: `2`
- **用途**: 向玩家展示生成的游戏回放

---

### `tmm:entityData` — 实体数据
- **权限**: `2`
- **结构**: `set <targets> <data>`
- **用途**: 为实体附加自定义持久化字符串数据

---

### `forceTeam` — 强制设置玩家队伍
- **权限**: `3`
- **结构**: `<players>` (EntitySelector) + (`innocent`|`neutral`|`neutral_for_killer`|`killer`|`vigilante`|`reset`)
- **用途**: 强制设置玩家的阵营类型，影响下一局游戏的职业分配权重

---

### `tmm:fourthroom` — 第四房间模式管理
- **权限**: 无 (基础), `generate_test_scene` 需 `2`
- **结构**:
  - `status` — 查看游戏状态
  - `generate_test_scene [origin]` — 生成测试场景
  - `reveal` — 揭示自身身份
  - `play <cardId> [target]` — 使用卡牌
  - `endturn` — 结束回合
  - `buy <itemId>` — 购买商店物品
  - `use_item <itemId> <target>` — 使用刺杀物品
  - `task_complete` — 完成任务
  - `search_notes` — 搜索笔记
- **用途**: 第四房间游戏模式的完整管理命令

---

### `tmm:giveRoomKey` — 给房间钥匙
- **权限**: `2`
- **结构**: `<roomName>` (string)
- **用途**: 给执行者一把指定房间的钥匙物品

---

### `listGameRoles` — 列出游戏角色
- **权限**: `2`
- **用途**: 列出当前游戏中所有玩家的角色和修饰符

---

### `sre:monitor` — 监控摄像机管理
- **权限**: `2`
- **结构**: `search <block_pos>` + (`in_reset_template` | `<range>`)
- **用途**: 搜索并配置安全监控摄像头的相机位置

---

### `sre:area_manager` — 区域管理器
- **权限**: `2`
- **结构**:
  - `set_area_limits <min> <max>` — 设置区域边界
  - `reset_area` — 重置区域配置
  - `add_door <pos>` — 添加门位置
  - `remove_door <pos>` — 移除门位置
  - `add_task_block <pos>` — 添加任务方块
  - `remove_task_block <pos>` — 移除任务方块
  - `add_spawn <pos>` — 添加玩家出生点
  - `remove_spawn <pos>` — 移除玩家出生点
  - `list_doors` — 列出所有门
  - `list_spawns` — 列出所有出生点
  - `clear_doors` — 清除所有门
  - `clear_spawns` — 清除所有出生点
- **用途**: 地图区域的游戏实体配置管理

---

### `tmm:sre_easy_mode` — 简易地图工具
- **权限**: `2`
- **结构**:
  - `set_seat` — 设置座椅位置
  - `set_door` — 设置门位置
  - `set_monitor` — 设置监控位置
  - `set_blackout_global` — 设置全局关灯位置
  - `set_blackout_single` — 设置单区关灯位置
  - `set_room_spawn` — 设置房间出生点
  - `set_spawn` — 设置出生位置
- **用途**: 简易地图设置工具，替代 `sre:area_manager` 的快捷版本

---

### `tmm:mapvote` — 地图投票
- **权限**: 无
- **结构**: `<map_id>` (string)
- **用途**: 为指定地图投票，用于地图投票系统

---

### `tmm:money` — 金币管理
- **权限**: `2`
- **结构**:
  - `get` — 查看自己的金币
  - `get <player>` — 查看指定玩家的金币
  - `set <amount>` — 设置自己的金币
  - `set <player> <amount>` — 设置指定玩家的金币
  - `add <amount>` — 给自己增加金币
  - `add <player> <amount>` — 给指定玩家增加金币
- **用途**: 管理玩家金币

---

### `tmm:mood` — 心情管理
- **权限**: `2`
- **结构**:
  - `<value>` (float) — 设置心情值
  - `get` — 查看心情值
  - `add <value>` (float) — 增减心情值
- **用途**: 管理玩家心情值

---

### `tmm:narrator` — 语音播报
- **权限**: `2`
- **结构**:
  - `say <id> <text>` — 创建带 ID 的播报 TTS 任务
  - `interrupt <text>` — 打断当前播报
  - `stop` — 停止所有播报
- **用途**: 全局语音播报系统（TTS）

---

### `tmm:networkstats` — 网络统计
- **权限**: `4`
- **结构**:
  - `stats` — 查看网络统计
  - `reset` — 重置网络统计
  - `config ...` — 配置网络统计参数
- **用途**: 监控和分析服务器网络性能

---

### `tmm:kick_non_op` — 非 OP 踢出
- **权限**: `2`
- **结构**: `<targets>` (EntitySelector)
- **用途**: 踢出指定的非 OP 玩家

---

### `tmm:participation` — 参与度
- **权限**: `2`
- **结构**: `<targets>` (EntitySelector) preset `<preset>`
- **用途**: 管理玩家参与度数据

---

### `tmm:player_inventory` — 查看玩家物品栏
- **权限**: `2`
- **结构**: `<player>` (Player)
- **用途**: 以 GUI 形式查看指定玩家的物品栏

---

### `sre:reload_map_config` — 重载地图配置
- **权限**: `2`
- **用途**: 重载地图配置文件

---

### `sre:reload_ready_area` — 重载准备区域
- **权限**: `2`
- **用途**: 重载玩家准备区域配置

---

### `sre:replay` — 回放管理
- **权限**: `2`
- **结构**:
  - `view` — 查看当前回放
  - `toggle_pause` — 暂停/继续回放
  - `speed <rate>` (float) — 设置播放速度
  - `jump <tick>` (int) — 跳转到指定 tick
  - `stop` — 停止回放
- **用途**: 游戏回放系统的播放控制

---

### `sre:auto_train_reset` — 自动列车重置
- **权限**: `2`
- **结构**: `<enabled>` (bool)
- **用途**: 设置是否自动重置列车地图

---

### `sre:bound` — 边界设置
- **权限**: `2`
- **结构**: `<bound>` (bool)
- **用途**: 设置游戏是否限制玩家在边界内

---

### `sre:death_penalty` — 死亡惩罚
- **权限**: `2`
- **结构**:
  - `status` — 查看状态
  - `timeout <seconds>` — 设置超时
  - `min_players <count>` — 最小玩家数
  - `enable` / `disable` — 启用/禁用
- **用途**: 管理死亡惩罚系统

---

### `tmm:timer` — 游戏倒计时
- **权限**: `2`
- **结构**: `set <seconds>` (int)
- **用途**: 设置游戏倒计时

---

### `sre:visual` — 可视化设置
- **权限**: `2`
- **结构**:
  - `fade` — 淡入淡出
  - `show_areas` — 显示区域
  - `hide_areas` — 隐藏区域
  - `show_spawns` — 显示出生点
  - `hide_spawns` — 隐藏出生点
- **用途**: 管理员可视化工具

---

### `tmm:show_selected_map_ui` — 显示已选地图 UI
- **权限**: 无
- **用途**: 向玩家展示当前选中地图的投票 UI

---

### `tmm:shield` — 护盾管理
- **权限**: `2`
- **结构**:
  - `get` — 查看护盾数量
  - `set <amount>` — 设置护盾数
  - `add <amount>` — 增加护盾
  - `remove <amount>` — 减少护盾
- **用途**: 管理玩家护盾值

---

### `tmm:stats` — 统计数据
- **权限**: 无
- **结构**: (无参) — 显示自己的统计数据
- **用途**: 查看个人游戏统计数据

---

### `tmm:skins` — 皮肤管理
- **权限**: `2`
- **结构**: `add <item>` — 为手中物品添加自定义皮肤
- **用途**: 管理物品皮肤

---

### `tmm:stamina` — 体力管理
- **权限**: `2`
- **结构**:
  - `get [player]` — 查看体力
  - `set <amount>` — 设置体力
  - `multiply <factor>` — 倍率调整
- **用途**: 管理玩家体力冲刺值

---

### `tmm:start` — 开始游戏
- **权限**: `2`
- **结构**: `<gameMode>` (game mode ID) `[startTimeInMinutes]` (int)
- **用途**: 启动指定游戏模式

---

### `tmm:stop` — 停止游戏
- **权限**: `2`
- **用途**: 停止当前正在运行的游戏

---

### `tmm:switchmap` — 切换地图
- **权限**: `2`
- **结构**:
  - `<mapId>` (string) — 切换到指定地图
  - `list` — 列出所有可用地图
  - `random` — 随机切换地图
- **用途**: 切换服务器地图

---

### `tmm:toggle_waypoints` — 路径点开关
- **权限**: `2`
- **结构**:
  - `on` / `off` — 启用/禁用所有路径点
  - `toggle <name>` (string) — 切换指定路径点
  - `list` — 列出所有路径点
  - `reload` — 重载路径点
- **用途**: 管理员路径点可视化和管理

---

### `sre:reloadRoleConfig` — 重载自定义职业配置
- **权限**: `3`
- **用途**: 重新加载并同步所有客户端自定义职业配置

---

## 二、HarpyModLoader 命令 (`org.agmas.harpymodloader.commands`)

### `changeRole` — 改变玩家职业
- **权限**: `3`
- **结构**:
  - `<player> reset` — 重置玩家职业为平民
  - `<player> <role>` — 改变玩家职业
  - `<player> <role> <record_replay>` (bool) — 是否记录回放
  - `<player> <role> <record_replay> <add_stats>` (bool) — 是否计入统计
- **用途**: 改变玩家的职业，支持回放记录和数据统计控制

---

### `changeModifier` — 改变玩家修饰符
- **权限**: `3`
- **结构**:
  - `<player> clear` — 清除玩家所有修饰符
  - `<player> <modifier>` — 添加指定修饰符
- **用途**: 管理玩家身上的修饰符

---

### `forceRole` — 强制分配职业
- **权限**: `3`
- **结构**: `<player> <role>`
- **用途**: 为玩家强制分配职业（不触发常规角色分配流程）

---

### `forceModifier` — 强制分配修饰符
- **权限**: `3`
- **结构**: `<modifier>` (玩家选择器)
- **用途**: 为指定玩家强制分配修饰符

---

### `setRoleCount` — 设置职业数量
- **权限**: `3`
- **结构**: `<killerCount> <vigilanteCount> <neutralCount>`
- **用途**: 设置下一局中杀手、警长、中立的数量（覆盖自动计算）

---

### `setRoleWeight` — 设置职业权重
- **权限**: `3`
- **结构**: `<roleType> <weight>`
- **用途**: 设置角色类型的权重值

---

### `setPlayerWeight` — 设置玩家权重
- **权限**: `3`
- **结构**: `<player> <roleType> <weight>`
- **用途**: 设置指定玩家的角色类型权重

---

### `toggleCustomRoleWeights` — 切换自定义职业权重
- **权限**: `3`
- **结构**: `<enabled>` (bool)
- **用途**: 启用/禁用自定义角色权重系统

---

### `setOccupationRole` — 设置职业占用数量
- **权限**: `3`
- **结构**: `<role> <count>`
- **用途**: 设置职业占用角色池的数量

---

### `setEnabledRole` — 启用/禁用职业
- **权限**: `3`
- **结构**: `<role> <enabled>` (bool)
- **用途**: 控制指定职业是否在本局可用

---

### `setEnabledModifier` — 启用/禁用修饰符
- **权限**: `3`
- **结构**: `<modifier> <enabled>` (bool)
- **用途**: 控制指定修饰符是否在本局可用

---

### `setCompanionRole` — 设置绑定职业
- **权限**: `3`
- **结构**: `<role> <companionRole>`
- **用途**: 设置两个职业的绑定生成关系（一个出现则另一个也会出现）

---

### `listRoles` — 列出所有职业
- **权限**: 无
- **结构**:
  - (无参) — 列出所有职业
  - `<roleType>` — 按类型过滤列出
- **用途**: 查看所有已注册的职业及其详细信息

---

## 三、Noelle's Roles 命令 (`org.agmas.noellesroles.commands`)

### `broadcast` — 广播消息
- **权限**: `2`
- **结构**: `<message>` (greedy String)
- **用途**: 向所有玩家广播带格式的消息

---

### `noellesroles:mod_color` — 设置颜色参数
- **权限**: `2`
- **结构**: (传递 ModColorArgument)
- **用途**: 设置 Noelle's Roles 的颜色参数

---

### `game.roles_in_round` — 查看本局角色
- **权限**: `2`
- **结构**: `<targets>`
- **用途**: 查看指定玩家在本局中的角色详细信息

---

## 四、`tmm:game` 命令（跨多个包注册）

> `tmm:game` 是一个聚合命令，由多个不同包的文件分别注册子命令。

### `tmm:game role` — 职业管理 (GameUtilsCommand)
- **权限**: `2`
- **结构**:
  - `role <role>` — 改变自己职业（同步 + 记录回放）
  - `role <role> no_sync` — 静默改变职业（不同步）
  - `role <role> send_welcome` — 发送欢迎报幕
    - (无参) — 根据实际杀手数量自动报幕
    - `<killer_count>` (int) — 指定杀手数量（`-1` = 使用实际值）
    - `<killer_count> <role>` — 指定杀手数量 + 指定角色ID报幕
  - `sync_roles` — 同步所有角色数据到客户端
  - `sync_modifiers` — 同步所有修饰符数据到客户端
  - `sendjson <component>` — 发送 JSON 组件到客户端

---

### `tmm:game role role_change_mode` — 改变职业并欢迎 (ClassChangeTestCommand)
- **权限**: `3`
- **结构**: `role role_change_mode <player> <role> [record_replay] [add_stats]`

---

### `tmm:game role silent_change` — 静默改变职业 (GameUtilsCommand)
- **权限**: `2`
- **结构**: `role silent_change <role> [no_sync]`

---

### `tmm:game role send_welcome` — 发送欢迎报幕 (GameUtilsCommand)
- **结构**: `send_welcome [killer_count] [role]`（`-1`=自动杀手数）

---

### `tmm:game role` — 其他角色管理 (GameUtilsCommand)
- **结构**: `sync_roles` | `assign_event` | `remove_event`

---

### `tmm:game tests` — 测试工具 (GameUtilsCommand + GamblerMiracleCommand)
- **结构**: `vote_players` | `prayer` | `gambler_draw` | `gambler_miracle [player]` | `math [forced]`

---

### `tmm:game tasks` — 任务队列管理 (GameUtilsCommand)
- **结构**: `tasks list` | `tasks clear task_queue/task_list` | `tasks cancel task_queue/task_list <tid>/all`

---

### `tmm:game win` — 触发胜利 (GameUtilsCommand)
- **结构**: `win <id>` | `win CUSTOM <color> <id>` | `win CUSTOM_COMPONENT <color> <title> <subtitle>`

---

### `tmm:game reset` — 重置地图/实体 (GameUtilsCommand)
- **结构**: `reset blocks copy/simple` | `reset entity clear`

---

### `tmm:game scan` — 扫描地图数据 (GameUtilsCommand)
- **结构**: `scan` | `scan reset_points` | `scan task_points`

---

### `tmm:game blackout / monitor_broken / psycho` — 游戏状态控制 (GameUtilsCommand)
- **结构**: `blackout [duration|stop]` | `monitor_broken [duration|stop]` | `psycho stop`

---

### `tmm:game body` — 尸体操作 (GameUtilsCommand)
- **结构**: `body teleport` | `body kill` | `body as_run <command>`

---

### `tmm:game revive` — 复活 (GameUtilsCommand)
- **结构**: `revive <player> [to_body [remove_body]|pos]`

---

### `tmm:game kill` — 击杀玩家 (GameUtilsCommand)
- **结构**: `kill <victim> <death_reason> [killer] [spawn_body] [force]`

---

### `tmm:game timestop` — 时停 (GameUtilsCommand)
- **结构**: `timestop <duration> <message>` | `timestop stop`

---

### `tmm:game visual` — 视觉效果 (SetVisualCommand)
- **结构**: `visual snow/fog/hud <enabled>` | `visual trainSpeed <speed>` | `visual time <tod>` | `visual reset`

---

### `tmm:game bounds` — 边界限制 (SetBoundCommand)
- **结构**: `bounds <enabled>` (bool)

---

### `tmm:game time` — 倒计时 (SetTimerCommand)
- **结构**: `time [get]` | `time set <minutes> <seconds>`

---

### `tmm:game penalty` — 死亡惩罚 (SetDeathPenaltyCommand)
- **结构**: `penalty stop` | `penalty start <time> <after_detection>`

---

## 五、已知别名列表

| 原始命令 | 别名 | 说明 |
|----------|------|------|
| `tmm:start` | `start` | 开始游戏 |
| `tmm:stop` | `stop` | 停止游戏 |
| `tmm:switchmap` | `switchmap` | 切换地图 |
| `tmm:config` | `config` | 运行时配置 |
| `tmm:autoStart` | `autoStart` | 自动开始 |
| `tmm:afk` | `afk` | AFK 管理 |
| `tmm:money` | `money` | 金币管理 |
| `tmm:showStats` | `showStats` | 统计数据 |
| `tmm:skins` | `skins` | 皮肤管理 |
| `tmm:stamina` | `stamina` | 体力管理 |

> 注：`tmm:` 命名空间下的大部分命令在游戏内也可以通过 `/tmm:help` 或 TAB 补全查看完整列表。

---

## 六、投票 & 播报命令

### `sre:vote` — 游戏内投票系统 (SREVoteCommand)
- **权限**: 无
- **结构**:
  - `title <text>` — 设置投票标题
  - `add player <name>` — 添加玩家选项
  - `add text <content>` — 添加文本选项
  - `add item <itemId>` — 添加物品选项
  - `list` — 查看所有选项
  - `remove <index>` — 移除选项
  - `start` — 开始投票
  - `stop` — 停止投票
  - `pause` — 暂停投票
  - `resume` — 恢复投票
  - `clear` — 清除所有选项
  - `status` — 查看投票状态
  - `result` — 查看投票结果

---

### `sre:narrator` — 旁白语音播报 (NarratorCommand)
- **权限**: `2`
- **结构**: `say <id> <text>` | `interrupt <text>` | `stop`
- **用途**: 全局 TTS 语音播报

---

## 七、Noelle's Roles 额外命令

### `noellesroles config` — NoelleRole 专属配置 (org.agmas.noellesroles.commands.ConfigCommand)
- **权限**: `3`
- **结构**:
  - `reload` — 重载配置
  - `reset` — 重置配置
  - `accidentalKillPunishment <enabled>` (bool) — 设置误杀惩罚
  - `skillEchoEvent <enabled>` (bool) — 技能回声事件
  - `skillEchoRandom <seconds>` (int) — 技能回声随机间隔

---

### `noellesroles preset` — 职业预设管理 (PresetCommand)
- **权限**: `3`
- **结构**:
  - `apply <presetName>` — 应用预设
  - `list` — 列出所有预设
  - `create <presetName>` — 创建预设
  - `delete <presetName>` — 删除预设
  - `save` — 保存当前预设

---

### `noellesroles setmax` — 设置职业最大数量 (SetRoleMaxCommand)
- **权限**: `3`
- **结构**: `<roleId> <maxCount>`
- **用途**: 设置指定职业每局最大出现数量

---

### `room` — 房间系统 (RoomCommand)
- **权限**: `2`
- **结构**: 管理列车房间分配

---

### `vt_mode` — VT 模式 (VTCommand)
- **权限**: 无
- **用途**: 切换为主播模式

---

### `stuck` — 卡住救援 (StuckCommand)
- **权限**: 无
- **用途**: 当玩家卡在方块中时传送到安全位置

---

### `sre:helium` — 氦气效果 (HeliumCommand)
- **权限**: `2`
- **结构**: `<player> <enabled>` (bool)
- **用途**: 对指定玩家启用/禁用氦气变声效果

---

### `sre:infected` — 感染管理 (InfectedCommand)
- **权限**: `3`
- **结构**: `<player> <type> <duration>` — 设置玩家感染状态

---

### `nr_free_cam` — 自由视角 (AdminFreeCamCommand)
- **权限**: `2`
- **用途**: 退出死亡惩罚
---

### `sre:eggclear` — 清除布谷鸟蛋实体 (EggClearCommand)
- **权限**: `3`
- **用途**: 清除游戏中的布谷鸟蛋实体

---

### `DisplayItem` — 手持物品展示 (DisplayItemCommand)
- **权限**: 无
- **用途**: 在聊天栏中展示手持物品的信息

---

### `item extra` — 额外物品管理 (ExtraItemsManagerCommand)
- **权限**: `2`
- **结构**:
  - `set <itemId> <count>` — 设置额外物品
  - `list` — 列出额外物品
  - `clear` — 清除额外物品

---

### `goods:add` / `goods:remove` / `goods:list` — 商品管理 (GoodsManagerCommand)
- **权限**: `3`
- **结构**:
  - `goods:add player <name> <price> <goodsId>` — 为玩家添加商品
  - `goods:add item <itemId> <price> <goodsId>` — 添加物品类商品
  - `goods:remove player <name> <index>` — 移除玩家商品
  - `goods:remove stack <stackIndex>` — 移除该组商品
  - `goods:list` — 列出所有商品

---

### `tmm:nr fielditem` — 轮椅物品管理 (WheelchairFieldItemCommand)
- **权限**: `2`
- **用途**: 管理轮椅等场地物品的生成

---

### `repairshop` — 修机商店管理 (RepairShopCommand)
- **权限**: `2`
- **用途**: 打开修机模式的商店管理界面

---

### `repairrole` — 修机职业管理 (RepairRoleCommand)
- **权限**: `3`
- **结构**:
  - `force <player> <role>` — 强制分配修机职业
  - `clear` — 清除所有修机职业
  - `unlock` — 解锁所有修机职业

---

## 八、名签系统 (NameTag)

### `nametag:add` / `nametag:remove` / `nametag:set` / `nametag:get` / `nametag:list` / `nametag:clear` / `nametag:sync`
- **权限**: `2`
- **结构**:
  - `add <player> <text>` — 为玩家添加名签
  - `remove <player> <index>` — 移除指定名签
  - `set <player> <index> <text>` — 修改名签文本
  - `get <player>` — 查看玩家名签
  - `list` — 列出所有名签
  - `clear <player>` — 清除玩家所有名签
  - `sync` — 同步名签到所有客户端

---

## 九、模组白名单 (Mod Whitelist)

### `mw:reload` — 重载白名单 (ModWhitelistCommand)
- **权限**: `4`
- **用途**: 重新加载模组白名单配置

### `mw:maxplayers` — 最大玩家数 (ModWhitelistCommand)
- **权限**: `4`
- **结构**: `get` | `set <count>`
- **用途**: 查看/设置服务器最大玩家数

---

## 十、皮肤同步 (Skin Sync)

### `tmm:skinsync` — 物品皮肤同步 (SkinsNetworkSyncCommand)
- **权限**: `2`
- **结构**:
  - `config stop` — 停止皮肤同步配置
  - `sync` — 手动同步皮肤
  - `pull` — 拉取皮肤数据
  - `status` — 查看同步状态
  - `enable` / `disable` — 启用/禁用皮肤同步

---

## 十一、其他命令

### `cooldown` — 查看技能冷却 (GameUtilsCommand)
- **权限**: 无
- **用途**: 查看当前职业技能的冷却时间

### `sre:reloadRoleConfig` — 重载自定义职业 (CustomRoleReloadCommand)
- **权限**: `3`
- **用途**: 重新加载并同步所有客户端自定义职业配置

### `forceModifier` — 强制分配修饰符 (ForceModifierCommand)
- **权限**: `3`
- **结构**: `<modifier>` (修饰符选择器)
- **用途**: 为命令执行者强制添加修饰符

---


