package com.jgeted.sagadyssey.npc.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;

/**
 * 指挥杖：右键地面两次标记工作范围两角，再右键 NPC 应用范围。
 * 蹲下 + 右键地面：清除杖上已标记的角。
 */
public class NpcCommandWandItem extends Item {

    public NpcCommandWandItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;
        ItemStack stack = context.getItemInHand();
        BlockPos pos = context.getClickedPos();

        if (context.getLevel().isClientSide) {
            // 客户端直接成功，交给服务端写 NBT，避免两端各写一份
            return InteractionResult.SUCCESS;
        }

        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (player.isShiftKeyDown()) {
            // 蹲下右键：清除标记
            tag.remove("Corner1");
            tag.remove("Corner2");
            player.displayClientMessage(Component.literal("§e已清除指挥杖上的标记"), true);
        } else if (!tag.contains("Corner1")) {
            tag.putIntArray("Corner1", new int[]{pos.getX(), pos.getY(), pos.getZ()});
            player.displayClientMessage(Component.literal("§a已标记第一角 " + pos.toShortString()
                    + "，走到对角后右键标记第二角"), true);
        } else if (!tag.contains("Corner2")) {
            tag.putIntArray("Corner2", new int[]{pos.getX(), pos.getY(), pos.getZ()});
            player.displayClientMessage(Component.literal("§a已标记第二角 " + pos.toShortString()
                    + "，现在右键 NPC 应用范围"), true);
        } else {
            player.displayClientMessage(Component.literal("§e两角已标记，右键 NPC 应用；蹲下右键地面清除"), true);
        }
        CustomData.set(DataComponents.CUSTOM_DATA, stack, tag);

        return InteractionResult.SUCCESS;
    }
}
