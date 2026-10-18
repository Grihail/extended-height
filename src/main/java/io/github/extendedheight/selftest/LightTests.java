package io.github.extendedheight.selftest;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;

import io.github.extendedheight.api.ExtendedHeight;
import io.github.extendedheight.api.HeightRange;

/** Тесты света этапа 2: свет полостей сверяется с {@link LightReference}, оболочка тёмная */
final class LightTests {

    static final String EXPECTED_FILE = "light-expected.txt";

    private final SelfTest t;
    private final WorldServer world;
    private final HeightRange range;

    LightTests(SelfTest t, WorldServer world) {
        this.t = t;
        this.world = world;
        this.range = ExtendedHeight.getRange(world);
    }

    // ---- конструкции (детерминированы, в verify строятся так же) ----

    static final class Structure {

        final String name;
        final Set<Long> cavityKeys = new LinkedHashSet<>();
        final List<int[]> cavity = new ArrayList<>();
        final List<int[]> shell = new ArrayList<>();
        final Map<Long, Block> sources = new HashMap<>();

        Structure(String name) {
            this.name = name;
        }

        Structure box(int x0, int y0, int z0, int x1, int y1, int z1) {
            for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++) {
                if (cavityKeys.add(LightReference.key(x, y, z))) {
                    cavity.add(new int[] { x, y, z });
                }
            }
            return this;
        }

        Structure source(int x, int y, int z, Block block) {
            sources.put(LightReference.key(x, y, z), block);
            return this;
        }

        /** Оболочка — соседи полости в мире, не входящие в неё */
        Structure close(HeightRange range) {
            Set<Long> seen = new LinkedHashSet<>();
            int[][] d = { { 1, 0, 0 }, { -1, 0, 0 }, { 0, 1, 0 }, { 0, -1, 0 }, { 0, 0, 1 }, { 0, 0, -1 } };
            for (int[] c : cavity) {
                for (int[] o : d) {
                    int x = c[0] + o[0], y = c[1] + o[1], z = c[2] + o[2];
                    long k = LightReference.key(x, y, z);
                    if (range.contains(y) && !cavityKeys.contains(k) && seen.add(k)) {
                        shell.add(new int[] { x, y, z });
                    }
                }
            }
            return this;
        }
    }

    /** Свет неба: шахта от maxY до y -20 через границы 256 и 0 с боковыми туннелями */
    Structure shaft() {
        return new Structure("shaft").box(50, -20, 50, 50, range.maxY, 50)
            .box(51, 0, 50, 55, 0, 50)
            .box(55, -4, 50, 55, -1, 50)
            .box(45, 256, 50, 49, 256, 50)
            .box(45, 252, 50, 45, 260, 50)
            .close(range);
    }

    /** Свет блоков: закрытая коробка через границу чанка x = 15/16 со светокамнем на y -200 */
    Structure borderBox() {
        return new Structure("border box").box(10, -205, 40, 21, -195, 46)
            .source(14, -200, 43, Blocks.glowstone)
            .close(range);
    }

    /** Свет блоков у дна мира: коробка на minY со светокамнем в нижнем слое */
    Structure bottomBox() {
        int b = range.minY;
        return new Structure("bottom box").box(30, b, 40, 36, b + 4, 46)
            .source(33, b, 43, Blocks.glowstone)
            .close(range);
    }

    // ---- фаза write ----

    void runWrite() throws IOException {
        loadChunks(-1, -1, 5, 5);
        Structure shaft = shaft();
        Structure border = borderBox();
        Structure bottom = bottomBox();
        for (Structure s : new Structure[] { shaft, border, bottom }) {
            build(s);
            int before = mismatches(s);
            t.info(s.name + ": " + before + " cells differ from the reference right after building (before settling)");
            int passes = settle(s);
            t.info(s.name + ": settled in " + passes + " pass(es)");
            compare(s, "after build");
        }

        // Пересчёт на maxY: закрыть шахту сверху, затем открыть
        step(shaft, "cap at maxY placed", 50, range.maxY, 50, Blocks.stone);
        step(shaft, "cap at maxY removed", 50, range.maxY, 50, Blocks.air);
        // Пересчёт на границе секций y = 0: перекрыть туннель, затем открыть
        step(shaft, "tunnel blocked at y=0", 53, 0, 50, Blocks.stone);
        step(shaft, "tunnel reopened at y=0", 53, 0, 50, Blocks.air);
        // Источник света через границу чанков: перенести в другой чанк
        step(border, "glowstone removed at y=-200", 14, -200, 43, Blocks.air);
        step(border, "glowstone placed in the next chunk", 18, -198, 44, Blocks.glowstone);
        // Пересчёт на minY
        int b = range.minY;
        step(bottom, "glowstone removed at minY", 33, b, 43, Blocks.air);
        step(bottom, "glowstone placed above minY", 31, b + 2, 45, Blocks.glowstone);
        step(bottom, "stone placed at minY", 32, b, 44, Blocks.stone);
        step(bottom, "stone removed at minY", 32, b, 44, Blocks.air);

        // Два тиковых прохода не должны портить правильный свет
        Structure[] all = { shaft, border, bottom };
        relightChecks(all);
        lightPopulation(all);

        heightMaps();
        writeExpected(all);
    }

    // ---- фаза verify ----

    void runVerify() throws IOException {
        File file = new File(EXPECTED_FILE);
        if (!file.isFile()) {
            t.check("light snapshot from the write phase exists", false, file.getAbsolutePath());
            return;
        }
        // getSavedLightValue / getHeightValue не грузят чанки: на незагруженном — значения по умолчанию
        loadChunks(-1, -1, 5, 5);
        int total = 0, differing = 0;
        List<String> diffs = new ArrayList<>();
        for (String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
            String[] p = line.split(" ");
            String now = null;
            if (p[0].equals("L")) {
                int x = Integer.parseInt(p[1]), y = Integer.parseInt(p[2]), z = Integer.parseInt(p[3]);
                int sky = world.getSavedLightValue(EnumSkyBlock.Sky, x, y, z);
                int block = world.getSavedLightValue(EnumSkyBlock.Block, x, y, z);
                if (sky != Integer.parseInt(p[4]) || block != Integer.parseInt(p[5])) {
                    now = sky + " " + block + " block=" + Block.blockRegistry.getNameForObject(world.getBlock(x, y, z));
                }
            } else if (p[0].equals("H")) {
                int x = Integer.parseInt(p[1]), z = Integer.parseInt(p[2]);
                if (world.getHeightValue(x, z) != Integer.parseInt(p[3])) {
                    now = String.valueOf(world.getHeightValue(x, z));
                }
            }
            total++;
            if (now != null) {
                differing++;
                if (diffs.size() < 10) diffs.add("[" + line + " -> now " + now + "]");
            }
        }
        t.check(
            "light values and height maps survive save/load (" + total + " values)",
            differing == 0 && total > 0,
            differing + " differ: " + String.join(" ", diffs));
        for (Structure s : new Structure[] { shaft(), borderBox(), bottomBox() }) {
            compare(s, "after reload");
        }
        t.check(
            "column at y=-100 keeps negative height after reload",
            world.getHeightValue(60, 60) == -99,
            world.getHeightValue(60, 60));
        t.check(
            "empty column height is minY after reload",
            world.getHeightValue(61, 61) == range.minY,
            world.getHeightValue(61, 61));
    }

    // ---- тиковые проходы ----

    private Set<Chunk> chunksOf(Structure[] structures) {
        Set<Chunk> chunks = new LinkedHashSet<>();
        for (Structure s : structures) {
            for (int[] c : s.cavity) {
                chunks.add(world.getChunkFromBlockCoords(c[0], c[2]));
            }
        }
        return chunks;
    }

    /** Chunk.enqueueRelightChecks по всему чанку (секции * 256 проверок, 8 за вызов) */
    private void relightChecks(Structure[] structures) {
        int calls = range.sectionCount() * 256 / 8 + 1;
        for (Chunk chunk : chunksOf(structures)) {
            chunk.resetRelightChecks();
            for (int i = 0; i < calls; i++) {
                chunk.enqueueRelightChecks();
            }
        }
        for (Structure s : structures) {
            compare(s, "after a full round of relight checks");
        }
    }

    /** Chunk.func_150809_p: проход «свет заселён» раз на чанк после заселения */
    private void lightPopulation(Structure[] structures) {
        int populated = 0;
        Set<Chunk> chunks = chunksOf(structures);
        for (Chunk chunk : chunks) {
            chunk.isTerrainPopulated = true;
            chunk.isLightPopulated = false;
            chunk.func_150809_p();
            if (chunk.isLightPopulated) populated++;
        }
        t.check(
            "light population pass completes for " + chunks.size() + " chunks",
            populated == chunks.size(),
            populated);
        for (Structure s : structures) {
            compare(s, "after the light population pass");
        }
    }

    // ---- карта высот и осадки ----

    private void heightMaps() {
        int min = range.minY;
        expectColumn("empty column", 61, 61, min);

        world.setBlock(62, -200, 62, Blocks.stone, 0, 3);
        expectColumn("block at y=-200", 62, 62, -199);
        world.setBlock(62, min, 62, Blocks.stone, 0, 3);
        expectColumn("blocks at -200 and minY", 62, 62, -199);
        world.setBlock(62, -200, 62, Blocks.air, 0, 3);
        // Ваниль не сканирует нижний ряд для осадков; причуда сохранена: осадки minY, карта высот minY + 1
        expectColumn("-200 removed, block at minY left", 62, 62, min + 1, min);
        world.setBlock(62, min, 62, Blocks.air, 0, 3);
        expectColumn("column emptied again", 62, 62, min);

        world.setBlock(63, range.maxY, 63, Blocks.stone, 0, 3);
        expectColumn("block at maxY", 63, 63, range.maxY + 1);
        t.check(
            "sky light under a block at maxY is 14 (lit from the sides)",
            world.getSavedLightValue(EnumSkyBlock.Sky, 63, range.maxY - 1, 63) == 14,
            world.getSavedLightValue(EnumSkyBlock.Sky, 63, range.maxY - 1, 63));
        world.setBlock(63, range.maxY, 63, Blocks.air, 0, 3);
        expectColumn("block at maxY removed", 63, 63, min);

        world.setBlock(63, 0, 62, Blocks.stone, 0, 3);
        expectColumn("block at y=0", 63, 62, 1);
        world.setBlock(63, -1, 62, Blocks.stone, 0, 3);
        world.setBlock(63, 0, 62, Blocks.air, 0, 3);
        expectColumn("block at y=0 removed, y=-1 left", 63, 62, 0);

        // Оставлено для проверки после перезагрузки
        world.setBlock(60, -100, 60, Blocks.stone, 0, 3);
        expectColumn("block at y=-100 (kept for reload)", 60, 60, -99);
    }

    private void expectColumn(String what, int x, int z, int height) {
        expectColumn(what, x, z, height, height);
    }

    private void expectColumn(String what, int x, int z, int height, int precipitation) {
        int h = world.getHeightValue(x, z);
        int p = world.getPrecipitationHeight(x, z);
        Chunk chunk = world.getChunkFromBlockCoords(x, z);
        int stored = chunk.precipitationHeightMap[(x & 15) | (z & 15) << 4];
        t.check("heightMap, " + what + ": " + height, h == height, h);
        t.check("precipitation height, " + what + ": " + precipitation, p == precipitation, p);
        t.check("precipitationHeightMap cache, " + what, stored == p, stored);
        t.check("height map equals a column scan, " + what, h == scanHeight(x, z), scanHeight(x, z));
    }

    // ---- постройка, установка, сравнение ----

    private void loadChunks(int cx0, int cz0, int cx1, int cz1) {
        for (int cx = cx0; cx <= cx1; cx++) for (int cz = cz0; cz <= cz1; cz++) {
            world.getChunkFromChunkCoords(cx, cz);
        }
    }

    private void build(Structure s) {
        for (int[] c : s.shell) {
            world.setBlock(c[0], c[1], c[2], Blocks.stone, 0, 3);
        }
        for (int[] c : s.cavity) {
            Block source = s.sources.get(LightReference.key(c[0], c[1], c[2]));
            world.setBlock(c[0], c[1], c[2], source != null ? source : Blocks.air, 0, 3);
        }
    }

    /** Гоняет updateLightByType, пока что-то меняется; возвращает число проходов */
    private int settle(Structure s) {
        List<int[]> all = new ArrayList<>(s.cavity);
        all.addAll(s.shell);
        for (int pass = 1; pass <= 20; pass++) {
            List<Integer> before = snapshot(all);
            for (int[] c : all) {
                world.updateLightByType(EnumSkyBlock.Sky, c[0], c[1], c[2]);
                world.updateLightByType(EnumSkyBlock.Block, c[0], c[1], c[2]);
            }
            if (before.equals(snapshot(all))) {
                return pass;
            }
        }
        t.check(s.name + " light settles within 20 passes", false, "still changing");
        return 20;
    }

    private List<Integer> snapshot(List<int[]> cells) {
        List<Integer> out = new ArrayList<>(cells.size() * 2);
        for (int[] c : cells) {
            out.add(world.getSavedLightValue(EnumSkyBlock.Sky, c[0], c[1], c[2]));
            out.add(world.getSavedLightValue(EnumSkyBlock.Block, c[0], c[1], c[2]));
        }
        return out;
    }

    /** Меняет один блок через World.setBlock и сравнивает без установки */
    private void step(Structure s, String what, int x, int y, int z, Block block) {
        world.setBlock(x, y, z, block, 0, 3);
        compare(s, what);
    }

    private int mismatches(Structure s) {
        int n = 0;
        for (boolean sky : new boolean[] { true, false }) {
            Map<Long, Integer> ref = LightReference.compute(s.cavity, adapter(), sky);
            for (int[] c : s.cavity) {
                if (game(c, sky) != ref.get(LightReference.key(c[0], c[1], c[2]))) n++;
            }
        }
        return n;
    }

    private void compare(Structure s, String when) {
        for (boolean sky : new boolean[] { true, false }) {
            Map<Long, Integer> ref = LightReference.compute(s.cavity, adapter(), sky);
            int bad = 0;
            String first = null;
            for (int[] c : s.cavity) {
                int expected = ref.get(LightReference.key(c[0], c[1], c[2]));
                int actual = game(c, sky);
                if (actual != expected) {
                    bad++;
                    if (first == null) {
                        first = c[0] + "," + c[1] + "," + c[2] + " game=" + actual + " ref=" + expected;
                    }
                }
            }
            t.check(
                s.name + ", "
                    + when
                    + ": "
                    + (sky ? "sky" : "block")
                    + " light equals the BFS reference ("
                    + s.cavity.size()
                    + " cells)",
                bad == 0,
                bad + " differ, first " + first);
        }
        int lit = 0;
        String firstLit = null;
        for (int[] c : s.shell) {
            if (game(c, true) != 0 || game(c, false) != 0) {
                lit++;
                if (firstLit == null) firstLit = c[0] + "," + c[1] + "," + c[2];
            }
        }
        t.check(
            s.name + ", " + when + ": stone shell is dark (" + s.shell.size() + " cells)",
            lit == 0,
            lit + " lit, first " + firstLit);
        Set<Long> columns = new LinkedHashSet<>();
        int badColumns = 0;
        String firstColumn = null;
        for (int[] c : s.cavity) {
            if (columns.add(LightReference.key(c[0], 0, c[2]))) {
                int h = world.getHeightValue(c[0], c[2]);
                if (h != scanHeight(c[0], c[2])) {
                    badColumns++;
                    if (firstColumn == null)
                        firstColumn = c[0] + "," + c[2] + " game=" + h + " scan=" + scanHeight(c[0], c[2]);
                }
            }
        }
        t.check(
            s.name + ", " + when + ": height map of " + columns.size() + " columns equals a column scan",
            badColumns == 0,
            badColumns + " differ, first " + firstColumn);
    }

    private int game(int[] c, boolean sky) {
        return world.getSavedLightValue(sky ? EnumSkyBlock.Sky : EnumSkyBlock.Block, c[0], c[1], c[2]);
    }

    /** Независимая карта высот: y + 1 верхнего непрозрачного блока или minY */
    private int scanHeight(int x, int z) {
        for (int y = range.maxY; y >= range.minY; y--) {
            if (world.getBlock(x, y, z)
                .getLightOpacity(world, x, y, z) > 0) {
                return y + 1;
            }
        }
        return range.minY;
    }

    private LightReference.World adapter() {
        Map<Long, Integer> heights = new HashMap<>();
        return new LightReference.World() {

            @Override
            public int opacity(int x, int y, int z) {
                return world.getBlock(x, y, z)
                    .getLightOpacity(world, x, y, z);
            }

            @Override
            public int emission(int x, int y, int z) {
                return world.getBlock(x, y, z)
                    .getLightValue(world, x, y, z);
            }

            @Override
            public boolean seesSky(int x, int y, int z) {
                return y >= heights.computeIfAbsent(LightReference.key(x, 0, z), k -> scanHeight(x, z));
            }
        };
    }

    private void writeExpected(Structure[] structures) throws IOException {
        try (PrintWriter out = new PrintWriter(new File(EXPECTED_FILE), "UTF-8")) {
            Set<Long> columns = new LinkedHashSet<>();
            for (Structure s : structures) {
                for (int[] c : s.cavity) {
                    out.println("L " + c[0] + " " + c[1] + " " + c[2] + " " + game(c, true) + " " + game(c, false));
                    if (columns.add(LightReference.key(c[0], 0, c[2]))) {
                        out.println("H " + c[0] + " " + c[2] + " " + world.getHeightValue(c[0], c[2]));
                    }
                }
            }
            for (int[] col : new int[][] { { 60, 60 }, { 61, 61 }, { 62, 62 }, { 63, 63 }, { 63, 62 } }) {
                out.println("H " + col[0] + " " + col[1] + " " + world.getHeightValue(col[0], col[1]));
            }
        }
    }
}
