package io.github.extendedheight.mixins.early;

import java.util.List;

import net.minecraft.entity.Entity;
import net.minecraft.util.MathHelper;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityEvent;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.github.extendedheight.api.HeightRange;
import io.github.extendedheight.core.IExtendedHeightChunk;

/** 1.8: списки сущностей по реальным секциям расширенного чанка, индекс chunkCoordY - minSection */
@Mixin(Chunk.class)
public abstract class MixinChunkEntities {

    @Shadow
    public List[] entityLists;
    @Shadow
    public boolean hasEntities;
    @Shadow
    @org.spongepowered.asm.mixin.Final
    public int xPosition;
    @Shadow
    @org.spongepowered.asm.mixin.Final
    public int zPosition;

    @Unique
    private HeightRange eh$extendedRange() {
        HeightRange range = ((IExtendedHeightChunk) this).eh$getRange();
        return range.isVanilla() ? null : range;
    }

    /** Как в ванили (с событием Forge и предупреждением), но с реальной секцией */
    @Inject(method = "addEntity", at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    @SuppressWarnings("unchecked")
    private void eh$addEntity(Entity entity, CallbackInfo ci) {
        HeightRange range = eh$extendedRange();
        if (range == null) {
            return;
        }
        ci.cancel();
        hasEntities = true;
        int cx = MathHelper.floor_double(entity.posX / 16.0D);
        int cz = MathHelper.floor_double(entity.posZ / 16.0D);
        if (cx != xPosition || cz != zPosition) {
            org.apache.logging.log4j.LogManager.getLogger()
                .warn(
                    "Wrong location! " + entity
                        + " (at "
                        + cx
                        + ", "
                        + cz
                        + " instead of "
                        + xPosition
                        + ", "
                        + zPosition
                        + ")");
            Thread.dumpStack();
        }
        int section = MathHelper
            .clamp_int(MathHelper.floor_double(entity.posY / 16.0D), range.minSection(), range.maxSection());
        MinecraftForge.EVENT_BUS
            .post(new EntityEvent.EnteringChunk(entity, xPosition, zPosition, entity.chunkCoordX, entity.chunkCoordZ));
        entity.addedToChunk = true;
        entity.chunkCoordX = xPosition;
        entity.chunkCoordY = section;
        entity.chunkCoordZ = zPosition;
        entityLists[section - range.minSection()].add(entity);
    }

    /** Вызывающие передают entity.chunkCoordY, т.е. реальную секцию */
    @Inject(method = "removeEntityAtIndex", at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private void eh$removeEntityAtIndex(Entity entity, int section, CallbackInfo ci) {
        HeightRange range = eh$extendedRange();
        if (range == null) {
            return;
        }
        ci.cancel();
        int index = MathHelper.clamp_int(section - range.minSection(), 0, entityLists.length - 1);
        entityLists[index].remove(entity);
    }

    /** Секции AABB-запросов переводятся в индексы списков; по два вызова в двух методах */
    @Redirect(
        method = { "getEntitiesWithinAABBForEntity", "getEntitiesOfTypeWithinAAAB" },
        at = @At(value = "INVOKE", target = "Lnet/minecraft/util/MathHelper;clamp_int(III)I"),
        require = 4,
        allow = 4)
    private int eh$sectionToIndex(int section, int min, int max) {
        HeightRange range = eh$extendedRange();
        return MathHelper.clamp_int(range == null ? section : section - range.minSection(), min, max);
    }
}
