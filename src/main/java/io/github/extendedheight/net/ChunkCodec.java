package io.github.extendedheight.net;

import java.io.IOException;
import java.util.Arrays;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.NibbleArray;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

/** Данные чанков расширенных пакетов: формат S21/S26, но long-маски, бит i — storageArrays[i] */
public final class ChunkCodec {

    /** Наибольший кадр, который принимает ванильный декодер (длина varint 21 бит) */
    public static final int MAX_FRAME = 2097151;
    /** Худший случай секции: блоки 4096 + метаданные, свет блоков, свет неба, Add по 2048 */
    public static final int WORST_SECTION_BYTES = 12288;
    public static final int BIOME_BYTES = 256;
    /** Заголовок S26: id + short count + int сжатая длина + int исходная + sky + minSection */
    public static final int BULK_HEADER_BYTES = 3 + 2 + 4 + 4 + 1 + 1;
    /** На чанк в S26: int x + int z + long маска + long маска Add */
    public static final int BULK_PER_CHUNK_BYTES = 24;
    /** Ванильный S26 шлёт не больше стольких чанков; расширенный — тоже */
    public static final int VANILLA_BULK_CHUNKS = 5;

    private ChunkCodec() {}

    public static int sectionBytes(boolean sky) {
        return sky ? 4096 + 3 * 2048 : 4096 + 2 * 2048;
    }

    public static int rawLength(long mask, long addMask, boolean sky, boolean full) {
        return Long.bitCount(mask) * sectionBytes(sky) + Long.bitCount(addMask) * 2048 + (full ? BIOME_BYTES : 0);
    }

    /** Верхняя граница вывода zlib для несжимаемых данных */
    public static long deflateBound(long n) {
        return n + 5 * ((n + 65534) / 65535) + 6;
    }

    /** Наибольшее k, при котором полный S26 из k худших чанков влезает в кадр (5.A) */
    public static int maxChunksPerBulk(int sections) {
        long perChunk = (long) WORST_SECTION_BYTES * sections + BIOME_BYTES;
        int k = 0;
        while (k < VANILLA_BULK_CHUNKS && bulkFrameBound(k + 1, perChunk) <= MAX_FRAME) {
            k++;
        }
        return k;
    }

    public static long bulkFrameBound(int chunks, long rawPerChunk) {
        return BULK_HEADER_BYTES + (long) BULK_PER_CHUNK_BYTES * chunks + deflateBound(rawPerChunk * chunks);
    }

    public static final class Extracted {

        public long mask;
        public long addMask;
        public byte[] data;
    }

    /** Серверная копия S21PacketChunkData.func_149269_a с long-масками */
    public static Extracted extract(Chunk chunk, boolean full, long requestedMask) {
        ExtendedBlockStorage[] s = chunk.getBlockStorageArray();
        boolean sky = !chunk.worldObj.provider.hasNoSky;
        if (full) {
            chunk.sendUpdates = true;
        }
        Extracted e = new Extracted();
        for (int i = 0; i < s.length; i++) {
            if (s[i] != null && (!full || !s[i].isEmpty()) && (requestedMask & 1L << i) != 0) {
                e.mask |= 1L << i;
                if (s[i].getBlockMSBArray() != null) {
                    e.addMask |= 1L << i;
                }
            }
        }
        byte[] out = new byte[rawLength(e.mask, e.addMask, sky, full)];
        int p = 0;
        for (int i = 0; i < s.length; i++) {
            if ((e.mask & 1L << i) != 0) {
                byte[] lsb = s[i].getBlockLSBArray();
                System.arraycopy(lsb, 0, out, p, lsb.length);
                p += lsb.length;
            }
        }
        for (int i = 0; i < s.length; i++) {
            if ((e.mask & 1L << i) != 0) {
                p = copy(s[i].getMetadataArray(), out, p);
            }
        }
        for (int i = 0; i < s.length; i++) {
            if ((e.mask & 1L << i) != 0) {
                p = copy(s[i].getBlocklightArray(), out, p);
            }
        }
        if (sky) {
            for (int i = 0; i < s.length; i++) {
                if ((e.mask & 1L << i) != 0) {
                    p = copy(s[i].getSkylightArray(), out, p);
                }
            }
        }
        for (int i = 0; i < s.length; i++) {
            if ((e.addMask & 1L << i) != 0) {
                p = copy(s[i].getBlockMSBArray(), out, p);
            }
        }
        if (full) {
            byte[] biomes = chunk.getBiomeArray();
            System.arraycopy(biomes, 0, out, p, biomes.length);
            p += biomes.length;
        }
        if (p != out.length) {
            throw new IllegalStateException("Chunk data length " + p + " != expected " + out.length);
        }
        e.data = out;
        return e;
    }

    private static int copy(NibbleArray nibbles, byte[] out, int p) {
        System.arraycopy(nibbles.data, 0, out, p, nibbles.data.length);
        return p + nibbles.data.length;
    }

    /** Deflate с буфером, в который всё влезает (ваниль молча обрезает, 5.2) */
    public static byte[] deflate(byte[] raw) {
        Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION);
        try {
            deflater.setInput(raw);
            deflater.finish();
            byte[] out = new byte[(int) Math.min(Integer.MAX_VALUE - 8, deflateBound(raw.length) + 64)];
            int len = 0;
            while (!deflater.finished()) {
                if (len == out.length) {
                    out = Arrays.copyOf(out, out.length * 2);
                }
                len += deflater.deflate(out, len, out.length - len);
            }
            return Arrays.copyOf(out, len);
        } finally {
            deflater.end();
        }
    }

    public static byte[] inflate(byte[] compressed, int rawLength) throws IOException {
        byte[] out = new byte[rawLength];
        Inflater inflater = new Inflater();
        try {
            inflater.setInput(compressed);
            int n = 0;
            while (n < rawLength && !inflater.finished()) {
                int r = inflater.inflate(out, n, rawLength - n);
                if (r == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
                    break;
                }
                n += r;
            }
            if (n != rawLength || !inflater.finished()) {
                throw new IOException("Bad compressed chunk data: " + n + " of " + rawLength + " bytes");
            }
            return out;
        } catch (DataFormatException ex) {
            throw new IOException("Bad compressed chunk data format", ex);
        } finally {
            inflater.end();
        }
    }

    /** Предел длин из сети: самый большой чанк допустимого диапазона */
    public static void checkLength(int rawLength, int chunks) throws IOException {
        long max = ((long) WORST_SECTION_BYTES * 64 + BIOME_BYTES) * Math.max(1, chunks);
        if (rawLength < 0 || rawLength > max) {
            throw new IOException("Bad chunk data length " + rawLength);
        }
    }
}
