package io.github.extendedheight.mixins.early.net;

import java.util.List;

import net.minecraft.server.management.PlayerManager;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PlayerManager.class)
public interface PlayerManagerAccessor {

    /** Инстансы с ожидающими изменениями блоков, сбрасываются в updatePlayerInstances */
    @Accessor("chunkWatcherWithPlayers")
    @SuppressWarnings("rawtypes")
    List eh$getChunkWatcherWithPlayers();
}
