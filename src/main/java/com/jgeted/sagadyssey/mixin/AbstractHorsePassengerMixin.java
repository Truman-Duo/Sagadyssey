package com.jgeted.sagadyssey.mixin;

import com.jgeted.sagadyssey.npc.entity.NpcBase;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 修正 NPC 骑乘坐骑时浮空的定位。
 * 原版马的乘客偏移是按玩家身高设计的，NPC 坐在上面会飘起来。
 */
@Mixin(AbstractHorse.class)
public class AbstractHorsePassengerMixin {

    @Inject(method = "positionRider", at = @At("HEAD"), cancellable = true)
    private void sagadyssey$positionNpcRider(
            Entity passenger,
            AbstractHorse.MoveFunction move,
            CallbackInfo ci
    ) {
        if (passenger instanceof NpcBase) {
            AbstractHorse self = (AbstractHorse) (Object) this;
            passenger.absMoveTo(self.getX(), self.getY() + 0.8, self.getZ());
            ci.cancel();
        }
    }
}
