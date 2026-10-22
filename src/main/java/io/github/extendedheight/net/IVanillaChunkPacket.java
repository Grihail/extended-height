package io.github.extendedheight.net;

import net.minecraft.world.chunk.Chunk;

/** Что ванильный S21 расширенного мира помнит о чанке для замены в {@link ExtPackets#onOutbound} */
public interface IVanillaChunkPacket {

    /** Чанк, для которого сделан пакет, или null */
    Chunk eh$getChunk();

    /** Ванильный flagsYAreasToUpdate пакета */
    int eh$getFlags();

    /** Ванильный аргумент «весь чанк» */
    boolean eh$isFull();
}
