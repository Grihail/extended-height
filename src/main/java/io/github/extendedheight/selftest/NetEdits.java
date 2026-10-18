package io.github.extendedheight.selftest;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;

/** Серверные правки вокруг чанка для всех путей расширенных изменений: S23, S22, частичный S21 */
final class NetEdits {

    static final class Edit {

        final int x, y, z;
        final Block block;

        Edit(int x, int y, int z, Block block) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.block = block;
        }
    }

    private NetEdits() {}

    static List<Edit> serverEdits(int bx, int bz) {
        int cx = bx >> 4, cz = bz >> 4;
        List<Edit> edits = new ArrayList<>();
        edits.add(new Edit((cx + 1) * 16 + 4, -100, cz * 16 + 4, Blocks.glowstone));
        edits.add(new Edit((cx - 1) * 16 + 4, 300, cz * 16 + 4, Blocks.stone));
        for (int i = 0; i < 5; i++) {
            edits.add(new Edit(cx * 16 + 4, -50 + i, (cz + 1) * 16 + 4, Blocks.glass));
        }
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 5; z++) {
                edits.add(new Edit(cx * 16 + x, -30, (cz - 1) * 16 + z, Blocks.air));
            }
        }
        return edits;
    }
}
