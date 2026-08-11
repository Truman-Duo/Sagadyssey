# 玩家阵营系统 — CC 实施指令

> 以 `docs/player-faction-design.md` v1.1 为蓝本，按以下顺序逐一交给 CC 执行。
> 每步独立粘贴给 CC，等它完成并 `./gradlew build` 通过后再进入下一步。

---

## 第 1 步：数据层 — player.json + FactionRegistry 兜底 + FactionStandings 适配

**目标：** 创建 player 阵营定义、Registry 硬编码兜底、声望查询适配。

**给 CC 的指令：**

```
阅读 docs/player-faction-design.md 的以下章节：
- §3.1 JSON 定义
- §3.3 FactionRegistry 硬编码兜底
- §5.1 玩家对自己阵营的声望
- §8.3 FactionStandings.java

然后：

1. 创建 player.json 数据包文件：
   路径：src/main/resources/data/sagadyssey/sagadyssey/faction/player.json
   内容按 §3.1 的 JSON 示例，注意：
   - id 写 "sagadyssey:player"（完整 ID）
   - color 用十进制 65280（0x00FF00 绿色）
   - default_standing 写 100
   - can_be_hostile 和 can_recruit 都写 false
   - decay_enabled 写 false
   - decay_rate_per_day 写 0
   - gui_node_position 写 [200, 200]
   - icon 写 "sagadyssey:textures/gui/faction/player.png"（暂缺纹理文件没关系）

2. 修改 FactionRegistry.java，新增硬编码兜底：
   - 添加 private static final Faction PLAYER_FALLBACK 字段
     * 用 Faction 的 compact constructor 构造，所有字段值对齐 player.json
     * 路径参数用 ResourceLocation.fromNamespaceAndPath(SagadysseyMod.MOD_ID, "...")
   - 添加 public static Faction getPlayerFaction() 方法：
     * 先调 get("sagadyssey:player") 从 Registry 读
     * 如果 Registry 还没加载（cachedRegistry == null），返回 PLAYER_FALLBACK
     * 如果 Registry 有但查不到 player，日志 warn 一次（用 static boolean warned 防刷屏），返回 PLAYER_FALLBACK
     * 如果 Registry 有且查到了，返回 Registry 版本
   - 中文注释

3. 修改 FactionStandings.java：
   - getValue(String factionId)：开头加判断
     if ("sagadyssey:player".equals(factionId)) return 100;
   - getLevel(Faction faction)（以及 getLevel(String factionId) 如果有）：
     开头加判断 if ("sagadyssey:player".equals(faction.id())) return StandingLevel.REVERED;
   - canRecruitFrom(Faction faction)：开头加判断
     if ("sagadyssey:player".equals(faction.id())) return false;
   - isHostile(Faction faction)：开头加判断
     if ("sagadyssey:player".equals(faction.id())) return false;
   - 中文注释说明为什么 player 阵营走特殊路径

所有新增代码用中文注释。创建完成后执行 ./gradlew build 验证编译通过。
```

**验证：** `./gradlew build` 无编译错误。player.json 文件存在。

---

## 第 2 步：NpcBase — originalFaction 字段 + setOwner 逻辑 + NBT 持久化

**目标：** 扩展 NPC 实体以支持阵营切换和恢复。

**给 CC 的指令：**

```
阅读 docs/player-faction-design.md 的以下章节：
- §8.1 NpcBase.java
- §8.2 NBT 持久化

然后修改 src/main/java/com/jgeted/sagadyssey/npc/entity/NpcBase.java：

1. 新增字段（放在现有 faction 字段附近）：
   private String originalFaction;  // 招募前的阵营 ID，null 表示未招募或已解散

2. 新增方法：
   - public String getOriginalFaction() { return originalFaction; }
   - public void setOriginalFaction(String id) { this.originalFaction = id; }

3. 新增 dismiss() 方法：
   ```
   public void dismiss() {
       if (this.originalFaction != null) {
           Faction original = FactionRegistry.get(this.originalFaction);
           this.faction = original != null ? original : FactionRegistry.get("sagadyssey:wilderness");
           this.originalFaction = null;
       }
       this.ownerUUID = null;
   }
   ```
   注意：恢复原阵营用 FactionRegistry.get() 而非 getPlayerFaction()。
   如果原阵营已被删除（get返回null），fallback 到 wilderness。

4. 修改 setOwner(UUID playerUUID) 方法：
   现有逻辑：this.ownerUUID = playerUUID;
   新增逻辑（放在赋值后）：
   ```
   if (playerUUID != null) {
       // 保存原阵营（仅首次——防止覆盖已有的 originalFaction）
       if (this.originalFaction == null && this.faction != null
           && !"sagadyssey:player".equals(this.faction.id())) {
           this.originalFaction = this.faction.id();
       }
       // 切换到 player 阵营
       this.faction = FactionRegistry.getPlayerFaction();
   }
   // playerUUID == null 时不做阵营切换——阵营清除统一由 dismiss() 负责
   ```

5. 修改 NBT 保存逻辑（addAdditionalSaveData 或等价方法）：
   在现有 Faction 标签写入后添加：
   ```
   if (this.originalFaction != null) {
       tag.putString("OriginalFaction", this.originalFaction);
   }
   ```

6. 修改 NBT 加载逻辑（readAdditionalSaveData 或等价方法）：
   在现有 Faction 标签读取后添加：
   ```
   this.originalFaction = tag.contains("OriginalFaction")
       ? tag.getString("OriginalFaction") : null;
   ```
   注意：JSON 中存的是完整 ID 如 "sagadyssey:kingdom"，不是短名。

7. 所有新增代码用中文注释。

创建完成后执行 ./gradlew build 验证编译通过。
```

**验证：** `./gradlew build` 无编译错误。

---

## 第 3 步：招募/解散流程 + NpcStatsPayload 扩展

**目标：** 招募时切阵营，解散时恢复，网络同步 originalFaction。

**给 CC 的指令：**

```
阅读 docs/player-faction-design.md 的以下章节：
- §4.2 招募流程
- §4.3 解散流程
- §8.5 NpcStatsPayload 字段扩展

1. 修改 NpcInteractionPacket.java 中的 handleRecruit 方法：
   路径：src/main/java/com/jgeted/sagadyssey/npc/network/NpcInteractionPacket.java

   在 npc.setOwner(player.getUUID()) 这行之后，新增：
   ```
   // 切换到 player 阵营（setOwner 内部已处理 originalFaction 保存 + faction 切换）
   // 同步 NPC 状态
   NpcStatsPayload.sync(npc);
   ```
   注意：setOwner 已经在内部调了 FactionRegistry.getPlayerFaction() 切阵营，
   这里不需要再手动调 setFaction。但需要确认 setOwner 在 NpcStatsPayload 同步
   之前被调用。

2. 修改 NpcStatsPayload.java：
   路径：src/main/java/com/jgeted/sagadyssey/npc/network/NpcStatsPayload.java

   - 新增字段：private final String originalFaction;
   - 构造函数新增参数 String originalFaction
   - STREAM_CODEC 中新增 originalFaction 的读写（用 ByteBufCodecs.STRING_UTF8 或
     StreamCodec.of 手动写——参考 payload 中其他字符串字段的写法）
   - 客户端处理（handle 方法或等价位置）：缓存 originalFaction 供 GUI 渲染

3. 修改 NpcCommandScreen.java 或 NpcInteractionScreen.java：
   路径：src/main/java/com/jgeted/sagadyssey/npc/gui/

   为已招募 NPC（faction=player 且 ownerUUID 匹配当前玩家）添加"解散"按钮：
   - 按钮文字："解散" / "Dismiss"
   - 点击后发送 NpcInteractionPacket，action 字段用新值如 "dismiss"
   - 如果当前没有 dismiss action，新增一个枚举值

4. 在 NpcInteractionPacket.java 中添加 dismiss 处理：
   - 服务端收到 "dismiss" action 时：
     * 检查 npc.getOwnerUUID() 匹配发送者
     * 调用 npc.dismiss()
     * 同步 NpcStatsPayload
   - 中文注释

创建完成后执行 ./gradlew build 验证编译通过。
```

**验证：** `./gradlew build` 编译通过。`./gradlew runClient` 进游戏验证招募后 NPC 阵营切换、解散后恢复。

---

## 第 4 步：AI 行为适配 — 6 个方法全部更新

**目标：** 所有涉及阵营/声望判定的 AI 方法都适配 player 阵营。

**给 CC 的指令：**

```
阅读 docs/player-faction-design.md 的以下章节：
- §5.2 其他阵营 NPC 对玩家阵营 NPC 的敌对判定
- §8.4 AI 集成点（全部 6 个方法）
- §10.2 PvP 规则

注意：这一步是改动量最大的一步，请仔细阅读 §8.4 的每个子章节后再动手。

========================
4A：创建统一工具方法
========================

在 src/main/java/com/jgeted/sagadyssey/npc/faction/StandingModifier.java 中新增静态方法：

/**
 * 判定 source 实体是否应视 target 实体为敌。
 * 任一方属于 player 阵营时，以其 owner 的 FactionStandings 代替直接阵营判定。
 *
 * @param source          判定发起方实体
 * @param target          被判定实体
 * @param sourceOwnerUuid source 的 owner（如果 source 是已招募 NPC）
 * @param targetOwnerUuid target 的 owner（如果 target 是已招募 NPC）
 * @return true 如果 source 应该攻击 target
 */
public static boolean isHostileBetween(
    LivingEntity source, LivingEntity target,
    @Nullable UUID sourceOwnerUuid, @Nullable UUID targetOwnerUuid
)

实现四种情况的逻辑（按 §8.4.6 的代码）：
- 双方都是 player 阵营 → v1.0 返回 false（玩家间 NPC 不互相敌对）
- 仅 source 是 player 阵营 → 查 source.owner 对 target 阵营的声望
- 仅 target 是 player 阵营 → 查 source 阵营 canBeHostile() + target.owner 对 source 声望
- 双方都是普通阵营 → 查 FactionRelationMatrix

写详细的日志注释说明每个分支的判定逻辑。中文注释。

========================
4B：NpcBase.isAlliedTo(LivingEntity other)
========================

扩展 isAlliedTo()。当前逻辑（同 ownerUUID → 盟友）保留，但需要加上：
- 如果 other 是 Player 且其 UUID 等于 this.ownerUUID → 盟友
- 如果 this.faction 是 player 阵营且 other.faction 是 player 阵营且 ownerUUID 相同 → 盟友
- 其他情况走现有逻辑

注意：player 阵营 NPC 之间只需同 owner 就是盟友，不需要查声望。

========================
4C：NpcHostileGoal
========================

修改 NpcHostileGoal.java：
路径：src/main/java/com/jgeted/sagadyssey/npc/ai/NpcHostileGoal.java

在 findTarget() 或等价的候选目标筛选逻辑中：
- 对每个候选目标，调用 StandingModifier.isHostileBetween(this.npc, target, ...)
- 如果返回 true → 将该目标加入候选列表
- 替换原有的"只查 FactionRelationMatrix"逻辑

在 canContinueToUse() 中：
- 如果当前 target 仍满足 isHostileBetween() → 继续
- 否则停止追击

========================
4D：ProtectOwnerGoal.isThreat()
========================

修改 ProtectOwnerGoal.java 中的 isThreat(LivingEntity potentialThreat) 方法：
路径：src/main/java/com/jgeted/sagadyssey/npc/ai/ProtectOwnerGoal.java

- 如果 potentialThreat 是玩家阵营 NPC：
  * 不同 owner → v1.0 不视为威胁（返回 false）
  * 同 owner → 不视为威胁（自己人）
- 如果 potentialThreat 是普通阵营 NPC：
  * 调用 StandingModifier.isHostileBetween()
- 如果 potentialThreat 是原版敌对生物（zombie 等）：
  * 保留现有逻辑（始终是威胁）

========================
4E：NpcBase.isHostileTo(LivingEntity target)
========================

如果 NpcBase 有 isHostileTo 方法（可能在 hurt() 或其它位置），改为调用
StandingModifier.isHostileBetween()，传入 this 和 target 的 faction + ownerUUID。

========================
4F：NpcFactionEvents — 击杀 player 阵营 NPC 的声望处理
========================

修改 src/main/java/com/jgeted/sagadyssey/npc/event/NpcFactionEvents.java

在 LivingDeathEvent 处理中，如果 killed NPC 的 faction 是 player：
- 获取 killedNpc.originalFaction（如果有）
- 按 originalFaction 的声望规则处理（视为击杀该阵营 NPC）
- originalFaction 为 null 时 fallback 到 wilderness
- 涟漪照常传播

添加日志：玩家 X 击杀了玩家 Y 的 NPC（原阵营：Z）

========================
全部修改完成后执行 ./gradlew build 验证编译通过。
```

**验证：** `./gradlew build` 编译通过。`./gradlew runClient` 验证：
- 招募 NPC → 敌对阵营 NPC 攻击已招募 NPC（而非忽略）
- 已招募 NPC 保护主人（ProtectOwnerGoal 正常触发）
- 不同玩家的 NPC 相遇不互打

---

## 第 5 步：交易解耦 — NpcTradeRegistry ignoreReputation 参数

**目标：** player 阵营 NPC 与主人交易时跳过所有声望门控。

**给 CC 的指令：**

```
阅读 docs/player-faction-design.md 的以下章节：
- §7.1 已招募 NPC 的阵营相关行为（交易行）

1. 修改 NpcTradeRegistry.java（或等价的交易 offer 生成类）：
   路径：src/main/java/com/jgeted/sagadyssey/npc/trade/NpcTradeRegistry.java

   - 找到生成 trade offer 的方法（可能是 getOffers(Faction, StandingLevel) 或类似签名）
   - 新增重载方法或参数：
     getOffers(Faction faction, StandingLevel level, boolean ignoreReputation)
   - 当 ignoreReputation == true 时：
     * 返回该阵营的所有可用交易项（不受等级限制）
     * 价格不乘任何系数（固定 100%）
   - 当 ignoreReputation == false 时：走现有逻辑

2. 修改 NPC 交易 GUI 打开逻辑（可能在 NpcInteractionScreen 或 NpcTradeScreen）：
   - 如果 npc.faction.id() == "sagadyssey:player" 且 npc.ownerUUID == 当前玩家 UUID：
     * 使用 ignoreReputation=true 生成 offer
     * 或者从 npc.originalFaction 查交易表
   - 否则走现有逻辑

3. 中文注释说明为什么 player 阵营 NPC 要跳过声望。

创建完成后执行 ./gradlew build 验证编译通过。
```

**验证：** `./gradlew build` 编译通过。进游戏验证：已招募 NPC 的交易 GUI 正常打开，所有物品可交易，价格 100%。

---

## 第 6 步：存档迁移 + GUI 适配 + 本地化

**目标：** 旧存档静默迁移、声望图表显示 "(已招募)"、本地化补全。

**给 CC 的指令：**

```
阅读 docs/player-faction-design.md 的以下章节：
- §9 已有存档迁移
- §7.1 GUI 显示 "(已招募)"
- §3.1 player 阵营定义

1. 在 NpcBase.java 的 readAdditionalSaveData() 末尾（NBT 加载完成后）添加迁移逻辑：
   ```
   // 静默迁移：旧存档中有 ownerUUID 但 faction 不是 player
   if (this.ownerUUID != null && !"sagadyssey:player".equals(this.faction.id())) {
       SagadysseyMod.LOGGER.info("迁移已招募 NPC: {} 从 {} 转到 player 阵营 (owner={})",
           this.getDisplayName().getString(), this.faction.id(), this.ownerUUID);
       this.originalFaction = this.faction.id();
       this.faction = FactionRegistry.getPlayerFaction();
   }
   ```
   注意：这个逻辑要放在 readAdditionalSaveData 的最后，确保 faction 和 ownerUUID 都已经读取完毕。

2. 修改声望图表 GUI：
   路径：src/main/java/com/jgeted/sagadyssey/npc/faction/gui/ReputationChartScreen.java
   或相关的 NPC 信息渲染位置

   当渲染已招募 NPC 的信息时：
   - 如果 npc.faction 是 player 且 npc.originalFaction 不为 null
   - 显示原阵营名称 + " (已招募)" / " (Recruited)"
   - 颜色用绿色（区别于未招募 NPC）

3. 更新本地化文件：
   src/main/resources/assets/sagadyssey/lang/zh_cn.json
   src/main/resources/assets/sagadyssey/lang/en_us.json

   新增 key：
   - "faction.sagadyssey.player": "玩家阵营" / "Player Faction"
   - "gui.sagadyssey.recruited": " (已招募)" / " (Recruited)"
   - "gui.sagadyssey.dismiss": "解散" / "Dismiss"

4. 中文注释。

创建完成后执行 ./gradlew build 验证编译通过。
```

**验证：** `./gradlew build` 编译通过。`./gradlew runClient` 验证：
- 旧存档 NPC 自动迁移（日志中有迁移信息）
- 声望图表中已招募 NPC 显示 "(已招募)"
- 语言文件正确显示

---

## 第 7 步：测试 + 边界情况自查

**目标：** 编译通过后逐项验证核心流程。

**给 CC 的指令：**

```
阅读 docs/player-faction-design.md 的以下章节：
- §4 NPC 阵营生命周期
- §11 边界情况
- §13.3 性能影响

执行 ./gradlew build 确保编译通过，然后逐项自查：

1. 搜索所有调用 FactionRegistry.get("sagadyssey:player") 的地方，
   确认都已改为 FactionRegistry.getPlayerFaction()（除 player.json 加载路径本身）。

2. 搜索所有 setOwner( 调用点：
   - setOwner(uuid) 在哪些地方被调？是否都在有意义的上下文中？
   - setOwner(null) 在哪些地方被调？是否都在 dismiss() 之后？
   列出所有调用点和文件行号。

3. 确认 NpcStatsPayload 的 originalFaction 字段在所有更新路径都同步：
   - setOwner() → 需要同步
   - dismiss() → 需要同步
   - NBT 加载迁移 → 需要同步（或确认首次 tick 时自动同步）
   在代码中加注释标注同步点。

4. 确认 StandingModifier.isHostileBetween() 被以下方法调用：
   - NpcHostileGoal 目标选择
   - ProtectOwnerGoal.isThreat()
   - NpcBase.isAlliedTo()（如果适用）
   列出所有调用点和文件行号。

5. 边界情况行走查：
   - 如果 player.json 被删除 → getPlayerFaction() 返回兜底 ✓（第1步已实现）
   - 如果原阵营 JSON 被删除 → dismiss() fallback 到 wilderness ✓（第2步已实现）
   - 多次招募/解散 → originalFaction 正确恢复 ✓（第3步已实现）

输出自查报告，每项标注 ✓ / ✗，✗ 的说明原因。
```

**验证：** 自查报告全部 ✓。`./gradlew build` 通过。

---

## 参考：关键文件改动速查

| 文件 | 改动类型 | 对应步骤 |
|------|---------|---------|
| `data/.../faction/player.json` | 新增 | 第 1 步 |
| `FactionRegistry.java` | 修改：新增 getPlayerFaction() + PLAYER_FALLBACK | 第 1 步 |
| `FactionStandings.java` | 修改：player 阵营特殊处理（4 处） | 第 1 步 |
| `NpcBase.java` | 修改：originalFaction 字段、setOwner、dismiss、NBT 读写、迁移 | 第 2 步 + 第 6 步 |
| `NpcInteractionPacket.java` | 修改：招募流程 + dismiss handler | 第 3 步 |
| `NpcStatsPayload.java` | 修改：新增 originalFaction 字段 | 第 3 步 |
| `NpcCommandScreen.java` | 修改：新增"解散"按钮 | 第 3 步 |
| `StandingModifier.java` | 修改：新增 isHostileBetween() | 第 4 步 |
| `NpcHostileGoal.java` | 修改：目标选择改用 isHostileBetween() | 第 4 步 |
| `ProtectOwnerGoal.java` | 修改：isThreat 改用 isHostileBetween() | 第 4 步 |
| `NpcFactionEvents.java` | 修改：击杀 player 阵营 NPC 的声望处理 | 第 4 步 |
| `NpcTradeRegistry.java` | 修改：新增 ignoreReputation 参数 | 第 5 步 |
| `ReputationChartScreen.java` | 修改：已招募 NPC 显示 "(已招募)" | 第 6 步 |
| `zh_cn.json` / `en_us.json` | 修改：新增 3 个 key | 第 6 步 |

---

## 参考：player 阵营判定速查

```
判断一个 Faction 是否是 player 阵营：
  "sagadyssey:player".equals(faction.id())

判断一个 NPC 是否是已招募状态：
  npc.getOwnerUUID() != null

获取 player 阵营对象（安全）：
  FactionRegistry.getPlayerFaction()   // 永远不为 null

两个 player 阵营 NPC 是否同主人：
  npcA.getOwnerUUID() != null && npcA.getOwnerUUID().equals(npcB.getOwnerUUID())
```
