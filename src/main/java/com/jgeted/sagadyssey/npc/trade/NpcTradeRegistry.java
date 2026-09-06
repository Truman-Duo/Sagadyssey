package com.jgeted.sagadyssey.npc.trade;

import com.jgeted.sagadyssey.npc.profession.NpcProfession;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.enchantment.Enchantments;

import java.util.*;

/**
 * 每个职业每级的交易池 + 商人跨职业池 + 抽取逻辑。
 */
public class NpcTradeRegistry {

    /** 每个职业每级抽几项（下标 = level-1） */
    public static final Map<NpcProfession, int[]> PICK_COUNTS = Map.ofEntries(
            Map.entry(NpcProfession.BLACKSMITH, new int[]{2, 2, 2, 2}),
            Map.entry(NpcProfession.FARMER,    new int[]{2, 2, 2, 1}),
            Map.entry(NpcProfession.WORKER,    new int[]{3, 2, 2, 2}),
            Map.entry(NpcProfession.WARRIOR,   new int[]{2, 2, 2, 1}),
            Map.entry(NpcProfession.ARCHER,    new int[]{2, 2, 2, 2}),
            Map.entry(NpcProfession.HEAVY,     new int[]{1, 2, 2, 1}),
            Map.entry(NpcProfession.MEDIC,     new int[]{2, 2, 2, 2}),
            Map.entry(NpcProfession.BARD,      new int[]{1, 2, 1, 1}),
            Map.entry(NpcProfession.TRADER,    new int[]{3, 3, 3, 2}),
            Map.entry(NpcProfession.NONE,      new int[]{0, 0, 0, 0})
    );

    /** 每个职业每级的池（List 的下标 = level-1） */
    private static final Map<NpcProfession, List<List<NpcTradeOffer>>> POOLS = new HashMap<>();

    static {
        // ==================== 铁匠 ====================
        List<List<NpcTradeOffer>> bs = new ArrayList<>();
        bs.add(List.of(  // Lv1 — 材料 + 功能性方块
                offer(e(), 1, 1, iron(), 2, 3, 1),
                offer(e(), 1, 1, lantern(), 1, 1, 1),
                offer(e(), 1, 1, coal(), 6, 8, 1),
                offer(e(), 1, 1, furnace(), 1, 1, 1),
                buyOffer(iron(), 4, 6, e(), 1, 1),
                buyOffer(coal(), 12, 14, e(), 1, 1)
        ));
        bs.add(List.of(  // Lv2 — 铁工具 + 盾牌
                offer(e(), 2, 2, ironPick(), 1, 1, 2),
                offer(e(), 2, 2, ironAxe(), 1, 1, 2),
                offer(e(), 2, 2, ironShovel(), 1, 1, 2),
                offer(e(), 2, 2, ironHoe(), 1, 1, 2),
                offer(e(), 2, 2, ironSword(), 1, 1, 2),
                offer(e(), 2, 2, shield(), 1, 1, 2)
        ));
        bs.add(List.of(  // Lv3 — 钻石工具
                offer(e(), 3, 4, diamondPick(), 1, 1, 3),
                offer(e(), 3, 4, diamondAxe(), 1, 1, 3),
                offer(e(), 3, 4, diamondShovel(), 1, 1, 3),
                offer(e(), 3, 4, diamondHoe(), 1, 1, 3),
                offer(e(), 4, 5, diamondSword(), 1, 1, 3),
                offer(e(), 2, 3, anvil(), 1, 1, 3)
        ));
        bs.add(List.of(  // Lv4 — 大师级
                offer(goldIngot(), 12, 12, netheriteUpgrade(), 1, 1, 4),
                offerSpecial(goldIngot(), 12, 12, diamondPick(), 1, 1, 4, mendingDiamondPick())
        ));
        POOLS.put(NpcProfession.BLACKSMITH, bs);

        // ==================== 农民 ====================
        List<List<NpcTradeOffer>> fm = new ArrayList<>();
        fm.add(List.of(  // Lv1 — 作物批量买卖
                offer(e(), 1, 1, wheat(), 11, 15, 1),
                offer(e(), 1, 1, wheatSeeds(), 6, 7, 1),
                offer(e(), 1, 1, potato(), 5, 6, 1),
                offer(e(), 1, 1, carrot(), 7, 8, 1),
                offer(e(), 1, 1, beetSeeds(), 6, 7, 1),
                offer(e(), 1, 1, beetroot(), 7, 8, 1),
                buyOffer(wheat(), 18, 22, e(), 1, 1),
                buyOffer(carrot(), 11, 12, e(), 1, 1),
                buyOffer(beetroot(), 13, 15, e(), 1, 1),
                buyOffer(potato(), 12, 14, e(), 1, 1)
        ));
        fm.add(List.of(  // Lv2 — 加工食物 + 南瓜西瓜
                offer(e(), 1, 1, bread(), 3, 4, 2),
                offer(e(), 1, 1, pumpkinSeeds(), 2, 3, 2),
                offer(e(), 1, 1, melonSlice(), 8, 12, 2),
                offer(e(), 1, 1, melonSeeds(), 2, 3, 2),
                buyOffer(pumpkin(), 2, 2, e(), 1, 1, 2),
                buyOffer(melonSlice(), 12, 16, e(), 1, 1, 2)
        ));
        fm.add(List.of(  // Lv3 — 高级食物
                offer(e(), 2, 2, goldenCarrot(), 2, 3, 3),
                offer(e(), 2, 2, glisteringMelon(), 2, 3, 3),
                offer(e(), 1, 1, pumpkinPie(), 2, 3, 3),
                offer(e(), 1, 1, mushroomStew(), 1, 1, 3),
                offer(e(), 2, 2, cake(), 1, 1, 3),
                buyOffer(redMushroom(), 8, 10, e(), 1, 1, 3),
                buyOffer(brownMushroom(), 8, 10, e(), 1, 1, 3)
        ));
        fm.add(List.of(  // Lv4 — 大师级
                offer(goldIngot(), 4, 4, chorusFruit(), 1, 1, 4)
        ));
        POOLS.put(NpcProfession.FARMER, fm);

        // ==================== 工人 ====================
        List<List<NpcTradeOffer>> wk = new ArrayList<>();
        wk.add(List.of(  // Lv1 — 建材 + 基础工具
                offer(e(), 1, 1, cobblestone(), 7, 9, 1),
                offer(e(), 1, 1, stone(), 5, 7, 1),
                offer(e(), 1, 1, dirt(), 9, 12, 1),
                offer(e(), 1, 1, sand(), 6, 8, 1),
                offer(e(), 1, 1, gravel(), 5, 7, 1),
                offer(e(), 1, 1, torch(), 5, 7, 1),
                offer(e(), 1, 1, ladder(), 4, 6, 1),
                offer(e(), 1, 1, stonePick(), 1, 1, 1),
                offer(e(), 1, 1, stoneAxe(), 1, 1, 1),
                offer(e(), 1, 1, stoneShovel(), 1, 1, 1),
                buyOffer(coal(), 14, 18, e(), 1, 1),
                buyOffer(cobblestone(), 18, 22, e(), 1, 1),
                buyOffer(iron(), 3, 4, e(), 1, 1)
        ));
        wk.add(List.of(  // Lv2 — 加工石材 + 原木回收
                offer(e(), 1, 1, stoneBricks(), 4, 8, 2),
                offer(e(), 1, 1, polishedAndesite(), 4, 8, 2),
                offer(e(), 1, 1, polishedDiorite(), 4, 8, 2),
                offer(e(), 1, 1, polishedGranite(), 4, 8, 2),
                buyOffer(stone(), 22, 30, e(), 1, 1, 2),
                buyOffer(andesite(), 24, 32, e(), 1, 1, 2),
                buyOffer(diorite(), 24, 32, e(), 1, 1, 2),
                buyOffer(granite(), 24, 32, e(), 1, 1, 2),
                buyOffer(darkOakLog(), 8, 10, e(), 1, 1, 2),
                buyOffer(jungleLog(), 8, 10, e(), 1, 1, 2)
        ));
        wk.add(List.of(  // Lv3 — 建材升级 + 回收
                offer(e(), 1, 1, glass(), 4, 6, 3),
                offer(e(), 1, 1, bricks(), 4, 6, 3),
                buyOffer(clay(), 8, 12, e(), 1, 1, 3),
                buyOffer(sand(), 8, 12, e(), 1, 1, 3)
        ));
        wk.add(List.of(  // Lv4 — 大师级
                offerSpecial(goldIngot(), 28, 32, placeholder(), 1, 1, 4, efficiencyVBook()),
                offerSpecial(goldIngot(), 24, 28, placeholder(), 1, 1, 4, unbreakingIIIBook())
        ));
        POOLS.put(NpcProfession.WORKER, wk);

        // ==================== 战士 ====================
        List<List<NpcTradeOffer>> wr = new ArrayList<>();
        wr.add(List.of(  // Lv1 — 武器 + 基础护甲
                offer(e(), 1, 1, stoneSword(), 1, 1, 1),
                offer(e(), 2, 2, ironSword(), 1, 1, 1),
                offer(e(), 2, 2, shield(), 1, 1, 1),
                offer(e(), 2, 2, bow(), 1, 1, 1),
                offer(e(), 1, 1, arrow(), 12, 16, 1),
                offer(e(), 2, 2, leatherChest(), 1, 1, 1),
                offer(e(), 2, 2, leatherLegs(), 1, 1, 1),
                buyOffer(rottenFlesh(), 14, 18, e(), 1, 1),
                buyOffer(bone(), 10, 14, e(), 1, 1)
        ));
        wr.add(List.of(  // Lv2 — 锁链甲 + 战利品回收
                offer(e(), 2, 2, ironSword(), 1, 1, 2),
                offer(e(), 2, 2, ironAxe(), 1, 1, 2),
                offer(e(), 2, 2, chainHelmet(), 1, 1, 2),
                offer(e(), 3, 3, chainChest(), 1, 1, 2),
                offer(e(), 3, 3, chainLegs(), 1, 1, 2),
                offer(e(), 2, 2, chainBoots(), 1, 1, 2),
                buyOffer(gunpowder(), 3, 4, e(), 1, 1, 2),
                buyOffer(slimeBall(), 6, 8, e(), 1, 1, 2)
        ));
        wr.add(List.of(  // Lv3 — 钻石武器 + 附魔铁甲
                offer(e(), 5, 5, diamondSword(), 1, 1, 3),
                offer(e(), 5, 5, diamondAxe(), 1, 1, 3),
                offerSpecial(e(), 5, 5, ironChest(), 1, 1, 3, enchantedIron(Items.IRON_CHESTPLATE)),
                offerSpecial(e(), 4, 4, ironLegs(), 1, 1, 3, enchantedIron(Items.IRON_LEGGINGS)),
                offerSpecial(e(), 3, 3, ironHelmet(), 1, 1, 3, enchantedIron(Items.IRON_HELMET)),
                offerSpecial(e(), 3, 3, ironBoots(), 1, 1, 3, enchantedIron(Items.IRON_BOOTS)),
                buyOffer(enderPearl(), 2, 3, e(), 2, 2, 3),
                buyOffer(blazeRod(), 2, 3, e(), 1, 1, 3)
        ));
        wr.add(List.of(  // Lv4 — 大师级
                offerSpecial(goldIngot(), 28, 32, diamondSword(), 1, 1, 4, sharpnessVSword())
        ));
        POOLS.put(NpcProfession.WARRIOR, wr);

        // ==================== 弓手 ====================
        List<List<NpcTradeOffer>> ar = new ArrayList<>();
        ar.add(List.of(  // Lv1 — 远程武器 + 弹药
                offer(e(), 2, 2, bow(), 1, 1, 1),
                offer(e(), 1, 1, arrow(), 14, 18, 1),
                offer(e(), 3, 3, crossbow(), 1, 1, 1),
                buyOffer(string(), 4, 6, e(), 1, 1),
                buyOffer(feather(), 8, 12, e(), 1, 1)
        ));
        ar.add(List.of(  // Lv2 — 弩 + 光灵箭
                offer(e(), 3, 3, crossbow(), 1, 1, 2),
                offer(e(), 2, 2, spectralArrow(), 8, 12, 2)
        ));
        ar.add(List.of(  // Lv3 — 附魔弓/弩
                offerSpecial(e(), 5, 5, bow(), 1, 1, 3, enchantedBow(Enchantments.POWER, 3)),
                offerSpecial(e(), 5, 5, bow(), 1, 1, 3, enchantedBow(Enchantments.PUNCH, 2)),
                offerSpecial(e(), 5, 5, bow(), 1, 1, 3, enchantedBow(Enchantments.FLAME, 1)),
                offerSpecial(e(), 6, 6, bow(), 1, 1, 3, enchantedBow(Enchantments.INFINITY, 1)),
                offerSpecial(e(), 5, 5, crossbow(), 1, 1, 3, enchantedCrossbow(Enchantments.QUICK_CHARGE, 2)),
                offerSpecial(e(), 5, 5, crossbow(), 1, 1, 3, enchantedCrossbow(Enchantments.MULTISHOT, 1)),
                offerSpecial(e(), 5, 5, crossbow(), 1, 1, 3, enchantedCrossbow(Enchantments.PIERCING, 3))
        ));
        ar.add(List.of(  // Lv4 — 大师级
                offerSpecial(goldIngot(), 32, 48, bow(), 1, 1, 4, ultimateBow()),
                offerSpecial(goldIngot(), 32, 48, crossbow(), 1, 1, 4, ultimateCrossbow())
        ));
        POOLS.put(NpcProfession.ARCHER, ar);

        // ==================== 重甲兵 ====================
        List<List<NpcTradeOffer>> hv = new ArrayList<>();
        hv.add(List.of(  // Lv1 — 重甲套
                offer(e(), 4, 5, ironChest(), 1, 1, 1),
                offer(e(), 3, 4, ironLegs(), 1, 1, 1),
                offer(e(), 2, 3, ironBoots(), 1, 1, 1),
                offer(e(), 2, 3, ironHelmet(), 1, 1, 1),
                offer(e(), 2, 2, shield(), 1, 1, 1),
                offer(e(), 2, 2, ironSword(), 1, 1, 1),
                buyOffer(iron(), 5, 7, e(), 1, 1),
                buyOffer(ironBlock(), 1, 1, e(), 2, 3, 1)
        ));
        hv.add(List.of(  // Lv2 — 钻石剑 + 战利品回收
                offer(e(), 3, 3, ironChest(), 1, 1, 2),
                offer(e(), 3, 3, ironLegs(), 1, 1, 2),
                offer(e(), 3, 3, ironHelmet(), 1, 1, 2),
                offer(e(), 3, 3, ironBoots(), 1, 1, 2),
                offer(e(), 5, 5, diamondSword(), 1, 1, 2),
                offer(e(), 2, 2, shield(), 1, 1, 2),
                buyOffer(gunpowder(), 3, 4, e(), 1, 1, 2),
                buyOffer(slimeBall(), 6, 8, e(), 1, 1, 2)
        ));
        hv.add(List.of(  // Lv3 — 钻石甲 + 锋利III钻石剑
                offer(e(), 7, 8, diamondChest(), 1, 1, 3),
                offer(e(), 6, 7, diamondLegs(), 1, 1, 3),
                offer(e(), 5, 7, diamondHelmet(), 1, 1, 3),
                offer(e(), 5, 7, diamondBoots(), 1, 1, 3),
                offerSpecial(e(), 8, 10, diamondSword(), 1, 1, 3, sharpnessIII()),
                buyOffer(enderPearl(), 2, 3, e(), 2, 2, 3),
                buyOffer(blazeRod(), 2, 3, e(), 1, 1, 3)
        ));
        hv.add(List.of(  // Lv4 — 大师级
                offerSpecial(goldIngot(), 28, 32, diamondAxe(), 1, 1, 4, sharpnessVAxe())
        ));
        POOLS.put(NpcProfession.HEAVY, hv);

        // ==================== 医师 ====================
        List<List<NpcTradeOffer>> md = new ArrayList<>();
        md.add(List.of(  // Lv1 — 医疗用品
                offer(e(), 2, 2, goldenApple(), 1, 1, 1),
                offer(e(), 3, 3, potion(), 1, 1, 1),
                offer(e(), 1, 1, paper(), 2, 4, 1)
        ));
        md.add(List.of(  // Lv2 — 治疗药水 + 药材回收
                offerPotion(e(), 2, 2, Items.POTION, Potions.HEALING, 1, 1, 2),
                offer(e(), 5, 6, goldenApple(), 1, 1, 2),
                buyOffer(ghastTear(), 2, 2, e(), 2, 2, 2),
                buyOffer(blazeRod(), 2, 3, e(), 1, 1, 2)
        ));
        md.add(List.of(  // Lv3 — 喷溅药水 + 酿造材料
                offerPotion(e(), 3, 3, Items.SPLASH_POTION, Potions.HEALING, 1, 1, 3),
                offerPotion(e(), 4, 4, Items.SPLASH_POTION, Potions.REGENERATION, 1, 1, 3),
                offer(e(), 1, 1, milkBucket(), 1, 1, 3),
                buyOffer(pufferfish(), 1, 1, e(), 4, 4, 3),
                buyOffer(netherWart(), 6, 8, e(), 1, 1, 3)
        ));
        md.add(List.of(  // Lv4 — 大师级
                offer(goldIngot(), 48, 64, totemOfUndying(), 1, 1, 4),
                offer(goldIngot(), 48, 64, enchantedGoldenApple(), 1, 1, 4)
        ));
        POOLS.put(NpcProfession.MEDIC, md);

        // ==================== 诗人 ====================
        List<List<NpcTradeOffer>> bd = new ArrayList<>();
        bd.add(List.of(  // Lv1 — 书卷类
                offer(e(), 1, 1, book(), 1, 1, 1),
                offer(e(), 3, 3, writableBook(), 1, 1, 1),
                offer(e(), 2, 2, nameTag(), 1, 1, 1),
                buyOffer(goldIngot(), 1, 1, e(), 3, 4, 1)
        ));
        bd.add(List.of(  // Lv2 — 音乐 + 附魔
                offer(e(), 3, 3, jukebox(), 1, 1, 2),
                offerSpecial(e(), 3, 3, placeholder(), 1, 1, 2, randomDisc()),
                offer(e(), 1, 1, noteBlock(), 2, 3, 2),
                offerSpecial(e(), 3, 3, placeholder(), 1, 1, 2, randomEnchantedBook()),
                buyOffer(lapisLazuli(), 16, 20, e(), 1, 1, 2),
                buyOffer(leather(), 8, 12, e(), 1, 1, 2),
                buyOffer(goldIngot(), 4, 6, e(), 1, 1, 2)
        ));
        bd.add(List.of(  // Lv3 — 导航工具
                offer(e(), 5, 5, recoveryCompass(), 1, 1, 3),
                offer(e(), 3, 3, compass(), 1, 1, 3),
                offer(e(), 2, 2, clock(), 1, 1, 3),
                buyOffer(echoShard(), 1, 2, e(), 1, 1, 3)
        ));
        bd.add(List.of(  // Lv4 — 大师级
                offer(goldIngot(), 6, 8, nameTag(), 1, 1, 4)
        ));
        POOLS.put(NpcProfession.BARD, bd);

        // 无职业——空池
        POOLS.put(NpcProfession.NONE,
                List.of(List.of(), List.of(), List.of(), List.of()));
        // 商人 Lv4 — 固定交易（不走跨职业池，不加价）
        POOLS.put(NpcProfession.TRADER,
                List.of(List.of(), List.of(), List.of(),
                        List.of(
                                offer(goldIngot(), 10, 10, diamond(), 1, 1, 4),
                                offer(goldIngot(), 1, 1, experienceBottle(), 16, 16, 4)
                        )
                ));
    }

    // ==================== 公开 API ====================

    /** 从指定职业指定等级的池中随机抽 count 项（不重复），并 resolve（含特殊物品） */
    public static List<NpcTradeOffer> pickRandom(NpcProfession prof, int level, long seed, int count, RegistryAccess registry) {
        List<NpcTradeOffer> pool = getPool(prof, level);
        if (pool.isEmpty() || count <= 0) return List.of();
        List<NpcTradeOffer> copy = new ArrayList<>(pool);
        Random rand = new Random(seed + level * 31L);
        Collections.shuffle(copy, rand);
        List<NpcTradeOffer> picked = copy.subList(0, Math.min(count, copy.size()));
        return picked.stream().map(t -> t.resolveWithRegistry(rand, registry)).toList();
    }

    /** 获取某职业某级的池子（不随机，模板状态） */
    public static List<NpcTradeOffer> getPool(NpcProfession prof, int level) {
        List<List<NpcTradeOffer>> levels = POOLS.getOrDefault(prof, List.of());
        int idx = Math.min(level - 1, levels.size() - 1);
        if (idx < 0 || levels.isEmpty()) return List.of();
        return levels.get(idx);
    }

    // ==================== 商人特殊逻辑 ====================

    /** 商人跨职业池 */
    public static List<NpcTradeOffer> getMerchantPool(int level) {
        if (level == 4) return getPool(NpcProfession.TRADER, 4);
        List<NpcTradeOffer> all = new ArrayList<>();
        for (NpcProfession prof : POOLS.keySet()) {
            if (prof == NpcProfession.NONE || prof == NpcProfession.TRADER) continue;
            for (NpcTradeOffer t : getPool(prof, level)) {
                if (t.specialResultResolver() != null) continue;
                if (isSingleDiscountable(t)) continue;
                all.add(t);
            }
        }
        return all;
    }

    /** 单件定价过低，商人无法打折获利 */
    private static boolean isSingleDiscountable(NpcTradeOffer t) {
        if (t.specialResultResolver() != null) return false;
        // 不可堆叠 + 结果 1 + 无范围
        return t.resultItem().getMaxStackSize() == 1
                && t.resultMin() == 1 && t.resultMax() == 1
                && t.costMin() == t.costMax()
                && t.costMin() <= 2;
    }

    /** 商人加价：提高 cost，降低 result */
    public static NpcTradeOffer applyMerchantMarkup(NpcTradeOffer original, double markupRate) {
        return applyMerchantMarkup(original, markupRate, false);
    }

    /**
     * 商人加价（可选跳过）。
     * ignoreReputation=true 时原样返回，用于 player 阵营 NPC 与主人的交易。
     */
    public static NpcTradeOffer applyMerchantMarkup(NpcTradeOffer original, double markupRate, boolean ignoreReputation) {
        if (ignoreReputation) return original;
        int newCostMin = Math.max(1, (int) Math.round(original.costMin() * (1.0 + markupRate)));
        int newCostMax = Math.max(1, (int) Math.round(original.costMax() * (1.0 + markupRate)));
        int newResultMin = Math.max(1, (int) Math.round(original.resultMin() * (1.0 - markupRate)));
        int newResultMax = Math.max(1, (int) Math.round(original.resultMax() * (1.0 - markupRate)));
        return new NpcTradeOffer(original.costItem(), newCostMin, newCostMax,
                original.resultItem(), newResultMin, newResultMax, original.minNpcLevel());
    }

    /** 商人货币兑换（固定，不计入 activeTrades） */
    public static NpcTradeOffer getCurrencyExchange(int slot) {
        return switch (slot) {
            case 0 -> new NpcTradeOffer(e(), 4, 4, goldIngot(), 1, 1, 1);
            case 1 -> new NpcTradeOffer(goldIngot(), 1, 1, e(), 3, 3, 1);
            default -> throw new IllegalArgumentException("slot must be 0 or 1");
        };
    }

    // ==================== 工厂方法 ====================

    private static NpcTradeOffer offer(ItemStack costItem, int costMin, int costMax,
                                        ItemStack resultItem, int resultMin, int resultMax,
                                        int minLevel) {
        return new NpcTradeOffer(costItem, costMin, costMax, resultItem, resultMin, resultMax, minLevel);
    }

    private static NpcTradeOffer buyOffer(ItemStack costItem, int costMin, int costMax,
                                           ItemStack resultItem, int resultMin, int resultMax) {
        return new NpcTradeOffer(costItem, costMin, costMax, resultItem, resultMin, resultMax, 1);
    }
    private static NpcTradeOffer buyOffer(ItemStack costItem, int costMin, int costMax,
                                           ItemStack resultItem, int resultMin, int resultMax,
                                           int minLevel) {
        return new NpcTradeOffer(costItem, costMin, costMax, resultItem, resultMin, resultMax, minLevel);
    }

    private static NpcTradeOffer offerSpecial(ItemStack costItem, int costMin, int costMax,
                                               ItemStack resultItem, int resultMin, int resultMax,
                                               int minLevel, NpcTradeOffer.SpecialItemResolver resolver) {
        return new NpcTradeOffer(costItem, costMin, costMax, resultItem, resultMin, resultMax, minLevel, resolver);
    }

    /** 药水 offer：resultItem 为基底（POTION/SPLASH_POTION 等），Holder 已预绑定药水效果 */
    private static NpcTradeOffer offerPotion(ItemStack costItem, int costMin, int costMax,
                                              Item potionBase, net.minecraft.core.Holder<net.minecraft.world.item.alchemy.Potion> potionHolder,
                                              int resultMin, int resultMax, int minLevel) {
        return new NpcTradeOffer(costItem, costMin, costMax, new ItemStack(potionBase),
                resultMin, resultMax, minLevel,
                (rand, registry) -> {
                    ItemStack stack = new ItemStack(potionBase);
                    stack.set(DataComponents.POTION_CONTENTS, new PotionContents(potionHolder));
                    return stack;
                });
    }

    // ==================== 特殊物品 Resolver ====================

    private static NpcTradeOffer.SpecialItemResolver randomDisc() {
        return (rand, registry) -> new ItemStack(ALL_DISCS.get(rand.nextInt(ALL_DISCS.size())));
    }

    private static NpcTradeOffer.SpecialItemResolver randomEnchantedBook() {
        return (rand, registry) -> {
            var enchants = registry.registryOrThrow(Registries.ENCHANTMENT)
                    .holders()
                    .filter(e -> e.is(EnchantmentTags.IN_ENCHANTING_TABLE))
                    .toList();
            if (enchants.isEmpty()) return new ItemStack(Items.ENCHANTED_BOOK);
            var picked = enchants.get(rand.nextInt(enchants.size()));
            ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
            book.enchant(picked, 1);
            return book;
        };
    }

    /** 附魔铁甲：耐久I / 保护I / 两者皆有 */
    private static NpcTradeOffer.SpecialItemResolver enchantedIron(Item armorItem) {
        return (rand, registry) -> {
            ItemStack armor = new ItemStack(armorItem);
            var enchReg = registry.registryOrThrow(Registries.ENCHANTMENT);
            int roll = rand.nextInt(3);
            if (roll == 0) {
                armor.enchant(enchReg.getHolderOrThrow(Enchantments.UNBREAKING), 1);
            } else if (roll == 1) {
                armor.enchant(enchReg.getHolderOrThrow(Enchantments.PROTECTION), 1);
            } else {
                armor.enchant(enchReg.getHolderOrThrow(Enchantments.UNBREAKING), 1);
                armor.enchant(enchReg.getHolderOrThrow(Enchantments.PROTECTION), 1);
            }
            return armor;
        };
    }

    private static NpcTradeOffer.SpecialItemResolver enchantedBow(net.minecraft.resources.ResourceKey<net.minecraft.world.item.enchantment.Enchantment> ench, int level) {
        return (rand, registry) -> {
            ItemStack bow = new ItemStack(Items.BOW);
            bow.enchant(registry.registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(ench), level);
            return bow;
        };
    }

    private static NpcTradeOffer.SpecialItemResolver enchantedCrossbow(net.minecraft.resources.ResourceKey<net.minecraft.world.item.enchantment.Enchantment> ench, int level) {
        return (rand, registry) -> {
            ItemStack crossbow = new ItemStack(Items.CROSSBOW);
            crossbow.enchant(registry.registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(ench), level);
            return crossbow;
        };
    }

    private static NpcTradeOffer.SpecialItemResolver sharpnessIII() {
        return (rand, registry) -> {
            ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
            sword.enchant(registry.registryOrThrow(Registries.ENCHANTMENT)
                    .getHolderOrThrow(Enchantments.SHARPNESS), 3);
            return sword;
        };
    }

    // ==================== Lv4 特殊 Resolver ====================

    private static NpcTradeOffer.SpecialItemResolver mendingDiamondPick() {
        return (rand, registry) -> {
            ItemStack pick = new ItemStack(Items.DIAMOND_PICKAXE);
            pick.enchant(registry.registryOrThrow(Registries.ENCHANTMENT)
                    .getHolderOrThrow(Enchantments.MENDING), 1);
            return pick;
        };
    }

    private static NpcTradeOffer.SpecialItemResolver sharpnessVSword() {
        return (rand, registry) -> {
            ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
            sword.enchant(registry.registryOrThrow(Registries.ENCHANTMENT)
                    .getHolderOrThrow(Enchantments.SHARPNESS), 5);
            return sword;
        };
    }

    private static NpcTradeOffer.SpecialItemResolver sharpnessVAxe() {
        return (rand, registry) -> {
            ItemStack axe = new ItemStack(Items.DIAMOND_AXE);
            axe.enchant(registry.registryOrThrow(Registries.ENCHANTMENT)
                    .getHolderOrThrow(Enchantments.SHARPNESS), 5);
            return axe;
        };
    }

    private static NpcTradeOffer.SpecialItemResolver efficiencyVBook() {
        return (rand, registry) -> {
            ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
            book.enchant(registry.registryOrThrow(Registries.ENCHANTMENT)
                    .getHolderOrThrow(Enchantments.EFFICIENCY), 5);
            return book;
        };
    }

    private static NpcTradeOffer.SpecialItemResolver unbreakingIIIBook() {
        return (rand, registry) -> {
            ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
            book.enchant(registry.registryOrThrow(Registries.ENCHANTMENT)
                    .getHolderOrThrow(Enchantments.UNBREAKING), 3);
            return book;
        };
    }

    private static NpcTradeOffer.SpecialItemResolver ultimateBow() {
        return (rand, registry) -> {
            ItemStack bow = new ItemStack(Items.BOW);
            var enchReg = registry.registryOrThrow(Registries.ENCHANTMENT);
            bow.enchant(enchReg.getHolderOrThrow(Enchantments.POWER), 5);
            bow.enchant(enchReg.getHolderOrThrow(Enchantments.FLAME), 1);
            bow.enchant(enchReg.getHolderOrThrow(Enchantments.INFINITY), 1);
            return bow;
        };
    }

    private static NpcTradeOffer.SpecialItemResolver ultimateCrossbow() {
        return (rand, registry) -> {
            ItemStack crossbow = new ItemStack(Items.CROSSBOW);
            var enchReg = registry.registryOrThrow(Registries.ENCHANTMENT);
            crossbow.enchant(enchReg.getHolderOrThrow(Enchantments.QUICK_CHARGE), 3);
            crossbow.enchant(enchReg.getHolderOrThrow(Enchantments.MULTISHOT), 1);
            crossbow.enchant(enchReg.getHolderOrThrow(Enchantments.PIERCING), 4);
            return crossbow;
        };
    }

    // ==================== 所有音乐唱片 ====================
    private static final List<Item> ALL_DISCS = List.of(
            Items.MUSIC_DISC_13, Items.MUSIC_DISC_CAT, Items.MUSIC_DISC_BLOCKS,
            Items.MUSIC_DISC_CHIRP, Items.MUSIC_DISC_FAR, Items.MUSIC_DISC_MALL,
            Items.MUSIC_DISC_MELLOHI, Items.MUSIC_DISC_STAL, Items.MUSIC_DISC_STRAD,
            Items.MUSIC_DISC_WARD, Items.MUSIC_DISC_11, Items.MUSIC_DISC_WAIT,
            Items.MUSIC_DISC_OTHERSIDE, Items.MUSIC_DISC_5, Items.MUSIC_DISC_PIGSTEP,
            Items.MUSIC_DISC_RELIC
    );

    // ==================== 物品快捷工厂 ====================

    // Lv1
    private static ItemStack e() { return new ItemStack(Items.EMERALD); }
    private static ItemStack iron() { return new ItemStack(Items.IRON_INGOT); }
    private static ItemStack goldIngot() { return new ItemStack(Items.GOLD_INGOT); }
    private static ItemStack lantern() { return new ItemStack(Items.LANTERN); }
    private static ItemStack coal() { return new ItemStack(Items.COAL); }
    private static ItemStack furnace() { return new ItemStack(Items.FURNACE); }
    private static ItemStack ironPick() { return new ItemStack(Items.IRON_PICKAXE); }
    private static ItemStack ironAxe() { return new ItemStack(Items.IRON_AXE); }
    private static ItemStack ironShovel() { return new ItemStack(Items.IRON_SHOVEL); }
    private static ItemStack ironHoe() { return new ItemStack(Items.IRON_HOE); }
    private static ItemStack ironSword() { return new ItemStack(Items.IRON_SWORD); }
    private static ItemStack shield() { return new ItemStack(Items.SHIELD); }
    private static ItemStack wheat() { return new ItemStack(Items.WHEAT); }
    private static ItemStack wheatSeeds() { return new ItemStack(Items.WHEAT_SEEDS); }
    private static ItemStack potato() { return new ItemStack(Items.POTATO); }
    private static ItemStack carrot() { return new ItemStack(Items.CARROT); }
    private static ItemStack beetSeeds() { return new ItemStack(Items.BEETROOT_SEEDS); }
    private static ItemStack beetroot() { return new ItemStack(Items.BEETROOT); }
    private static ItemStack cobblestone() { return new ItemStack(Items.COBBLESTONE); }
    private static ItemStack stone() { return new ItemStack(Items.STONE); }
    private static ItemStack dirt() { return new ItemStack(Items.DIRT); }
    private static ItemStack sand() { return new ItemStack(Items.SAND); }
    private static ItemStack gravel() { return new ItemStack(Items.GRAVEL); }
    private static ItemStack torch() { return new ItemStack(Items.TORCH); }
    private static ItemStack ladder() { return new ItemStack(Items.LADDER); }
    private static ItemStack stonePick() { return new ItemStack(Items.STONE_PICKAXE); }
    private static ItemStack stoneAxe() { return new ItemStack(Items.STONE_AXE); }
    private static ItemStack stoneShovel() { return new ItemStack(Items.STONE_SHOVEL); }
    private static ItemStack stoneSword() { return new ItemStack(Items.STONE_SWORD); }
    private static ItemStack bow() { return new ItemStack(Items.BOW); }
    private static ItemStack arrow() { return new ItemStack(Items.ARROW); }
    private static ItemStack crossbow() { return new ItemStack(Items.CROSSBOW); }
    private static ItemStack leatherChest() { return new ItemStack(Items.LEATHER_CHESTPLATE); }
    private static ItemStack leatherLegs() { return new ItemStack(Items.LEATHER_LEGGINGS); }
    private static ItemStack ironChest() { return new ItemStack(Items.IRON_CHESTPLATE); }
    private static ItemStack ironLegs() { return new ItemStack(Items.IRON_LEGGINGS); }
    private static ItemStack ironBoots() { return new ItemStack(Items.IRON_BOOTS); }
    private static ItemStack ironHelmet() { return new ItemStack(Items.IRON_HELMET); }
    private static ItemStack ironBlock() { return new ItemStack(Items.IRON_BLOCK); }
    private static ItemStack rottenFlesh() { return new ItemStack(Items.ROTTEN_FLESH); }
    private static ItemStack bone() { return new ItemStack(Items.BONE); }
    private static ItemStack string() { return new ItemStack(Items.STRING); }
    private static ItemStack feather() { return new ItemStack(Items.FEATHER); }
    private static ItemStack goldenApple() { return new ItemStack(Items.GOLDEN_APPLE); }
    private static ItemStack potion() { return new ItemStack(Items.POTION); }
    private static ItemStack paper() { return new ItemStack(Items.PAPER); }
    private static ItemStack book() { return new ItemStack(Items.BOOK); }
    private static ItemStack writableBook() { return new ItemStack(Items.WRITABLE_BOOK); }
    private static ItemStack nameTag() { return new ItemStack(Items.NAME_TAG); }

    // Lv2
    private static ItemStack bread() { return new ItemStack(Items.BREAD); }
    private static ItemStack pumpkinSeeds() { return new ItemStack(Items.PUMPKIN_SEEDS); }
    private static ItemStack pumpkin() { return new ItemStack(Items.PUMPKIN); }
    private static ItemStack melonSlice() { return new ItemStack(Items.MELON_SLICE); }
    private static ItemStack melonSeeds() { return new ItemStack(Items.MELON_SEEDS); }
    private static ItemStack stoneBricks() { return new ItemStack(Items.STONE_BRICKS); }
    private static ItemStack polishedAndesite() { return new ItemStack(Items.POLISHED_ANDESITE); }
    private static ItemStack polishedDiorite() { return new ItemStack(Items.POLISHED_DIORITE); }
    private static ItemStack polishedGranite() { return new ItemStack(Items.POLISHED_GRANITE); }
    private static ItemStack andesite() { return new ItemStack(Items.ANDESITE); }
    private static ItemStack diorite() { return new ItemStack(Items.DIORITE); }
    private static ItemStack granite() { return new ItemStack(Items.GRANITE); }
    private static ItemStack darkOakLog() { return new ItemStack(Items.DARK_OAK_LOG); }
    private static ItemStack jungleLog() { return new ItemStack(Items.JUNGLE_LOG); }
    private static ItemStack chainHelmet() { return new ItemStack(Items.CHAINMAIL_HELMET); }
    private static ItemStack chainChest() { return new ItemStack(Items.CHAINMAIL_CHESTPLATE); }
    private static ItemStack chainLegs() { return new ItemStack(Items.CHAINMAIL_LEGGINGS); }
    private static ItemStack chainBoots() { return new ItemStack(Items.CHAINMAIL_BOOTS); }
    private static ItemStack diamondSword() { return new ItemStack(Items.DIAMOND_SWORD); }
    private static ItemStack gunpowder() { return new ItemStack(Items.GUNPOWDER); }
    private static ItemStack slimeBall() { return new ItemStack(Items.SLIME_BALL); }
    private static ItemStack spectralArrow() { return new ItemStack(Items.SPECTRAL_ARROW); }
    private static ItemStack ghastTear() { return new ItemStack(Items.GHAST_TEAR); }
    private static ItemStack blazeRod() { return new ItemStack(Items.BLAZE_ROD); }
    private static ItemStack jukebox() { return new ItemStack(Items.JUKEBOX); }
    private static ItemStack noteBlock() { return new ItemStack(Items.NOTE_BLOCK); }
    private static ItemStack lapisLazuli() { return new ItemStack(Items.LAPIS_LAZULI); }
    private static ItemStack leather() { return new ItemStack(Items.LEATHER); }

    // Lv3
    private static ItemStack diamondPick() { return new ItemStack(Items.DIAMOND_PICKAXE); }
    private static ItemStack diamondAxe() { return new ItemStack(Items.DIAMOND_AXE); }
    private static ItemStack diamondShovel() { return new ItemStack(Items.DIAMOND_SHOVEL); }
    private static ItemStack diamondHoe() { return new ItemStack(Items.DIAMOND_HOE); }
    private static ItemStack anvil() { return new ItemStack(Items.ANVIL); }
    private static ItemStack goldenCarrot() { return new ItemStack(Items.GOLDEN_CARROT); }
    private static ItemStack glisteringMelon() { return new ItemStack(Items.GLISTERING_MELON_SLICE); }
    private static ItemStack pumpkinPie() { return new ItemStack(Items.PUMPKIN_PIE); }
    private static ItemStack mushroomStew() { return new ItemStack(Items.MUSHROOM_STEW); }
    private static ItemStack cake() { return new ItemStack(Items.CAKE); }
    private static ItemStack redMushroom() { return new ItemStack(Items.RED_MUSHROOM); }
    private static ItemStack brownMushroom() { return new ItemStack(Items.BROWN_MUSHROOM); }
    private static ItemStack glass() { return new ItemStack(Items.GLASS); }
    private static ItemStack bricks() { return new ItemStack(Items.BRICKS); }
    private static ItemStack clay() { return new ItemStack(Items.CLAY); }
    private static ItemStack diamondChest() { return new ItemStack(Items.DIAMOND_CHESTPLATE); }
    private static ItemStack diamondLegs() { return new ItemStack(Items.DIAMOND_LEGGINGS); }
    private static ItemStack diamondHelmet() { return new ItemStack(Items.DIAMOND_HELMET); }
    private static ItemStack diamondBoots() { return new ItemStack(Items.DIAMOND_BOOTS); }
    private static ItemStack enderPearl() { return new ItemStack(Items.ENDER_PEARL); }
    private static ItemStack pufferfish() { return new ItemStack(Items.PUFFERFISH); }
    private static ItemStack netherWart() { return new ItemStack(Items.NETHER_WART); }
    private static ItemStack milkBucket() { return new ItemStack(Items.MILK_BUCKET); }
    private static ItemStack recoveryCompass() { return new ItemStack(Items.RECOVERY_COMPASS); }
    private static ItemStack compass() { return new ItemStack(Items.COMPASS); }
    private static ItemStack clock() { return new ItemStack(Items.CLOCK); }
    private static ItemStack echoShard() { return new ItemStack(Items.ECHO_SHARD); }

    /** 特殊物品占位符（结果由 resolver 动态生成） */
    private static ItemStack placeholder() { return new ItemStack(Items.AIR); }

    // Lv4
    private static ItemStack netheriteUpgrade() { return new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE); }
    private static ItemStack chorusFruit() { return new ItemStack(Items.CHORUS_FRUIT); }
    private static ItemStack totemOfUndying() { return new ItemStack(Items.TOTEM_OF_UNDYING); }
    private static ItemStack enchantedGoldenApple() { return new ItemStack(Items.ENCHANTED_GOLDEN_APPLE); }
    private static ItemStack diamond() { return new ItemStack(Items.DIAMOND); }
    private static ItemStack experienceBottle() { return new ItemStack(Items.EXPERIENCE_BOTTLE); }
}
