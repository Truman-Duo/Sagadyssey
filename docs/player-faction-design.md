# Sagadyssey 玩家阵营设计文档

> **版本：** v1.1  
> **日期：** 2026-08-01  
> **适用模块：** NPC / Core（阵营系统）  
> **目标 MC 版本：** 1.21.1 NeoForge  
> **依赖文档：** [阵营声望系统设计文档 v1.6](./faction-reputation-design.md)  
> **状态：** 设计阶段，CC 反馈已修正（5 个问题 + 2 个建议）

---

## 目录

1. [动机与问题陈述](#1-动机与问题陈述)
2. [设计概览](#2-设计概览)
3. [阵营定义：sagadyssey:player](#3-阵营定义sagadysseyplayer)
4. [NPC 阵营生命周期](#4-npc-阵营生命周期)
5. [玩家阵营与声望系统](#5-玩家阵营与声望系统)
6. [玩家阵营与阵营间关系](#6-玩家阵营与阵营间关系)
7. [NPC 行为变化](#7-npc-行为变化)
8. [数据模型变更](#8-数据模型变更)
9. [已有存档迁移](#9-已有存档迁移)
10. [多人游戏考量](#10-多人游戏考量)
11. [边界情况](#11-边界情况)
12. [实施阶段](#12-实施阶段)
13. [风险与顾虑](#13-风险与顾虑)

---

## 1. 动机与问题陈述

### 1.1 当前矛盾

```
玩家花费绿宝石 → 招募 NPC → NPC 仍然属于原阵营
                                    ↓
              原阵营的声望规则对"自己人"也生效
                                    ↓
              玩家对自己招募的 NPC 所属阵营声望不足 →
              NPC 不能交易 / 不能保护你 / 理论上应该攻击你
```

招募行为的语义是"这个 NPC 现在为你工作"。但代码层面 `setOwner()` 只设置了 `ownerUUID`，NPC 的 `faction` 字段纹丝不动。这导致三个具体问题：

1. **声望门控矛盾**：你花了 20 绿宝石招募了一个王国骑士，但如果你的王国声望降到 COLD 以下，你自己的 NPC 会对你的交易请求加价 120%——他已经在为你工作了，为什么还在乎你和他前雇主的声望？

2. **ProtectOwnerGoal 绕过声望**：当前代码中 `isAlliedTo()` 把同主人 NPC 视为盟友，绕过了声望检查。这解决了"不保护你"的紧急问题，但它是一个特例补丁——正确的设计应该是"已招募 NPC 本来就属于你，不存在声望问题"。

3. **阵营归属混乱**：打开声望图表（R 键），一个已招募的 NPC 显示为"王国卫队"阵营——视觉上它仍然是王国的兵，但行为上是你的兵。玩家困惑。

### 1.2 设计目标

- **语义正确**：招募 = 转入玩家阵营，NPC 的身份从"某阵营成员"变为"某玩家的下属"
- **最小改动**：不重建阵营系统，在现有架构上扩展
- **可逆**：支持解散 NPC，让其回到原阵营
- **多人安全**：每个玩家的 NPC 互不干扰

---

## 2. 设计概览

### 2.1 核心模型

```
招募前：
  NPC.faction = "sagadyssey:kingdom"
  NPC.ownerUUID = null

招募后：
  NPC.faction = "sagadyssey:player"
  NPC.ownerUUID = <玩家UUID>
  NPC.originalFaction = "sagadyssey:kingdom"  ← 新增字段，用于解散时恢复
```

### 2.2 "每玩家独立实例"的实现策略

用户选择了"每玩家独立实例"——每个玩家的 player 阵营在逻辑上是独立的。但技术实现上不走"每个 UUID 注册一个 Faction 对象"的路线（Registry 不支持动态条目）。取而代之的是：

> **单 Registry 条目 + ownerUUID 参数化**

- `sagadyssey:player` 作为**一个** Faction 注册在 Registry 中
- 该阵营的行为**由 ownerUUID 参数化**——查询声望、判定敌友时，根据 NPC 的 ownerUUID 找到对应玩家的声望数据
- 从外部视角看，玩家 A 的 player 阵营 NPC 和玩家 B 的 player 阵营 NPC 表现不同，因为查询的声望数据不同

```
逻辑视角（玩家看到的）：
  sagadyssey:player:<uuid-A>  ← 玩家A的阵营
  sagadyssey:player:<uuid-B>  ← 玩家B的阵营
  两者行为独立，各自对应所属玩家的声望关系

技术视角（代码实现的）：
  sagadyssey:player           ← 一个 Registry 条目
  + ownerUUID 参数化查询      ← 行为差异由查询时传入的 UUID 决定
```

---

## 3. 阵营定义：sagadyssey:player

### 3.1 JSON 定义

`data/sagadyssey/sagadyssey/faction/player.json`（新增）：

```json
{
  "id": "sagadyssey:player",
  "display_name": "faction.sagadyssey.player",
  "color": 65280,
  "_comment_color": "0x00FF00 = 65280，绿色，区别于其他7个阵营",
  "default_standing": 100,
  "_comment_default_standing": "玩家对自己阵营永远是 REVERED(100)。此值也作为 ownerUUID 匹配时的默认返回值",
  "can_be_hostile": false,
  "can_recruit": false,
  "_comment_can_recruit": "玩家阵营不可被招募（这是玩家自己的阵营）",
  "icon": "sagadyssey:textures/gui/faction/player.png",
  "gui_node_position": [200, 200],
  "reputation_multiplier": 1.0,
  "decay_enabled": false,
  "_comment_decay": "玩家阵营声望不衰减——你永远忠于自己",
  "decay_target": 100,
  "decay_rate_per_day": 0
}
```

### 3.2 与其他阵营的区别

| 属性 | 普通阵营 | sagadyssey:player |
|------|---------|-------------------|
| canBeHostile | 可能为 true | 永远 false |
| canRecruit | 可能为 true | 永远 false |
| decayEnabled | 可能为 true | 永远 false |
| defaultStanding | 0 或 -100 等 | 100（REVERED） |
| 声望查询 | player → faction | player → 自己的 player faction = 固定100 |
| 涟漪参与 | 参与 | 不参与 |

### 3.3 FactionRegistry 硬编码兜底

`player.json` 是 datapack 资源，整合包可能误删。如果 JSON 丢失后 `FactionRegistry.get("sagadyssey:player")` 返回 null，所有已招募 NPC 会在下次加载时静默变成 wilderness——不可接受。

**解决方案：** `FactionRegistry` 提供一个硬编码兜底方法，确保即使 JSON 被删，player 阵营对象也永远不为 null：

```java
// FactionRegistry.java 新增

private static final Faction PLAYER_FALLBACK = new Faction(
    "sagadyssey:player",
    "faction.sagadyssey.player",
    0x00FF00,        // 绿色
    100,             // defaultStanding = REVERED
    false,           // canBeHostile
    false,           // canRecruit
    ResourceLocation.fromNamespaceAndPath(SagadysseyMod.MOD_ID, "textures/gui/faction/player.png"),
    Optional.empty(), // bannerPattern
    Optional.of(new int[]{200, 200}), // guiNodePosition
    1.0f,            // reputationMultiplier
    false,           // decayEnabled
    100,             // decayTarget
    0                // decayRatePerDay
);

/**
 * 获取 player 阵营。优先从 Registry 读 JSON 定义，
 * 如果 JSON 丢失则返回硬编码兜底。
 */
public static Faction getPlayerFaction() {
    Faction fromRegistry = get("sagadyssey:player");
    return fromRegistry != null ? fromRegistry : PLAYER_FALLBACK;
}
```

所有需要获取 player 阵营的地方使用 `FactionRegistry.getPlayerFaction()` 而非 `FactionRegistry.get("sagadyssey:player")`。日志在兜底触发时 warn 一次。

---

## 4. NPC 阵营生命周期

### 4.1 状态机

```
                    ┌──────────────┐
                    │  未招募 NPC   │
                    │ faction = X   │
                    │ ownerUUID =   │
                    │   null        │
                    │ origFaction = │
                    │   null        │
                    └──────┬───────┘
                           │ 玩家右键 → 招募
                           ▼
                    ┌──────────────┐
                    │  已招募 NPC   │
                    │ faction =    │
                    │   player     │
                    │ ownerUUID =  │
                    │   <玩家>      │
                    │ origFaction =│
                    │   X          │
                    └──────┬───────┘
                           │ 玩家右键 → 解散
                           ▼
                    ┌──────────────┐
                    │  已解散 NPC   │
                    │ faction = X  │
                    │   (恢复)      │
                    │ ownerUUID =  │
                    │   null       │
                    │ origFaction =│
                    │   null       │
                    └──────────────┘
```

### 4.2 招募流程

```
NpcInteractionPacket.handleRecruit():
  现有逻辑：
    1. 检查玩家背包有足够绿宝石
    2. 扣除绿宝石
    3. npc.setOwner(player.getUUID())     ← 已有
  
  新增逻辑：
    4. npc.setOriginalFaction(npc.getFaction().id())  ← 保存原阵营
    5. npc.setFaction(FactionRegistry.get("sagadyssey:player"))  ← 切阵营
    6. 同步 NpcStatsPayload 到所有附近玩家
```

**声望影响：无。** 招募不改变玩家对原阵营的声望——这是挖角行为，但中世纪世界观中个人跳槽不影响外交关系。

### 4.3 解散流程

```
解散（右键 NPC → 命令 → 解散）：
  1. 检查 NPC.originalFaction 不为 null
  2. npc.setFaction(FactionRegistry.get(originalFaction))
  3. npc.setOwner(null)
  4. npc.setOriginalFaction(null)
  5. NPC 停止跟随，回到原阵营行为模式
  6. 同步到客户端
```

**注意：** 解散不同于解雇——NPC 回到原阵营后仍留在原地（或走回生成点），不会消失。玩家如果想重新招募同一个 NPC，需要再次满足原阵营的招募条件（REVERED + 绿宝石）。

### 4.4 NPC 死亡

已招募 NPC 死亡后：
- `originalFaction` 随实体消亡
- 如果将来通过某种机制复活（如 Structure 模块的 NPC 重生），新实体按正常权重表分配阵营，不会自动回到 player 阵营

---

## 5. 玩家阵营与声望系统

### 5.1 玩家对自己阵营的声望

```java
// FactionStandings.getLevel(Faction faction)
// 新增特殊处理：

if ("sagadyssey:player".equals(faction.id())) {
    // 玩家对自己的阵营永远是 REVERED
    // 不需要在 standings map 中存储
    return StandingLevel.REVERED;
}
```

同理，`getValue("sagadyssey:player")` 固定返回 100。

### 5.2 其他阵营 NPC 对玩家阵营 NPC 的敌对判定

当 NPC（属于 bandit）判定是否攻击一个属于 player 阵营的 NPC 时：

```
bandit NPC 判定：目标 NPC 的 faction 是 "sagadyssey:player"
  → player 阵营不在 FactionRelationMatrix 中，无法直接查 bandit→player 的关系
  → 改为两段判定：
     1. bandit 阵营是否 canBeHostile()？ → 是
     2. 目标 NPC 的 owner（玩家）对 bandit 的声望是否为 HATED？ → 查 FactionStandings
  → 两者都满足 → bandit NPC 视该 player 阵营 NPC 为敌，主动攻击
```

**注意：** 这里的判定走的是"阵营本身的攻击性 + 玩家声望"路径，而不是从 FactionRelationMatrix 反向推断。FactionRelationMatrix 支持非对称关系（A 对 B ENEMY 但 B 对 A NEUTRAL），但这与 player 阵营无关——player 阵营不在矩阵中，查询 `getRelation(bandit, player)` 只会得到默认值 NEUTRAL，不能反映实际的敌对意愿。

**正确做法：** 当 source 是一个普通阵营的 NPC、target 是 player 阵营的 NPC 时，不查关系矩阵，而是查：
- source 阵营的 `canBeHostile()` 是否为 true
- target 的 owner 玩家对 source 阵营的 FactionStandings 是否为 HATED

这比关系矩阵更精确——它反映的是"这个玩家是不是这个阵营的敌人"，而非"这个阵营对所有玩家阵营的静态态度"。两个不同玩家对 bandit 可以有完全不同的声望，因此 bandit NPC 对玩家 A 的 NPC 和玩家 B 的 NPC 可以有不同的攻击行为。

### 5.3 声望查询矩阵

| 查询方向 | 现有机制 | 变化 |
|---------|---------|------|
| 玩家 → 普通阵营 | FactionStandings.get(factionId) | 无变化 |
| 玩家 → player 阵营 | 固定返回 REVERED/100 | 新增 |
| 普通阵营 NPC → player 阵营 NPC | 查 owner 的 FactionStandings | 无新增数据，复用现有 |
| player 阵营 NPC → 普通阵营 NPC | 查 owner 的 FactionStandings | 无新增数据，复用现有 |
| player 阵营 NPC(A) → player 阵营 NPC(B) | 比较 ownerUUID 是否相同 | 新增 |

---

## 6. 玩家阵营与阵营间关系

### 6.1 在关系矩阵中的位置

玩家阵营**不参与**阵营间关系矩阵。具体来说：

- `sagadyssey:player` 不出现在 `faction_relations.json` 中
- 涟漪传播时，source 或 target 为 `sagadyssey:player` 的条目被跳过
- 玩家阵营不产生涟漪，也不接受涟漪

**理由：** 关系矩阵描述的是"世界中的势力格局"——王国和教会是盟友，劫掠者是公敌。但 player 阵营不是一个"势力"，它是个人的延伸。个人的声望变化不应该通过涟漪传播影响其他阵营对待他的方式（这已经通过直接的声望系统处理了）。

### 6.2 声望 GUI 中的显示

在声望图表（R 键）中：

- `sagadyssey:player` **不显示**为一张阵营卡片
- 关系网络图中也不出现 player 节点
- 原因：玩家不需要"查看自己对自己的声望"

---

## 7. NPC 行为变化

### 7.1 已招募 NPC 的阵营相关行为

当 `npc.faction.id() == "sagadyssey:player"` 时：

| 行为 | 当前 | 变更后 |
|------|------|--------|
| 被其他 NPC 攻击判定 | 通过 `isAlliedTo()` 检查 owner | 改为：查 owner 对攻击者阵营的声望 + 攻击者阵营 canBeHostile()。见 §5.2 |
| 攻击其他 NPC | 通过 ProtectOwnerGoal 检查 | 改为：owner 对目标 NPC 的阵营是 HATED → 允许攻击 |
| 与主人交易 | 使用原阵营交易表 + 声望门控 | **完全解耦**：跳过所有声望门控（门控 = 等级限制 + 价格修正）。原阵营所有可交易物品对主人全部开放，价格固定 100%（无折扣、无加价）。实现：NpcTradeRegistry 生成 offer 时新增 ignoreReputation 参数，player 阵营 NPC 调此路径 |
| 与其他玩家交易 | 不存在此场景 | 其他人右键已招募 NPC 不打开交易 GUI（NPC 有主人，只与主人交互） |
| 招募 | 不可招募（已有主人） | 不变 |
| 声望图表中显示 | 显示为原阵营 | 显示为 `originalFaction` 阵营但标注 "(已招募)" |

### 7.2 NPC 外观

已招募 NPC 的外观不变——它保留了原阵营的纹理和模型。这保留了视觉多样性：玩家可以收集来自不同阵营的 NPC，每个看起来都不一样。

（远期可选：在已招募 NPC 的名牌旁添加一个小型绿色标记，表示"已招募"状态。）

---

## 8. 数据模型变更

### 8.1 NpcBase.java

```java
// 新增字段
private String originalFaction;  // 招募前的阵营 ID，解散时恢复

// setOwner() 变更
public void setOwner(UUID playerUUID) {
    this.ownerUUID = playerUUID;
    if (playerUUID != null) {
        // 保存原阵营（仅首次——避免覆盖已有的 originalFaction）
        if (this.originalFaction == null && this.faction != null
            && !"sagadyssey:player".equals(this.faction.id())) {
            this.originalFaction = this.faction.id();
        }
        // 切换到 player 阵营
        this.faction = FactionRegistry.getPlayerFaction();
    }
    // 注意：playerUUID == null 时不做任何阵营切换
    // 阵营清除统一由 dismiss() 负责，setOwner(null) 仅清 ownerUUID
    // 当前调用 setOwner(null) 的路径：
    //   - NpcBase.dismiss() → 已在 dismiss() 中先恢复原阵营再调 setOwner(null) ✓
    //   - 没有其他直接调 setOwner(null) 的路径
}

// 新增方法
public void dismiss() {
    if (this.originalFaction != null) {
        Faction original = FactionRegistry.get(this.originalFaction);
        this.faction = original != null ? original : FactionRegistry.get("sagadyssey:wilderness");
        this.originalFaction = null;
    }
    this.ownerUUID = null;
}

public String getOriginalFaction() { return originalFaction; }
public void setOriginalFaction(String id) { this.originalFaction = id; }
```

### 8.2 NBT 持久化

```java
// 保存
tag.putString("Faction", this.faction != null ? this.faction.id() : "sagadyssey:wilderness");
if (this.originalFaction != null) {
    tag.putString("OriginalFaction", this.originalFaction);
}
tag.putUUID("OwnerUUID", this.ownerUUID);  // 已有

// 加载
this.originalFaction = tag.contains("OriginalFaction") ? tag.getString("OriginalFaction") : null;
// 如果已有 ownerUUID 但 faction 不是 player → 触发静默迁移（见 §9）
```

### 8.3 FactionStandings.java

```java
// getValue() 新增特殊处理
public int getValue(String factionId) {
    if ("sagadyssey:player".equals(factionId)) {
        return 100;  // 固定值
    }
    return standings.getOrDefault(factionId, /* default from faction */);
}

// getLevel() 新增特殊处理
public StandingLevel getLevel(Faction faction) {
    if ("sagadyssey:player".equals(faction.id())) {
        return StandingLevel.REVERED;
    }
    // ... 现有逻辑
}
```

### 8.4 AI 集成点：所有需要适配的方法

以下 6 个方法当前通过阵营/声望判定行为，引入 player 阵营后需要全部适配。核心原则：**当涉及的任一实体属于 player 阵营时，以其 owner 玩家的 FactionStandings 代替直接阵营判定。**

#### 8.4.1 NpcBase.isAlliedTo(LivingEntity other)

当前逻辑（faction 无关，只检查 ownerUUID 相同）已经可以处理 player 阵营的基本情况。需扩展：

```java
// 场景：判断"我"是否与 other 结盟
if (this.faction.id().equals("sagadyssey:player") && this.ownerUUID != null) {
    // "我"是已招募 NPC：我与主人结盟，与主人的其他 NPC 结盟
    if (other instanceof Player player && player.getUUID().equals(this.ownerUUID)) return true;
    if (other instanceof NpcBase otherNpc && this.ownerUUID.equals(otherNpc.getOwnerUUID())) return true;
    return false;
}
// other 是 player 阵营的情况同理（对称处理）
// 否则走现有逻辑
```

#### 8.4.2 NpcHostileGoal：目标选择 + canContinueToUse()

```
canUse() / canContinueToUse():
  扫描范围内实体 → 对每个候选目标：
    1. 如果目标是 player 阵营 NPC：
       - 检查本 NPC 的阵营 canBeHostile()
       - 查目标 owner 对本阵营的声望 → HATED 则目标有效
    2. 如果目标是普通阵营 NPC：
       - 查 FactionRelationMatrix.getRelation(myFaction, targetFaction) → ENEMY
    3. 如果目标是玩家：
       - 现有逻辑：查玩家对 NPC 阵营的声望 → HATED
```

#### 8.4.3 ProtectOwnerGoal.isThreat(LivingEntity potentialThreat)

```
判断 potentialThreat 是否是主人的威胁：
  1. 如果 potentialThreat 是玩家阵营 NPC：
     - 查 threat.owner 对主人阵营（即 player 阵营）的态度
     - 实际上查 threat.owner 的 FactionStandings 中是否有任何 HATED 阵营
     - 更简单：不同 owner → 如果 PvP 规则允许 → 是威胁；否则否
     - **v1.0 规则：不同 owner 的 player 阵营 NPC 互不视为威胁（中立）**
  2. 如果 potentialThreat 是普通阵营 NPC：
     - 查 NPC 的阵营 canBeHostile()
     - 查主人对该阵营的声望 → HATED 则为威胁
  3. 如果 potentialThreat 是敌对生物（zombie 等）：
     - 始终是威胁（现有逻辑不变）
```

#### 8.4.4 NpcFactionEvents（击杀事件中的声望修改）

```
LivingDeathEvent → 玩家杀死了某 NPC：
  当前逻辑：
    killedNpc.getFaction() → 查玩家对此阵营的声望 → 扣声望 → 涟漪

  新增判定：
    如果 killedNpc.faction == player：
      - killedNpc 是某玩家的 NPC
      - 击杀者（player）扣声望的方式取决于 killedNpc.originalFaction：
        * 如果 originalFaction 存在 → 视为击杀该阵营 NPC
        * 如果 originalFaction 为 null → 视为击杀 wilderness NPC（兜底）
      - 涟漪照常传播
```

#### 8.4.5 NpcBase.isHostileTo(LivingEntity target)

```
判断本 NPC 是否应该敌对 target：
  1. 如果本 NPC 是 player 阵营：
     - 查 owner 对 target 的阵营的声望 → HATED → 敌对
     - 如果 target 也是 player 阵营：不同 owner → v1.0 不敌对
  2. 如果 target 是 player 阵营：
     - 查本 NPC 的阵营 canBeHostile()
     - 查 target.owner 对本 NPC 阵营的声望 → HATED → 敌对
  3. 否则：查 FactionRelationMatrix 现有逻辑
```

#### 8.4.6 统一工具方法

将上述重复逻辑提取到 `StandingModifier` 的静态方法中：

```java
/**
 * 两个实体之间的有效敌对关系判定。
 * 任一方属于 player 阵营时，以其 owner 的 FactionStandings 代替直接阵营判定。
 *
 * @return true 如果 source 应该视 target 为敌
 */
public static boolean isHostileBetween(
    LivingEntity source, LivingEntity target,
    @Nullable UUID sourceOwnerUuid, @Nullable UUID targetOwnerUuid
) {
    Faction sourceFaction = getFaction(source);
    Faction targetFaction = getFaction(target);

    boolean sourceIsPlayer = "sagadyssey:player".equals(sourceFaction.id());
    boolean targetIsPlayer = "sagadyssey:player".equals(targetFaction.id());

    // 情况 1：双方都是 player 阵营 → 同 owner 为友，不同 owner 中立（v1.0）
    if (sourceIsPlayer && targetIsPlayer) {
        return false; // v1.0: 玩家间 NPC 不互相敌对
    }

    // 情况 2：仅 source 是 player 阵营 → 查 owner 对 target 阵营的声望
    if (sourceIsPlayer) {
        ServerPlayer owner = getOnlinePlayer(sourceOwnerUuid);
        if (owner == null) return false;
        return FactionAttachments.getStandings(owner).isHostile(targetFaction);
    }

    // 情况 3：仅 target 是 player 阵营 → 查 source 阵营 canBeHostile + target.owner 对 source 声望
    if (targetIsPlayer) {
        if (!sourceFaction.canBeHostile()) return false;
        ServerPlayer targetOwner = getOnlinePlayer(targetOwnerUuid);
        if (targetOwner == null) return false;
        return FactionAttachments.getStandings(targetOwner).isHostile(sourceFaction);
    }

    // 情况 4：双方都是普通阵营 → 查 FactionRelationMatrix
    return FactionRelationMatrix.getInstance().getRelation(sourceFaction, targetFaction)
        == InterFactionRelation.ENEMY;
}
```

### 8.5 NpcStatsPayload 字段扩展

```java
// NpcStatsPayload.java（服务端→客户端，NPC 状态同步）
// 新增字段：
private final String originalFaction;  // 招募前的阵营 ID，未招募时为 null

// 客户端收到后：
// - 渲染 NPC 名牌时：faction=player 则显示 "originalFaction 阵营 (已招募)"
// - 未招募 NPC：originalFaction 为 null，正常显示当前 faction
```

所有更新 NpcStatsPayload 的地方（setOwner、dismiss、NBT 加载迁移）都需要同步 originalFaction 字段。

---

## 9. 已有存档迁移

### 9.1 迁移策略

**静默迁移**（用户选择）：已有存档中的已招募 NPC 在下次加载时自动切换阵营。

### 9.2 迁移逻辑

```java
// NpcBase.readAdditionalSaveData() 中，加载完成后：

if (this.ownerUUID != null && !"sagadyssey:player".equals(this.faction.id())) {
    // 旧存档：有主人但还属于原阵营 → 自动迁移
    SagadysseyMod.LOGGER.info("迁移已招募 NPC: {} 从 {} 转到 player 阵营 (owner={})",
        this.getDisplayName().getString(),
        this.faction.id(),
        this.ownerUUID
    );
    this.originalFaction = this.faction.id();
    this.faction = FactionRegistry.get("sagadyssey:player");
}
```

### 9.3 迁移触发时机

- NPC 从 chunk 加载时（`readAdditionalSaveData`）
- 不需要玩家手动操作
- 不需要命令
- 迁移仅一次——迁移后立即写入新的 NBT，下次加载不会再触发

---

## 10. 多人游戏考量

### 10.1 不同玩家的 NPC 交互

```
玩家A的NPC (faction=player, ownerUUID=A)
玩家B的NPC (faction=player, ownerUUID=B)

玩家A攻击玩家B的NPC：
  → 检查玩家A对 player 阵营（参数化为 ownerUUID=B）的声望
  → "player 阵营"对玩家A来说不是一个正常阵营
  → 应使用 PvP 规则：如果 PvP 开启，允许攻击，声望无变化
  
玩家B的NPC看到玩家A：
  → 同主人 NPC 互认为盟友（通过 isAlliedTo 检查 ownerUUID）
  → 不同主人 → 中立，不主动攻击
```

### 10.2 PvP 规则（远期）

当前阶段（v1.0）：
- 不同玩家的 NPC 互不攻击（视为 NEUTRAL）。两个 player 阵营 NPC 相遇时，`isHostileBetween()` 返回 false
- 玩家可以攻击其他玩家的 NPC（受服务器 PvP 设置约束）
- 同一玩家的多个 NPC 互认为盟友（`isAlliedTo()` 通过 ownerUUID 匹配）

远期可扩展：
- 玩家间结盟 → NPC 阵列共享"盟友"关系
- 玩家间敌对 → NPC 阵列互相敌对

---

## 11. 边界情况

### 11.1 原阵营被数据包删除

如果整合包删除了某个阵营（如去掉 `kingdom.json`），而已招募 NPC 的 `originalFaction = "sagadyssey:kingdom"`：

- 解散时 `FactionRegistry.get("sagadyssey:kingdom")` 返回 null
- fallback 到 `FactionRegistry.get("sagadyssey:wilderness")`
- 日志警告

### 11.2 player 阵营 JSON 被删除

如果整合包意外删除了 `player.json`：

- `FactionRegistry.get("sagadyssey:player")` 返回 null
- 招募时检测 null → fallback 到 wilderness + 日志警告
- player 阵营 JSON 标记为 `"can_recruit": false` 防止意外

### 11.3 同一 NPC 被多次招募/解散

```
招募 → 解散 → 招募 → 解散
```

每次解散后 `originalFaction` 置为 null。再次招募时 `originalFaction` 重新从当前 `faction` 读取。循环安全。

### 11.4 刷怪蛋生成已招募 NPC

如果使用刷怪蛋生成一个 NBT 预设了 `OwnerUUID` 的 NPC：

- `finalizeSpawn` 中检测到 `OwnerUUID` → 自动设置 faction 为 player
- 这样命令方块/数据包可以生成"预招募"的 NPC

### 11.5 已招募 NPC 获得原阵营的声望变化

如果玩家带着已招募的 NPC（originally kingdom）去杀王国 NPC：

- 玩家 → kingdom 声望降低（正常）
- 已招募 NPC 不参与判定（它的攻击行为由 ProtectOwnerGoal 控制，而非阵营敌对判定）
- 已招募 NPC 不会因为"攻击原阵营"而触发任何额外惩罚

---

## 12. 实施阶段

### 第一阶段：数据层（预计 0.5 天）

- [ ] 创建 `player.json` 阵营定义文件
- [ ] `FactionRegistry.getPlayerFaction()` 硬编码兜底方法
- [ ] NpcBase 新增 `originalFaction` 字段 + getter/setter
- [ ] NpcBase NBT 读写 `OriginalFaction` 标签
- [ ] `FactionStandings` 对 `sagadyssey:player` 的特殊处理
- [ ] `./gradlew build` 通过

### 第二阶段：招募/解散逻辑（预计 0.5 天）

- [ ] `NpcInteractionPacket.handleRecruit()` 添加阵营切换
- [ ] `NpcBase.setOwner()` 添加阵营切换逻辑
- [ ] `NpcBase.dismiss()` 新方法（恢复原阵营）
- [ ] `NpcCommandScreen` 添加"解散"按钮
- [ ] 网络同步：NpcStatsPayload 添加 originalFaction 字段
- [ ] `./gradlew runClient` 验证招募→阵营切换→解散→恢复

### 第三阶段：AI 行为适配（预计 1 天）

- [ ] `NpcHostileGoal`：适配 player 阵营判定
- [ ] `ProtectOwnerGoal`：适配 player 阵营判定
- [ ] `isAlliedTo()`：优化同阵营判定逻辑
- [ ] 交易价格：player 阵营 NPC 不受原阵营声望影响
- [ ] `./gradlew runClient` 验证 AI 行为正确

### 第四阶段：存档迁移 + 清理（预计 0.5 天）

- [ ] NBT 加载时自动检测并迁移旧存档
- [ ] 移除临时补丁代码（如有）
- [ ] GUI 适配：声望图表中已招募 NPC 显示 "(已招募)"
- [ ] `./gradlew build` + `runClient` 全流程测试

### 第五阶段：测试（预计 0.5 天）

- [ ] 单元测试：招募→切阵营→解散→恢复
- [ ] 集成测试：已招募 NPC 在声望系统下的行为
- [ ] 多人测试：两个玩家的 NPC 互不干扰
- [ ] 旧存档迁移测试

**总计：约 3 天**

---

## 13. 风险与顾虑

### 13.1 与其他模块的交互

| 模块 | 影响 | 风险 |
|------|------|------|
| NPC 交易 | player 阵营 NPC 使用 originalFaction 交易表 | 低——交易表查找已有阵营参数 |
| Structure | 无直接影响 | 无 |
| Vehicle | NPC 骑乘行为不变 | 无 |
| 声望 GUI | player 阵营不在 GUI 中显示 | 低——加一行过滤 |

### 13.2 回退复杂度

如果玩家阵营设计需要撤销：
1. 移除 `player.json`
2. 回退 `NpcBase.setOwner()` 
3. 添加一次性迁移脚本：将所有 faction=player 的 NPC 恢复为 originalFaction

回退成本：低（约 2 小时）。

### 13.3 性能影响

- `FactionStandings.getLevel()` 新增一个字符串比较（`"sagadyssey:player".equals(id)`）→ 可忽略
- NBT 新增一个短字符串（`OriginalFaction`）→ 每 NPC 约 20-30 字节
- 无新 tick 处理、无新事件监听

---

## 附录 A：与原阵营设计文档的交叉引用

| 本文档章节 | 对应原文档章节 | 关系 |
|-----------|--------------|------|
| §3 阵营定义 | §3.2, §11.1 | 新增第 8 个阵营 JSON |
| §4 NPC 生命周期 | §9.1, §9.3 | 扩展 NPC 属性，修改招募门槛逻辑 |
| §5 声望系统 | §4.1, §4.2 | FactionStandings 增加特殊 case |
| §6 阵营间关系 | §5 | player 阵营独立于关系矩阵 |
| §8 数据模型 | §10 | NpcBase + FactionStandings 字段变更 |
| §12 实施阶段 | §14 | 新增独立实施路线 |

---

## 附录 B：术语

| 术语 | 说明 |
|------|------|
| player 阵营 | `sagadyssey:player`，Registry 中的一个阵营条目，行为由 ownerUUID 参数化 |
| originalFaction | NPC 被招募前的阵营 ID，用于解散时恢复 |
| 解散 (dismiss) | 将已招募 NPC 恢复到原阵营、清除 owner |
| 招募 (recruit) | 支付绿宝石、设置 ownerUUID、切换到 player 阵营 |
| ownerUUID 参数化 | player 阵营通过所属玩家的 UUID 来查询声望，而非维护独立的声望数据 |

---

> **文档状态：** v1.1，CC 审视反馈已修正（5 个问题 + 2 个建议）  
> **设计决策已确认：** 招募不影响声望、每玩家独立实例、静默迁移  
> **待确认：** 解散后 NPC 是否保留职业/装备、PvP 规则细节

### v1.1 变更记录（2026-08-01，CC 反馈修正）

| # | 问题 | 修正 |
|---|------|------|
| 1 | §5.2 对称性假设错误——FactionRelationMatrix 非对称，player 阵营不在矩阵中 | 重写 §5.2：改为两段判定（canBeHostile + 玩家声望），明确定从关系矩阵改为基于声望的判定 |
| 2 | §7.1 交易解耦模糊——跳过声望门控还是只跳价格？ | 明确：跳过所有声望门控（等级限制+价格修正），所有原阵营物品全开放，价格固定100%。NpcTradeRegistry 新增 ignoreReputation 参数 |
| 3 | §8.4 AI 集成点未穷举 | 重写 §8.4：列出 6 个需要适配的方法（isAlliedTo、NpcHostileGoal、ProtectOwnerGoal、NpcFactionEvents、isHostileTo），附完整逻辑和统一工具方法 isHostileBetween() |
| 4 | FactionRegistry 兜底缺失——player.json 被删后 NPC 静默变 wilderness | 新增 §3.3：FactionRegistry.getPlayerFaction() 硬编码兜底，确保 JSON 丢失不丢阵营归属 |
| 5 | setOwner(null) 调用路径未审查 | §8.1 注释中标明所有 setOwner(null) 调用路径，setOwner 不再处理阵营切换（交给 dismiss()），防止误触发 |
| 6 | §10.2 PvP 行为未明确 | 补充：不同 owner NPC 互不攻击（NEUTRAL），isHostileBetween() 返回 false |
| 7 | NpcStatsPayload 缺少 originalFaction | 新增 §8.5：payload 中同步 originalFaction，客户端用于渲染 "(已招募)" 标注 |
