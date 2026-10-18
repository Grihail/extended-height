package io.github.extendedheight.mixins.early.net;

import java.util.Arrays;

import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S23PacketBlockChange;
import net.minecraft.server.management.PlayerManager;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.ForgeModContainer;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.github.extendedheight.api.ExtendedHeight;
import io.github.extendedheight.api.HeightRange;
import io.github.extendedheight.net.SPacketExtChunkData;
import io.github.extendedheight.net.SPacketExtMultiBlockChange;

/** Пакеты изменений блоков чанка (5.3): в расширенных мирах int-позиции и long-маска */
@Mixin(targets = "net.minecraft.server.management.PlayerManager$PlayerInstance")
public abstract class MixinPlayerInstance {

    @Shadow
    @Final
    private ChunkCoordIntPair chunkLocation;
    @Shadow
    private int numberOfTilesToUpdate;

    @Shadow
    public abstract void sendToAllPlayersWatchingChunk(Packet packet);

    @Shadow
    private void sendTileToAllPlayersWatchingChunk(TileEntity te) {}

    /** null для ванильных миров */
    @Unique
    private HeightRange eh$range;
    @Unique
    private PlayerManager eh$manager;
    @Unique
    private int[] eh$positions;
    @Unique
    private long eh$sectionMask;

    @Inject(
        method = "<init>(Lnet/minecraft/server/management/PlayerManager;II)V",
        at = @At("RETURN"),
        require = 1,
        allow = 1)
    private void eh$init(PlayerManager manager, int x, int z, CallbackInfo ci) {
        HeightRange range = ExtendedHeight.getRange(manager.getWorldServer());
        if (!range.isVanilla()) {
            eh$range = range;
            eh$manager = manager;
            eh$positions = new int[64];
        }
    }

    @Inject(method = "flagChunkForUpdate", at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    @SuppressWarnings("unchecked")
    private void eh$flag(int x, int y, int z, CallbackInfo ci) {
        HeightRange range = eh$range;
        if (range == null) {
            return;
        }
        ci.cancel();
        if (!range.contains(y)) {
            return;
        }
        if (numberOfTilesToUpdate == 0) {
            ((PlayerManagerAccessor) eh$manager).eh$getChunkWatcherWithPlayers()
                .add(this);
        }
        eh$sectionMask |= 1L << range.sectionIndex(y);
        int packed = SPacketExtMultiBlockChange.pack(x, y, z, range.minY);
        for (int i = 0; i < numberOfTilesToUpdate; i++) {
            if (eh$positions[i] == packed) {
                return;
            }
        }
        if (numberOfTilesToUpdate == eh$positions.length) {
            eh$positions = Arrays.copyOf(eh$positions, eh$positions.length << 1);
        }
        eh$positions[numberOfTilesToUpdate++] = packed;
    }

    @Inject(method = "sendChunkUpdate", at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private void eh$sendChunkUpdate(CallbackInfo ci) {
        HeightRange range = eh$range;
        if (range == null) {
            return;
        }
        ci.cancel();
        int count = numberOfTilesToUpdate;
        if (count == 0) {
            return;
        }
        WorldServer world = eh$manager.getWorldServer();
        int baseX = chunkLocation.chunkXPos * 16;
        int baseZ = chunkLocation.chunkZPos * 16;
        if (count == 1) {
            int p = eh$positions[0];
            int x = baseX + SPacketExtMultiBlockChange.unpackX(p);
            int y = SPacketExtMultiBlockChange.unpackY(p, range.minY);
            int z = baseZ + SPacketExtMultiBlockChange.unpackZ(p);
            sendToAllPlayersWatchingChunk(new S23PacketBlockChange(x, y, z, world));
            if (world.getBlock(x, y, z)
                .hasTileEntity(world.getBlockMetadata(x, y, z))) {
                sendTileToAllPlayersWatchingChunk(world.getTileEntity(x, y, z));
            }
        } else {
            Chunk chunk = world.getChunkFromChunkCoords(chunkLocation.chunkXPos, chunkLocation.chunkZPos);
            if (count >= ForgeModContainer.clumpingThreshold) {
                sendToAllPlayersWatchingChunk(new SPacketExtChunkData(chunk, false, eh$sectionMask));
            } else {
                sendToAllPlayersWatchingChunk(new SPacketExtMultiBlockChange(chunk, eh$positions, count, range.minY));
            }
            // Forge: только тайлы изменённых блоков
            for (int i = 0; i < count; i++) {
                int p = eh$positions[i];
                int x = baseX + SPacketExtMultiBlockChange.unpackX(p);
                int y = SPacketExtMultiBlockChange.unpackY(p, range.minY);
                int z = baseZ + SPacketExtMultiBlockChange.unpackZ(p);
                if (world.getBlock(x, y, z)
                    .hasTileEntity(world.getBlockMetadata(x, y, z))) {
                    sendTileToAllPlayersWatchingChunk(world.getTileEntity(x, y, z));
                }
            }
        }
        numberOfTilesToUpdate = 0;
        eh$sectionMask = 0;
    }
}
