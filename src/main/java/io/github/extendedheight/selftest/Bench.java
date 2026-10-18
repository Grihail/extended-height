package io.github.extendedheight.selftest;

import java.util.Arrays;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;

/** Грубый микробенчмарк getBlock/setBlock в живом сервере; медиана раундов после прогрева */
final class Bench {

    private static final int ROUNDS = 15;
    private static final int WARMUP = 5;
    private static final int SET_ROUNDS = 7;
    private static final int SET_WARMUP = 2;

    /** Сток, чтобы JIT не выкинул цикл */
    static int sink;

    private Bench() {}

    /** нс на getBlock по области 64x64 у чанка (cx0, cz0) и y в [y0, y1] */
    static double[] getBlock(World world, int cx0, int cz0, int y0, int y1) {
        int x0 = cx0 << 4, z0 = cz0 << 4;
        for (int cx = cx0; cx < cx0 + 4; cx++) for (int cz = cz0; cz < cz0 + 4; cz++) {
            world.getChunkFromChunkCoords(cx, cz);
        }
        long ops = 64L * 64 * (y1 - y0 + 1);
        int repeat = (int) Math.max(1, 8_000_000 / ops);
        double[] results = new double[ROUNDS];
        for (int round = -WARMUP; round < ROUNDS; round++) {
            long start = System.nanoTime();
            int acc = 0;
            for (int r = 0; r < repeat; r++) {
                for (int x = x0; x < x0 + 64; x++) {
                    for (int z = z0; z < z0 + 64; z++) {
                        for (int y = y0; y <= y1; y++) {
                            if (world.getBlock(x, y, z) != Blocks.air) acc++;
                        }
                    }
                }
            }
            long elapsed = System.nanoTime() - start;
            sink += acc;
            if (round >= 0) results[round] = (double) elapsed / (ops * repeat);
        }
        return median(results);
    }

    /** нс на setBlock (flags 2) слоя 32x32 на y: поставить и убрать */
    static double[] setBlock(World world, int cx0, int cz0, int y) {
        int x0 = cx0 << 4, z0 = cz0 << 4;
        for (int cx = cx0 - 1; cx < cx0 + 3; cx++) for (int cz = cz0 - 1; cz < cz0 + 3; cz++) {
            world.getChunkFromChunkCoords(cx, cz);
        }
        // setBlock упирается в свет (~1 мс), поэтому раундов меньше
        double[] results = new double[SET_ROUNDS];
        for (int round = -SET_WARMUP; round < SET_ROUNDS; round++) {
            long start = System.nanoTime();
            for (Block b : new Block[] { Blocks.stone, Blocks.air }) {
                for (int x = x0; x < x0 + 32; x++) {
                    for (int z = z0; z < z0 + 32; z++) {
                        world.setBlock(x, y, z, b, 0, 2);
                    }
                }
            }
            long elapsed = System.nanoTime() - start;
            if (round >= 0) results[round] = (double) elapsed / (2 * 32 * 32);
        }
        return median(results);
    }

    /** {медиана, минимум} замеренных раундов */
    private static double[] median(double[] values) {
        double[] v = values.clone();
        Arrays.sort(v);
        return new double[] { v[v.length / 2], v[0] };
    }
}
