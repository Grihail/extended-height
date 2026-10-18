package io.github.extendedheight.mixins.early.net;

import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.play.server.S26PacketMapChunkBulk;
import net.minecraft.world.WorldServer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import io.github.extendedheight.api.ExtendedHeight;
import io.github.extendedheight.api.HeightRange;
import io.github.extendedheight.net.ChunkCodec;
import io.github.extendedheight.net.SPacketExtMapChunkBulk;

/** Начальная отправка чанков: расширенный bulk, его лимит чанков, тайлы всего диапазона */
@Mixin(EntityPlayerMP.class)
public abstract class MixinEntityPlayerMP {

    @Unique
    private HeightRange eh$range() {
        return ExtendedHeight.getRange(((EntityPlayerMP) (Object) this).worldObj);
    }

    /** Чанков на пакет: ваниль 5, в расширенных мирах — по лимиту кадра 5.A */
    @Redirect(
        method = "onUpdate",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/network/play/server/S26PacketMapChunkBulk;func_149258_c()I"),
        require = 1,
        allow = 1)
    private int eh$bulkLimit() {
        HeightRange range = eh$range();
        return range.isVanilla() ? S26PacketMapChunkBulk.func_149258_c()
            : ChunkCodec.maxChunksPerBulk(range.sectionCount());
    }

    @Redirect(
        method = "onUpdate",
        at = @At(value = "NEW", target = "net/minecraft/network/play/server/S26PacketMapChunkBulk"),
        require = 1,
        allow = 1)
    @SuppressWarnings({ "rawtypes", "unchecked" })
    private S26PacketMapChunkBulk eh$newBulk(List chunks) {
        return eh$range().isVanilla() ? new S26PacketMapChunkBulk(chunks) : new SPacketExtMapChunkBulk(chunks);
    }

    /** Тайлы, отправляемые с чанками: ваниль берёт y 0..256 */
    @Redirect(
        method = "onUpdate",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/WorldServer;func_147486_a(IIIIII)Ljava/util/List;"),
        require = 1,
        allow = 1)
    @SuppressWarnings("rawtypes")
    private List eh$tileEntities(WorldServer world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        HeightRange range = ExtendedHeight.getRange(world);
        if (!range.isVanilla()) {
            minY = range.minY;
            maxY = range.maxYExclusive();
        }
        return world.func_147486_a(minX, minY, minZ, maxX, maxY, maxZ);
    }
}
