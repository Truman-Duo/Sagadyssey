# 阵营声望系统 — CC 实施指令

> 以 `docs/faction-reputation-design.md` v1.5 为蓝本，按以下顺序逐一交给 CC 执行。
> 每步独立粘贴给 CC，等它完成并 `./gradlew build` 通过后再进入下一步。

---

## 第 1 步：数据层基础 — Faction + StandingLevel + InterFactionRelation

**目标：** 创建阵营系统的三个基础类型。

**给 CC 的指令：**

```
阅读 docs/faction-reputation-design.md 的以下章节：
- §3.2 阵营数据结构（Faction record）
- §4.2 声望等级判定（StandingLevel enum）
- §5.2 关系类型（InterFactionRelation enum）

然后在 src/main/java/com/jgeted/sagadyssey/npc/faction/ 下创建三个文件：

1. Faction.java — 阵营定义 record
   - 字段: id(String), displayName(String), color(int), defaultStanding(int), canBeHostile(boolean), canRecruit(boolean), iconTexture(ResourceLocation), bannerPattern(ResourceLocation), guiNodePosition(Optional<int[]>), reputationMultiplier(float), decayEnabled(boolean), decayTarget(int), decayRatePerDay(int)
   - 为 guiNodePosition 提供默认值 Optional.empty()
   - 为 reputationMultiplier 提供默认值 1.0f
   - 为 decayEnabled 提供默认值 true
   - 为 decayTarget 提供默认值（等于 defaultStanding）
   - 为 decayRatePerDay 提供默认值 1
   - record 的紧凑构造器：如果 decayTarget 未显式设置则用 defaultStanding
   - 包含一个静态的 Codec<Faction>（用 RecordCodecBuilder）
   - 中文注释

2. StandingLevel.java — 五级声望枚举
   - REVERED(75)、HONORED(25)、NEUTRAL(-24)、COLD(-74)、HATED(-100)
   - 每个枚举值有 minValue 和 translationKey 字段
   - 静态方法 fromValue(int standing): 根据声望值返回对应等级
   - 注意边界：>=75 → REVERED, >=25 → HONORED, >=-24 → NEUTRAL, >=-74 → COLD, 其余 → HATED

3. InterFactionRelation.java — 阵营间关系枚举
   - ALLY(0.5f), FRIENDLY(0.3f), NEUTRAL(0.0f), DISTRUSTFUL(-0.2f), ENEMY(-0.5f)
   - transferRate 字段，getter

创建完成后执行 ./gradlew build 验证编译通过。
```

**验证：** `./gradlew build` 无编译错误。

---

## 第 2 步：FactionRegistry + 关系矩阵 + JSON 加载器

**目标：** 创建 Registry、关系矩阵和 JSON 数据加载器。

**给 CC 的指令：**

```
阅读 docs/faction-reputation-design.md 的：
- §3.3 阵营注册
- §10.4 关系矩阵加载
- §11.1 阵营定义 JSON
- §11.2 阵营关系 JSON

在 src/main/java/com/jgeted/sagadyssey/npc/faction/ 下创建：

1. FactionRegistry.java
   - 用 NewRegistryEvent + RegistryBuilder<Faction> 创建数据驱动 Registry（不要用 DeferredRegister）
   - ResourceKey: sagadyssey:faction
   - sync(true)，defaultKey 设为 wilderness
   - 静态方法 get(ResourceLocation) → Faction
   - 静态方法 get(String id) → Faction
   - 静态方法 getAllFactions() → Collection<Faction>
   - 用 @SubscribeEvent 在 mod 构造器中注册 NewRegistryEvent

2. FactionRelationMatrix.java
   - 单例，维护 Map<String, Map<String, InterFactionRelation>>
   - 方法 getRelation(Faction a, Faction b): 查 a→b 关系，默认 NEUTRAL
   - 方法 getAffectedFactions(Faction source): 返回与 source 关系不为 NEUTRAL 的阵营列表
   - 方法 loadFromJson(): 从数据包加载关系矩阵

3. util/FactionDataLoader.java
   - 静态方法 loadFactions(): 从 data/<namespace>/faction/*.json 加载所有阵营 JSON
   - 静态方法 loadRelations(): 从 data/<namespace>/faction_relations.json 加载关系矩阵
   - JSON 校验：缺失必填字段时日志警告并跳过
   - 路径格式：data/sagadyssey/faction/kingdom.json（单 namespace，不是双写）

然后在 Sagadyssey.java 主类中：
- 注册 FactionRegistry（通过 @SubscribeEvent NewRegistryEvent）
- 在 mod 构造器末尾调用 FactionDataLoader.loadFactions() 和 FactionRelationMatrix 初始化

请创建完整的数据包 JSON 文件（放在 src/main/resources/data/sagadyssey/faction/ 下）：
- kingdom.json, merchant_guild.json, church.json, dwarven.json, mystic.json, wilderness.json, bandit.json
- 每个 JSON 格式按 §11.1，包含 id, display_name, color(十进制整数), default_standing, can_be_hostile, can_recruit, icon, gui_node_position, reputation_multiplier, decay_enabled, decay_target, decay_rate_per_day

并创建 faction_relations.json（放在 src/main/resources/data/sagadyssey/ 下）：
- 按 §11.2 格式写出关系矩阵
- 确保所有非NEUTRAL关系都写明（包括 merchant→kingdom FRIENDLY, dwarven→merchant FRIENDLY 等 §5.4 矩阵中的全部关系）

创建完成后执行 ./gradlew build 验证编译通过。
```

**验证：** `./gradlew build` 编译通过。JSON 文件存在且格式正确。

---

## 第 3 步：FactionStandings + Attachment 注册

**目标：** 创建玩家声望数据容器和 Attachment。

**给 CC 的指令：**

```
阅读 docs/faction-reputation-design.md 的：
- §4.1 玩家声望状态
- §10.2 数据存储

在 src/main/java/com/jgeted/sagadyssey/npc/faction/ 下创建：

1. FactionStandings.java
   - 字段：
     * Map<String, Integer> standings（阵营ID → 声望值 -100~100）
     * Map<String, Long> lastInteractionTick（阵营ID → 最后互动 tick，用于衰减判定，不持久化）
     * Map<String, StandingLevel> levelCache（阵营ID → 等级缓存，不持久化，transient）
     * Map<String, Integer> dailyRippleReceived（阵营ID → 今日涟漪累计，不持久化，transient）
   - 查询方法：
     * getValue(Faction) → int（内部调 getValue(faction.id())）
     * getValue(String) → int
     * getLevel(Faction) → StandingLevel
     * isHostile(Faction) → boolean（HATED 为 true）
     * canTradeWith(Faction) → boolean（不是 HATED 就可交易）
     * canRecruitFrom(Faction) → boolean（REVERED 为 true）
     * getHostileFactions()、getAlliedFactions()（HONORED+）
   - 修改方法：
     * modify(Faction, int delta) → 公开入口，内部调 modifyInternal(faction, delta, isRipple=false)
     * modifyInternal(Faction, int delta, boolean isRipple) → 见下方注意
     * setValue(Faction, int value)
     * resetDailyRippleCounters() → 日出时重置涟漪计数器
     * recordInteraction(String factionId, long gameTime) → 更新 lastInteractionTick
   - 注意：modifyInternal 的实现按 §4.3 八步流程
   - level 变化时触发 StandingLevelChangeEvent（NeoForge EVENT_BUS）
   - 创建静态 Codec<FactionStandings>：只序列化 standings map，其他字段 transient
   - 中文注释

2. FactionAttachments.java（参考 ResearchAttachments.java 的模式）
   - 路径：src/main/java/com/jgeted/sagadyssey/npc/faction/FactionAttachments.java
   - 用 DeferredRegister<AttachmentType<?>> 注册
   - AttachmentType<FactionStandings> FACTION_STANDINGS:
     * builder(FactionStandings::new)
     * .serialize(FactionStandings.CODEC)
     * .copyOnDeath()
   - 静态便捷方法：getStandings(Entity)、syncToClient(ServerPlayer)

然后在 Sagadyssey.java 的 mod 构造器中注册 FactionAttachments.ATTACHMENT_TYPES。

创建完成后执行 ./gradlew build 验证编译通过。
```

**验证：** `./gradlew build` 编译通过。

---

## 第 4 步：StandingModifier 核心计算引擎

**目标：** 实现声望变化的完整计算逻辑。

**给 CC 的指令：**

```
阅读 docs/faction-reputation-design.md 的：
- §4.3 声望升降规则（八步流程）
- §5.3 关系传递规则
- §4.3 步骤8关于 FactionStandingsUpdatePayload 的同步

在 src/main/java/com/jgeted/sagadyssey/npc/faction/ 下创建：

StandingModifier.java — 声望变化计算引擎

- 静态方法 applyModification(ServerPlayer player, Faction faction, int rawDelta, String reasonKey):
  1. 计算净增量 delta（整数）
  2. 应用 faction 的 reputationMultiplier（delta = Math.round(delta * multiplier)）
  3. 浮点乘积累加后四舍五入
  4. clamp(-100, currentValue + delta, 100)
  5. 写入 FactionStandings
  6. 判定 level 是否变化，若变化→触发 StandingLevelChangeEvent (NeoForge EVENT_BUS)
  7. 计算并应用涟漪（见下方）
  8. 通过 FactionStandingsUpdatePayload 同步到客户端

- 静态方法 applyRipple(ServerPlayer player, Faction sourceFaction, int sourceDelta):
  * 从 FactionRelationMatrix 获取所有 affectedFactions
  * 对每个 affectedFaction B：
    - 获取 source→B 的 transferRate
    - rippleDelta = Math.round(sourceDelta * transferRate)
    - 检查 B 的每日涟漪上限（dailyRippleReceived 中累计 < 25）
    - 调用 FactionStandings.modifyInternal(B, rippleDelta, isRipple=true)
    - 更新 dailyRippleReceived 计数器
  * isRipple=true 时不触发二次涟漪传播

- 静态方法 clampStanding(int value):
  * Math.max(-100, Math.min(100, value))

创建完成后执行 ./gradlew build 验证编译通过。
```

**验证：** `./gradlew build` 编译通过。

---

## 第 5 步：网络层 — Payload 定义与注册

**目标：** 实现阵营数据的网络同步。

**给 CC 的指令：**

```
阅读 docs/faction-reputation-design.md 的：
- §12.1 全量同步
- §12.2 增量同步
- §12.3 同步策略

参考项目中已有的 ResearchSyncPayload.java 和 SagadysseyNetworking.java 的模式。

在 src/main/java/com/jgeted/sagadyssey/npc/faction/network/ 下创建：

1. FactionDataSyncPayload.java — 登录时全量同步（服务端→客户端）
   - record: Map<String, Integer> standings, Map<String, String> relationSummary
   - 实现 CustomPacketPayload
   - 静态 TYPE + STREAM_CODEC（用 StreamCodec 组合子）
   - static handle(payload, context): 客户端更新 ClientFactionCache

2. FactionStandingsUpdatePayload.java — 增量同步（服务端→客户端）
   - record: String factionId, int newValue, StandingLevel newLevel, String reasonKey, List<AffectedFaction> rippleEffects
   - AffectedFaction 内部 record: String factionId, int delta, int newValue, StandingLevel newLevel
   - 实现 CustomPacketPayload
   - 静态 TYPE + STREAM_CODEC

3. RequestStandingsRefreshPacket.java — 客户端→服务端（GUI 保鲜）
   - record（无字段）
   - 服务端收到后回传全量 FactionDataSyncPayload

4. ClientFactionCache.java — 客户端缓存
   - 静态 Map<String, Integer> clientStandings
   - 静态方法 updateFromSync/updateFromUpdate
   - 供 GUI 渲染使用

然后在 SagadysseyNetworking.java 的 registerPayloads 方法中注册这三个包。

创建完成后执行 ./gradlew build 验证编译通过。
```

**验证：** `./gradlew build` 编译通过。

---

## 第 6 步：GUI — 声望图表主界面 + 阵营卡片

**目标：** 实现可交互的声望图表 GUI。

**给 CC 的指令：**

```
阅读 docs/faction-reputation-design.md 的：
- §8.1 声望图表
- §8.2 关系网络图
- §8.3 NPC 对话集成

在 src/main/java/com/jgeted/sagadyssey/npc/faction/gui/ 下创建：

1. ReputationChartScreen.java — 声望图表主界面
   - 继承 AbstractContainerScreen 或 Screen
   - 布局：上方关系网络图区域，下方阵营卡片网格
   - 打开时（init）向服务端发送 RequestStandingsRefreshPacket
   - 渲染时从 ClientFactionCache 读取数据
   - 左上角显示当前激活称号
   - 右上角关闭按钮
   - 中文注释

2. FactionCardWidget.java — 阵营卡片组件
   - 每个卡片显示：阵营纹章(32x32)、阵营名称、星标(1-5★→HATED~REVERED)、等级文字标签、声望进度条(10px高)
   - 星标与等级映射：1★=HATED, 2★=COLD, 3★=NEUTRAL, 4★=HONORED, 5★=REVERED
   - 进度条：当前值在当前等级范围内的位置，标注距离下一等级还差多少点
   - 悬停提示：具体声望数值、最近变化原因
   - 点击卡片切换到该阵营的关系网络图焦点模式
   - 颜色随等级变化：HATED红色、COLD橙色、NEUTRAL灰色、HONORED绿色、REVERED金色

3. RelationNetworkWidget.java — 关系网络图
   - 从 FactionRegistry 获取所有阵营，读取 guiNodePosition
   - 内置阵营用 guiNodePosition 手摆坐标，自定义阵营用环形自动布局
   - 节点用阵营纹章图标渲染
   - 连线用 Bresenham 算法逐像素画斜线
   - 连线颜色：绿色(ALLY/FRIENDLY)、灰色(NEUTRAL)、橘色(DISTRUSTFUL)、红色(ENEMY)
   - 第一阶段全部 1px 细线，暂不做粗细变化
   - 底部显示：当前声望值 + 涟漪传递率信息（如 "教会(ALLY ±50%)"）

4. FactionChatOverlay.java — NPC 对话叠层（预留）
   - 留空类，带 // TODO 注释说明后续在 NPC 交易 Screen 的 render 方法中叠加阵营纹章

注册快捷键：
- 默认快捷键 R 打开 ReputationChartScreen
- 在客户端注册 KeyMapping（通过 RegisterKeyMappingsEvent）
- 在客户端 tick 事件中检测按键

创建完成后执行 ./gradlew build 验证编译通过。
```

**验证：** `./gradlew build` 编译通过。进游戏按 R 能看到 GUI（即使数据是默认值）。

---

## 第 7 步：事件系统 + 衰减处理器

**目标：** 实现声望变化事件和衰减机制。

**给 CC 的指令：**

```
阅读 docs/faction-reputation-design.md 的：
- §13.2 NeoForge 事件
- §6.4 声望衰减机制
- §6.3 COLD 恢复路径（误伤宽容）

在 src/main/java/com/jgeted/sagadyssey/npc/faction/ 下创建：

1. StandingModifyEvent.java — 声望即将被修改（可取消）
   - extends Event, 通过 NeoForge EVENT_BUS 触发（非 mod bus）
   - 字段: Player player, Faction faction, int delta（可修改 setter）, String reason
   - 取消后声望不变，涟漪也不触发

2. StandingChangedEvent.java — 声望已被修改（不可取消）
   - 字段: Player player, Faction faction, int oldValue, int newValue, String reason
   - 在所有涟漪完成后、同步前触发

3. StandingLevelChangeEvent.java — 声望等级发生变化（不可取消）
   - 字段: Player player, Faction faction, StandingLevel oldLevel, StandingLevel newLevel
   - 在 StandingChangedEvent 之前逐个触发，含涟漪触发的等级变化

4. StandingDecayHandler.java — 声望衰减逻辑
   - 订阅 ServerTickEvent（或 LevelTickEvent）
   - 每个 tick 遍历所有在线玩家
   - 对每个玩家的 FactionStandings：
     * 检查每个阵营的 lastInteractionTick
     * 如果距离上次互动超过 20 游戏日（20 * 24000 ticks）
     * 每日漂移 1 点向 defaultStanding 方向
     * 对 defaultStanding≥0 的阵营不漂入 COLD
     * 对 defaultStanding≤0 的阵营（如 mystic=-40）继续漂移
   - 互动定义（6种）：交易、击杀NPC、完成任务、赎罪捐赠、解救NPC、32格内停留>30秒
   - 在 Sagadyssey.java 的 mod 构造器中注册到 NeoForge EVENT_BUS

5. 更新 StandingModifier.java：
   - 在流程第2步前触发 StandingModifyEvent，如果取消则 return
   - 在流程第7步对每个等级变化的阵营触发 StandingLevelChangeEvent
   - 在流程第8步触发 StandingChangedEvent

6. 实现误伤宽容机制（在 StandingModifier 或单独工具类）：
   - 维护 Map<UUID, Map<String, Long>>（玩家→阵营→最近一次攻击tick）
   - 首次非致命攻击（伤害<目标最大生命值20%）在5秒宽容窗口内仅扣1点
   - 致命一击无视宽容
   - 窗口内再次攻击同一目标宽容失效

创建完成后执行 ./gradlew build 验证编译通过。
```

**验证：** `./gradlew build` 编译通过。

---

## 第 8 步：NPC 集成 — 阵营归属 + 攻击判定 + 交易乘数

**目标：** 将阵营系统集成到现有 NPC 系统中。

**给 CC 的指令：**

```
阅读 docs/faction-reputation-design.md 的：
- §9.1 NPC 基础属性扩展
- §9.2 按声望等级的行为差异
- §9.3 招募门槛
- §7.1 按等级的效果总表
- §10.3 NPC 阵营归属
- §11.3 NPC 阵营分配 JSON

重要背景：当前项目中的 NpcBase.java 已有 faction 字段（类型为旧的 NpcFaction enum）。
需要将 NpcBase.java 中的 NpcFaction 引用切换为新的 Faction 系统。

具体改动：

1. 修改 NpcBase.java：
   - faction 字段类型从 NpcFaction 改为 Faction（来自 FactionRegistry）
   - 默认值从 NpcFaction.NEUTRAL 改为 FactionRegistry.get("wilderness")
   - 在 finalizeSpawn（或等价初始化逻辑）中按权重表分配阵营：
     * 优先检查实体 NBT 中的预设 FactionTag
     * 其次按 npc_faction_assignments.json 权重随机
     * 默认 fallback wilderness
   - NBT 持久化：faction id 存为字符串 "sagadyssey:wilderness"
   - 移除旧的 NpcFaction.HOSTILE 判定逻辑，改用新系统的 isHostile(player)
   - 更新 hurt() 方法中的阵营切换逻辑
   - 更新 mobInteract() 中的阵营检查

2. 在 NpcHostileGoal.java 中：
   - 更新 isHostile 判定：通过 FactionAttachments.getStandings(player).isHostile(npcFaction)
   - 如果玩家声望为 HATED → 该阵营 NPC 主动攻击玩家

3. 在 ProtectOwnerGoal.java 中：
   - 更新 isAlliedTo 判定：HONORED+ 的阵营 NPC 会保护玩家

4. 在 NpcTradeScreen.java 或交易相关代码中：
   - 根据玩家对该 NPC 阵营的声望等级应用价格乘数：
     * REVERED: 80%
     * HONORED: 90%
     * NEUTRAL: 100%
     * COLD: 120%
     * HATED: 不交易

5. 在 NpcRecruitScreen.java 或招募相关代码中：
   - 添加声望检查：只有 REVERED 才能招募该阵营 NPC
   - 否则显示提示消息

6. 创建 IFactionInteractable.java 接口：
   - 路径：src/main/java/com/jgeted/sagadyssey/npc/faction/api/IFactionInteractable.java
   - 方法：getFaction(), isElite(), getStandingValueOnKill()
   - 让 NpcBase 实现此接口

7. 创建数据包文件：
   - src/main/resources/data/sagadyssey/npc_faction_assignments.json
   - 按 §11.3 格式，列出每种 NPC 实体类型可分配的阵营及权重

创建完成后执行 ./gradlew build 验证编译通过。
```

**验证：** `./gradlew build` 编译通过。进游戏验证 NPC 能正常分配阵营。

---

## 第 9 步：声望获取途径 — 击杀 + 交易 + 专属

**目标：** 实现声望的具体获取和扣除途径。

**给 CC 的指令：**

```
阅读 docs/faction-reputation-design.md 的：
- §6.1 通用途径
- §6.2 阵营专属途径
- §6.3 COLD 恢复路径（赎罪捐赠、解救NPC、赎罪箱）

1. 创建 NpcFactionEvents.java（放到 npc/event/ 下）：
   - 订阅 LivingDeathEvent：当 NPC（NpcBase）被玩家杀死时
     * 获取 NPC 的阵营和等级（普通/精英/Boss）
     * 计算 delta: 普通=-10, 精英=-15, Boss=-20
     * 调用 StandingModifier.applyModification(player, faction, delta, "killed_npc")
     * 涟漪自动处理敌对阵营的正向声望
   - 订阅 NPC 交易完成事件（如果有自定义事件）
     * 每 10 emerald 等值利润 → +2（每日上限 +10）
     * v1 仅计算绿宝石差额：玩家卖物品得绿宝石计为全额利润
     * 物物交换暂不纳入

2. 在 StandingModifier 或新工具类中实现阵营专属途径检查：
   - 王国：交付通缉令 → +15（依赖任务系统，先预留接口）
   - 商会：完成大量交易 → +20（预留）
   - 教会：捐赠金锭/绿宝石 → +5（实现右键修士NPC触发）
   - 矮人：交付稀有矿石 → +15（预留）
   - 秘法学会：交付附魔书/药水 → +10（预留）
   - 荒野流民：使用床 → +5/夜（实现：监听 PlayerSleepInBedEvent）
   - 劫掠者：对文明阵营 HONORED → -15（实现：监听 StandingLevelChangeEvent）

3. 实现 COLD 恢复机制（在 StandingModifier 或独立工具类）：
   - 赎罪捐赠：通过盟友/中立 NPC 中介缴纳绿宝石
     * 每次 +5~+15，3 游戏日冷却
     * 需要右键 NPC 触发 GUI 或命令
   - 赎罪箱：在阵营营地中放置无人看守的赎罪箱
     * 右键投入绿宝石 → +5/次
     * 先在代码中实现交互逻辑，方块和模型后续再做
   - 解救 NPC：右键被拘禁的友好阵营 NPC → +15
     * 利用现有营地 NPC spawn 逻辑，在敌对营地中放置被拘禁 NPC
   - 被动恢复：NPC 被第三方攻击时玩家在 32 格旁观不参与
     * 每日触发一次 +1 漂移，累积不超过 +5

4. 实现涟漪每日上限重置：
   - 在 ServerTickEvent 中检测 level.getDayTime() % 24000 == 0
   - 遍历在线玩家，调用 FactionStandings.resetDailyRippleCounters()

创建完成后执行 ./gradlew build 验证编译通过。
```

**验证：** `./gradlew build` 编译通过。

---

## 第 10 步：命令 + API + 称号系统

**目标：** 实现管理命令、公开 API 和称号系统。

**给 CC 的指令：**

```
阅读 docs/faction-reputation-design.md 的：
- §13.1 公开 API
- §7.2 称号系统
- §13.3 接口扩展

1. 创建 SagadysseyFactionApi.java（路径：npc/faction/api/）
   - 接口方法（按 §13.1）：
     * getFaction(ResourceLocation) → Faction
     * getAllFactions() → Collection<Faction>
     * getStanding(Player, Faction) → int
     * getStandingLevel(Player, Faction) → StandingLevel
     * isHostileTo(Player, Faction) → boolean
     * modifyStanding(Player, Faction, int delta, String reason) → void
       - Javadoc 标注：必须在服务端主线程调用
       - 入口加入 Preconditions.checkState(!level.isClientSide)
     * setStanding(Player, Faction, int value, String reason) → void
     * getRelation(Faction, Faction) → InterFactionRelation
     * getHostileFactions(Player) → Set<Faction>
     * getAlliedFactions(Player) → Set<Faction>
   - 创建默认实现类 SagadysseyFactionApiImpl

2. 创建 FactionCommand.java（路径：npc/faction/command/）
   - /sagadyssey faction get <player> <faction> → 显示声望值和等级
   - /sagadyssey faction set <player> <faction> <value> → 设置声望值
   - /sagadyssey faction reset <player> → 重置所有声望到 defaultStanding
   - /sagadyssey faction list → 列出所有阵营及关系
   - 权限检查：需要 OP 权限（>= 2）
   - 在 Sagadyssey.java 中注册命令

3. 实现称号系统：
   - 在 FactionStandings 或新建 TitleManager 中维护当前激活称号
   - 当玩家某个阵营达到 REVERED 时，可选择激活该阵营的称号
   - 称号对应关系（按 §7.2）：
     * kingdom → "王国骑士" / "Knight of the Realm"
     * merchant_guild → "行会大师" / "Guild Master"
     * church → "受祝者" / "Blessed One"
     * dwarven → "矮人之友" / "Dwarf-Friend"
     * mystic → "秘法达人" / "Arcane Adept"
     * wilderness → "荒野行者" / "Wildwalker"
   - 称号显示在玩家名牌下方（通过 PlayerEvent.NameFormat 或 ChatComponent 渲染）
   - 同一时间只能激活一个称号，在声望图表 GUI 中切换

4. 在 Sagadyssey.java mod 构造器中注册：
   - FactionCommand
   - 事件监听器（称号渲染）

创建完成后执行 ./gradlew build 验证编译通过。
```

**验证：** `./gradlew build` 编译通过。进游戏测试 `/sagadyssey faction` 命令。

---

## 第 11 步：本地化 + 数据包补全 + 打磨

**目标：** 补全语言文件、校验数据完整性。

**给 CC 的指令：**

```
1. 更新本地化文件：

更新 src/main/resources/assets/sagadyssey/lang/zh_cn.json，添加：
- 阵营名称：faction.sagadyssey.kingdom → "王国卫队" 等（7个）
- 声望等级：standing.sagadyssey.revered → "崇拜" 等（5个）
- 关系类型：relation.sagadyssey.ally → "盟友" 等（5个）
- 称号：title.sagadyssey.kingdom → "王国骑士" 等（6个）
- 命令消息：command.sagadyssey.faction.* 系列
- GUI 文本：gui.sagadyssey.reputation.* 系列
- 变化原因：standing.reason.killed_npc → "击杀NPC" 等
- NPC 对话文本：faction.<id>.<level> 格式的对话数组

更新 src/main/resources/assets/sagadyssey/lang/en_us.json，添加对应英文。

2. 检查数据包 JSON 完整性：
   - 7 个阵营 JSON 的字段是否齐全
   - faction_relations.json 是否包含 §5.4 矩阵中的所有非NEUTRAL关系
   - npc_faction_assignments.json 格式是否正确

3. 检查代码中的关键路径：
   - FactionRegistry 在 /reload 后能否正确重新加载
   - FactionStandings 用 String key 确保 /reload 不丢数据
   - 涟漪传递 isRipple 标志是否正确阻止嵌套传播

4. 最终验证：
   - ./gradlew build 通过
   - ./gradlew runClient 进游戏测试基本流程
   - 检查日志无异常

创建完成后执行 ./gradlew build 做最终编译验证。
```

**验证：** `./gradlew build` 通过，`./gradlew runClient` 进游戏无崩溃。

---

## 参考：设计文档关键数值速查

| 等级 | 范围 | 星标 | 交易价格 | 招募 | 攻击 |
|------|------|------|---------|------|------|
| REVERED | 75~100 | ★★★★★ | 80% | ✓ | ✗ |
| HONORED | 25~74 | ★★★★☆ | 90% | ✗ | ✗ |
| NEUTRAL | -24~24 | ★★★☆☆ | 100% | ✗ | ✗ |
| COLD | -74~-25 | ★★☆☆☆ | 120% | ✗ | ✗ |
| HATED | -100~-75 | ★☆☆☆☆ | — | ✗ | NPC主动攻击 |

| 关系 | 传递率 | 连线色 |
|------|--------|--------|
| ALLY | +50% | 绿色 |
| FRIENDLY | +30% | 绿色 |
| NEUTRAL | 0% | 灰色 |
| DISTRUSTFUL | -20% | 橘色 |
| ENEMY | -50% | 红色 |

| NPC | 击杀声望变化 |
|-----|------------|
| 普通 | -10 |
| 精英 | -15 |
| Boss | -20 |
