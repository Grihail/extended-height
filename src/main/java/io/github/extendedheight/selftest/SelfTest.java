package io.github.extendedheight.selftest;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.TreeMap;
import java.util.TreeSet;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.world.MinecraftException;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.storage.RegionFile;
import net.minecraft.world.chunk.storage.RegionFileCache;
import net.minecraft.world.storage.SaveHandler;
import net.minecraft.world.storage.ThreadedFileIOBase;
import net.minecraftforge.common.DimensionManager;

import io.github.extendedheight.ExtendedHeightMod;
import io.github.extendedheight.api.ExtendedHeight;
import io.github.extendedheight.api.HeightRange;
import io.github.extendedheight.core.IExtendedHeightWorld;
import io.github.extendedheight.core.MixinPlugin;

/** Серверный самотест для scripts/selftest.ps1: фазы baseline, vanilla-mod, write, verify */
public final class SelfTest {

    public static final String CONFIG_FILE = "extendedheight-selftest.properties";
    public static final int EXT_DIM = 4242;
    public static final int LATE_DIM = 4244;
    static final HeightRange EXT_RANGE = HeightRange.of(-256, 319);

    /** y -> цвет шерсти в расширенном измерении (x = 8..16, z = 8) */
    static final int[] EXT_YS = { -256, -255, -200, -65, -64, -17, -16, -1, 0, 15, 16, 255, 256, 300, 318, 319 };
    /** Второй чанк далеко от первого, тоже в отрицательных x/z */
    static final int[] FAR_XZ = { -37, -53 };
    static final int CHEST_Y = -100;
    static final int LONE_X = 3, LONE_Z = 3, LONE_Y = -200;

    private static Properties config;

    private final MinecraftServer server;
    private final String phase;
    private final List<String> lines = new ArrayList<>();
    private boolean failed;

    private SelfTest(MinecraftServer server, String phase) {
        this.server = server;
        this.phase = phase;
    }

    public static boolean isActive() {
        return config() != null;
    }

    /** Фазы под управлением клиента: client-audit, net-client и net-nomod */
    public static boolean isClientPhase() {
        String p = phase();
        return "client-audit".equals(p) || "net-client".equals(p) || "net-nomod".equals(p);
    }

    /** Фаза net-nomod: клиент скрывает мод в рукопожатии FML */
    public static boolean hidesModInHandshake() {
        return "net-nomod".equals(phase());
    }

    public static String phase() {
        return config() == null ? null : config().getProperty("phase");
    }

    private static Properties config() {
        if (config == null) {
            File file = new File(CONFIG_FILE);
            if (!file.isFile()) {
                return null;
            }
            Properties p = new Properties();
            try (FileReader reader = new FileReader(file)) {
                p.load(reader);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            config = p;
        }
        return config;
    }

    /** Вызывается в pre-init: регистрирует тестовые измерения; позднее — только в verify */
    public static void preInit() {
        String phase = phase();
        if (!"write".equals(phase) && !"verify".equals(phase)) {
            return;
        }
        for (int dim : new int[] { EXT_DIM, LATE_DIM }) {
            DimensionManager.registerProviderType(dim, SelfTestWorldProvider.class, false);
            DimensionManager.registerDimension(dim, dim);
        }
        ExtendedHeight.register(EXT_DIM, EXT_RANGE.minY, EXT_RANGE.maxY);
        if ("verify".equals(phase)) {
            // Имитирует регистрацию после создания измерения ванильным
            ExtendedHeight.register(LATE_DIM, EXT_RANGE.minY, EXT_RANGE.maxY);
        }
    }

    /** Вызывается при старте сервера: выполняет фазу, пишет отчёт и останавливает сервер */
    public static void serverStarted(MinecraftServer server) {
        SelfTest test = new SelfTest(server, phase());
        if ("net-server".equals(phase())) {
            // Ждёт сетевых клиентов; отчёт и остановку делает NetServerSelfTest
            test.environment();
            Throwable error = MixinAudit.run();
            test.check("mixin audit (all targets found, all injections applied)", error == null, error);
            NetServerSelfTest.start(test, server);
            return;
        }
        try {
            test.run();
        } catch (Throwable t) {
            ExtendedHeightMod.LOG.error("Self-test crashed", t);
            test.fail("no exception", t.toString());
        }
        test.writeReport();
        server.initiateShutdown();
    }

    /** Клиентская проверка client-audit: применяет все миксины клиента и пишет отчёт */
    public static void clientAudit() {
        SelfTest test = new SelfTest(null, phase());
        test.environment();
        Throwable error = MixinAudit.run();
        test.check("mixin audit (all targets found, all injections applied)", error == null, error);
        if (error != null) {
            ExtendedHeightMod.LOG.error("Mixin audit failed", error);
        }
        // Продолжение по клиентским тикам: мир (или выделенный сервер), затем отчёт и закрытие игры
        if (phase().startsWith("net-")) {
            NetClientSelfTest.start(test, config().getProperty("server"), "net-nomod".equals(phase()));
        } else {
            ClientSelfTest.start(test);
        }
    }

    private void environment() {
        Object deobf = net.minecraft.launchwrapper.Launch.blackboard.get("fml.deobfuscatedEnvironment");
        log("environment: " + (Boolean.TRUE.equals(deobf) ? "dev (deobfuscated)" : "OBFUSCATED (production-like)"));
    }

    private void run() throws Exception {
        log("phase " + phase);
        environment();
        if (!MixinPlugin.mixinsDisabled()) {
            Throwable error = MixinAudit.run();
            check("mixin audit (all targets found, all injections applied)", error == null, error);
            if (error != null) {
                ExtendedHeightMod.LOG.error("Mixin audit failed", error);
            }
        }
        switch (phase) {
            case "baseline":
            case "vanilla-mod":
                // Без этого побайтовое сравнение ничего не докажет, если миксины не применились
                boolean mixed = server.worldServerForDimension(0) instanceof IExtendedHeightWorld;
                check(
                    "mixins " + ("baseline".equals(phase) ? "absent" : "applied") + " to World",
                    mixed != "baseline".equals(phase),
                    mixed);
                editOverworld();
                saveAll();
                snapshotAndCompare(true);
                // После снимка: бенчмарк правит измерение 0
                benchOverworld();
                break;
            case "write":
                editOverworld();
                checkOverworldStaysVanilla();
                writeExtended();
                writeLateDimension();
                new LightTests(this, extWorld()).runWrite();
                saveAll();
                snapshotAndCompare(false);
                checkSavedSections();
                checkLevelDat();
                benchOverworld();
                benchExtended();
                break;
            case "verify":
                verifyExtended();
                verifyLateDimension();
                new LightTests(this, extWorld()).runVerify();
                checkSavedSections();
                checkLevelDat();
                break;
            default:
                fail("known phase", phase);
        }
    }

    // ---- измерение 0 (ванильное) ----

    /** Одинаково во всех фазах, чтобы чанки сравнивались побайтно */
    private void editOverworld() {
        WorldServer world = server.worldServerForDimension(0);
        int[] ys = { 1, 4, 64, 128, 255 };
        for (int i = 0; i < ys.length; i++) {
            world.setBlock(i, ys[i], 0, Blocks.wool, i + 1, 3);
        }
        world.setBlock(-20, 10, -20, Blocks.glowstone, 0, 3);
    }

    private void checkOverworldStaysVanilla() {
        WorldServer world = server.worldServerForDimension(0);
        check(
            "overworld range is vanilla",
            ExtendedHeight.getRange(world)
                .isVanilla(),
            ExtendedHeight.getRange(world));
        check("overworld setBlock(y=-1) rejected", !world.setBlock(0, -1, 0, Blocks.stone, 0, 3), null);
        check("overworld setBlock(y=256) rejected", !world.setBlock(0, 256, 0, Blocks.stone, 0, 3), null);
        check("overworld getBlock(y=-1) is air", world.getBlock(0, -1, 0) == Blocks.air, world.getBlock(0, -1, 0));
        check("overworld getHeight() == 256", world.getHeight() == 256, world.getHeight());
    }

    // ---- расширенное измерение ----

    private WorldServer extWorld() {
        return server.worldServerForDimension(EXT_DIM);
    }

    private void checkExtRange(WorldServer world) {
        HeightRange range = ExtendedHeight.getRange(world);
        check("extended dim range == " + EXT_RANGE, EXT_RANGE.equals(range), range);
        check("extended dim getHeight() == 320", world.getHeight() == 320, world.getHeight());
        check("extended dim getActualHeight() == 320", world.getActualHeight() == 320, world.getActualHeight());
        check(
            "chunk storage has 36 sections",
            world.getChunkFromChunkCoords(0, 0)
                .getBlockStorageArray().length == 36,
            world.getChunkFromChunkCoords(0, 0)
                .getBlockStorageArray().length);
    }

    private void writeExtended() {
        WorldServer world = extWorld();
        checkExtRange(world);
        for (int i = 0; i < EXT_YS.length; i++) {
            int y = EXT_YS[i];
            boolean set = world.setBlock(8 + (i % 8), y, 8 + i / 8, Blocks.wool, i % 16, 3);
            check("setBlock y=" + y, set, null);
            boolean setFar = world.setBlock(FAR_XZ[0], y, FAR_XZ[1], Blocks.stained_hardened_clay, i % 16, 3);
            check("setBlock far chunk y=" + y, setFar, null);
        }
        world.setBlock(LONE_X, LONE_Y, LONE_Z, Blocks.stone, 0, 3);

        world.setBlock(1, CHEST_Y, 1, Blocks.chest, 0, 3);
        TileEntity te = world.getTileEntity(1, CHEST_Y, 1);
        check("chest tile entity created at y=" + CHEST_Y, te instanceof TileEntityChest, te);
        if (te instanceof TileEntityChest) {
            ((TileEntityChest) te).setInventorySlotContents(0, new ItemStack(Items.diamond, 7));
        }

        checkExtendedContents(world, "immediately");
        checkOutOfRange(world);
        check(
            "getTopSolidOrLiquidBlock on a lone block at y=" + LONE_Y,
            world.getTopSolidOrLiquidBlock(LONE_X, LONE_Z) == LONE_Y + 1,
            world.getTopSolidOrLiquidBlock(LONE_X, LONE_Z));
        check(
            "getPrecipitationHeight on a lone block at y=" + LONE_Y,
            world.getPrecipitationHeight(LONE_X, LONE_Z) == LONE_Y + 1,
            world.getPrecipitationHeight(LONE_X, LONE_Z));
        check(
            "getTopSolidOrLiquidBlock on an empty column == minY - 1",
            world.getTopSolidOrLiquidBlock(5, 13) == EXT_RANGE.minY - 1,
            world.getTopSolidOrLiquidBlock(5, 13));
    }

    private void verifyExtended() {
        WorldServer world = extWorld();
        checkExtRange(world);
        checkExtendedContents(world, "after restart");
        checkOutOfRange(world);
        TileEntity te = world.getTileEntity(1, CHEST_Y, 1);
        check("chest at y=" + CHEST_Y + " survives restart", te instanceof TileEntityChest, te);
        if (te instanceof TileEntityChest) {
            ItemStack stack = ((TileEntityChest) te).getStackInSlot(0);
            check(
                "chest content survives restart",
                stack != null && stack.getItem() == Items.diamond && stack.stackSize == 7,
                stack);
        }
        check(
            "lone block at y=" + LONE_Y + " survives restart",
            world.getBlock(LONE_X, LONE_Y, LONE_Z) == Blocks.stone,
            world.getBlock(LONE_X, LONE_Y, LONE_Z));
    }

    private void checkExtendedContents(WorldServer world, String when) {
        for (int i = 0; i < EXT_YS.length; i++) {
            int y = EXT_YS[i];
            int x = 8 + (i % 8), z = 8 + i / 8;
            Block block = world.getBlock(x, y, z);
            int meta = world.getBlockMetadata(x, y, z);
            check(
                "block y=" + y + " " + when,
                block == Blocks.wool && meta == i % 16,
                Block.blockRegistry.getNameForObject(block) + ":" + meta);
            Block far = world.getBlock(FAR_XZ[0], y, FAR_XZ[1]);
            int farMeta = world.getBlockMetadata(FAR_XZ[0], y, FAR_XZ[1]);
            check(
                "far block y=" + y + " " + when,
                far == Blocks.stained_hardened_clay && farMeta == i % 16,
                Block.blockRegistry.getNameForObject(far) + ":" + farMeta);
        }
        // Соседи записанных клеток должны остаться воздухом: ловит путаницу индексов секций
        for (int y : new int[] { -240, -48, 32, 240, 288 }) {
            check(
                "untouched y=" + y + " is air " + when,
                world.getBlock(8, y, 8) == Blocks.air,
                world.getBlock(8, y, 8));
        }
    }

    private void checkOutOfRange(WorldServer world) {
        check("setBlock(y=-257) rejected", !world.setBlock(0, -257, 0, Blocks.stone, 0, 3), null);
        check("setBlock(y=320) rejected", !world.setBlock(0, 320, 0, Blocks.stone, 0, 3), null);
        check("getBlock(y=-257) is air", world.getBlock(0, -257, 0) == Blocks.air, null);
        check("getBlock(y=320) is air", world.getBlock(0, 320, 0) == Blocks.air, null);
        check("blockExists(y=-256)", world.blockExists(0, -256, 0), null);
        check("blockExists(y=319)", world.blockExists(0, 319, 0), null);
        check("!blockExists(y=-257)", !world.blockExists(0, -257, 0), null);
        check("!blockExists(y=320)", !world.blockExists(0, 320, 0), null);
        // Коробка внутри загруженного чанка 0,0; ваниль отвергает коробки целиком ниже y 0
        check("checkChunksExist for a box around y=-200", world.checkChunksExist(1, -217, 1, 14, -183, 14), null);
        check("!checkChunksExist for a box below minY", !world.checkChunksExist(1, -300, 1, 14, -260, 14), null);
    }

    // ---- поздняя регистрация ----

    private void writeLateDimension() {
        WorldServer world = server.worldServerForDimension(LATE_DIM);
        check(
            "late dim is vanilla when not requested",
            ExtendedHeight.getRange(world)
                .isVanilla(),
            null);
        world.setBlock(0, 10, 0, Blocks.gold_block, 0, 3);
    }

    private void verifyLateDimension() {
        WorldServer world = server.worldServerForDimension(LATE_DIM);
        check(
            "late registration does not extend a dimension with saved chunks",
            ExtendedHeight.getRange(world)
                .isVanilla(),
            ExtendedHeight.getRange(world));
        check("late dim block survives", world.getBlock(0, 10, 0) == Blocks.gold_block, world.getBlock(0, 10, 0));
    }

    // ---- сохранённые данные ----

    private void saveAll() throws MinecraftException, InterruptedException {
        for (WorldServer world : server.worldServers) {
            world.saveAllChunks(true, null);
        }
        ThreadedFileIOBase.threadedIOInstance.waitForFinish();
    }

    private File worldDir() {
        return ((SaveHandler) server.worldServerForDimension(0)
            .getSaveHandler()).getWorldDirectory();
    }

    /** Теги Y сохранённых секций двух записанных чанков расширенного измерения */
    private void checkSavedSections() throws IOException {
        File dimDir = new File(worldDir(), "DIM" + EXT_DIM);
        TreeSet<Integer> expected = new TreeSet<>();
        for (int y : EXT_YS) {
            expected.add(y >> 4);
        }
        int[][] chunks = { { 0, 0 }, { FAR_XZ[0] >> 4, FAR_XZ[1] >> 4 } };
        for (int[] c : chunks) {
            NBTTagCompound level = readChunkNbt(dimDir, c[0], c[1]);
            if (level == null) {
                fail("chunk " + c[0] + "," + c[1] + " saved", "missing");
                continue;
            }
            TreeSet<Integer> ys = new TreeSet<>();
            NBTTagList sections = level.getTagList("Sections", 10);
            for (int i = 0; i < sections.tagCount(); i++) {
                ys.add(
                    (int) sections.getCompoundTagAt(i)
                        .getByte("Y"));
            }
            check(
                "chunk " + c[0] + "," + c[1] + " saved section Y tags " + expected,
                ys.containsAll(expected) && ys.first() < 0 && ys.last() > 15,
                ys);
        }
    }

    private static NBTTagCompound readChunkNbt(File dimDir, int cx, int cz) throws IOException {
        DataInputStream in = RegionFileCache.getChunkInputStream(dimDir, cx, cz);
        if (in == null) {
            return null;
        }
        try {
            return CompressedStreamTools.read(in)
                .getCompoundTag("Level");
        } finally {
            in.close();
        }
    }

    private void checkLevelDat() throws IOException {
        NBTTagCompound data = readLevelDat().getCompoundTag("Data");
        boolean has = data.hasKey("ExtendedHeight", 9);
        check("level.dat has ExtendedHeight", has, null);
        if (!has) {
            return;
        }
        NBTTagList list = data.getTagList("ExtendedHeight", 10);
        List<String> entries = new ArrayList<>();
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound e = list.getCompoundTagAt(i);
            entries.add(e.getInteger("Dim") + ":" + e.getInteger("MinY") + ".." + e.getInteger("MaxY"));
        }
        check(
            "level.dat ExtendedHeight lists exactly the extended dim",
            entries.equals(Arrays.asList(EXT_DIM + ":" + EXT_RANGE.minY + ".." + EXT_RANGE.maxY)),
            entries);
    }

    private NBTTagCompound readLevelDat() throws IOException {
        try (InputStream in = new FileInputStream(new File(worldDir(), "level.dat"))) {
            return CompressedStreamTools.readCompressed(in);
        }
    }

    /** Пишет NBT чанков измерения 0 и level.dat без LastPlayed, сравнивает со снимком compareWith */
    private void snapshotAndCompare(boolean includeLevelDat) throws IOException {
        String out = config().getProperty("snapshot");
        if (out == null) {
            return;
        }
        File snapshot = new File(out);
        snapshot.mkdirs();
        TreeMap<String, byte[]> files = new TreeMap<>();
        File regionDir = new File(worldDir(), "region");
        File[] regions = regionDir.listFiles((dir, name) -> name.endsWith(".mca"));
        for (File region : regions == null ? new File[0] : regions) {
            String[] parts = region.getName()
                .split("\\.");
            int rx = Integer.parseInt(parts[1]), rz = Integer.parseInt(parts[2]);
            RegionFile file = RegionFileCache.createOrLoadRegionFile(worldDir(), rx << 5, rz << 5);
            for (int x = 0; x < 32; x++) {
                for (int z = 0; z < 32; z++) {
                    if (!file.chunkExists(x, z)) {
                        continue;
                    }
                    try (DataInputStream in = file.getChunkDataInputStream(x, z)) {
                        files.put("chunk." + ((rx << 5) + x) + "." + ((rz << 5) + z) + ".nbt", readAll(in));
                    }
                }
            }
        }
        NBTTagCompound level = readLevelDat();
        level.getCompoundTag("Data")
            .removeTag("LastPlayed");
        ByteArrayOutputStream levelBytes = new ByteArrayOutputStream();
        CompressedStreamTools.write(level, new DataOutputStream(levelBytes));
        files.put("level.dat.nbt", levelBytes.toByteArray());
        for (java.util.Map.Entry<String, byte[]> e : files.entrySet()) {
            Files.write(new File(snapshot, e.getKey()).toPath(), e.getValue());
        }
        log("snapshot: " + files.size() + " files in " + snapshot.getAbsolutePath());
        check("overworld snapshot not empty", files.size() > 100, files.size());

        String compareWith = config().getProperty("compareWith");
        if (compareWith == null) {
            return;
        }
        File reference = new File(compareWith);
        String[] refNames = reference.list();
        TreeSet<String> names = new TreeSet<>(refNames == null ? new ArrayList<>() : Arrays.asList(refNames));
        if (!includeLevelDat) {
            names.remove("level.dat.nbt");
        }
        TreeSet<String> ours = new TreeSet<>(files.keySet());
        if (!includeLevelDat) {
            ours.remove("level.dat.nbt");
        }
        check(
            "same set of saved overworld chunks as baseline",
            names.equals(ours),
            ours.size() + " vs " + names.size());
        int differing = 0;
        String firstDiff = null;
        for (String name : names) {
            byte[] mine = files.get(name);
            byte[] theirs = Files.readAllBytes(new File(reference, name).toPath());
            if (mine == null || !Arrays.equals(mine, theirs)) {
                differing++;
                if (firstDiff == null) {
                    firstDiff = name;
                }
            }
        }
        check(
            "overworld " + (includeLevelDat ? "chunks + level.dat" : "chunks")
                + " byte-identical to baseline ("
                + names.size()
                + " files)",
            differing == 0,
            differing + " differ, first: " + firstDiff);
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    // ---- бенчмарк ----

    /** Одна область и высоты во всех фазах для сравнимости */
    private void benchOverworld() {
        WorldServer world = server.worldServerForDimension(0);
        bench("overworld getBlock y 0..255", Bench.getBlock(world, 0, 0, 0, 255));
        bench("overworld setBlock y=100", Bench.setBlock(world, 4, 4, 100));
    }

    private void benchExtended() {
        WorldServer world = extWorld();
        bench("extended getBlock y 0..255", Bench.getBlock(world, 12, 12, 0, 255));
        bench("extended getBlock y -256..319", Bench.getBlock(world, 12, 12, -256, 319));
        bench("extended setBlock y=100", Bench.setBlock(world, 16, 16, 100));
        bench("extended setBlock y=-200", Bench.setBlock(world, 16, 16, -200));
    }

    /** result = {медиана, минимум} в нс на операцию */
    private void bench(String name, double[] result) {
        lines.add(
            String.format(
                java.util.Locale.ROOT,
                "BENCH %s: median %.2f ns/op, min %.2f ns/op",
                name,
                result[0],
                result[1]));
    }

    // ---- отчёт ----

    void info(String message) {
        log(message);
    }

    void check(String name, boolean ok, Object actual) {
        if (ok) {
            lines.add("PASS " + name);
        } else {
            fail(name, String.valueOf(actual));
        }
    }

    private void fail(String name, String actual) {
        failed = true;
        lines.add("FAIL " + name + " (actual: " + actual + ")");
    }

    private void log(String message) {
        lines.add("INFO " + message);
    }

    void writeReport() {
        lines.add("OVERALL: " + (failed ? "FAIL" : "PASS"));
        String path = config().getProperty("result", "selftest-result.txt");
        try (PrintWriter out = new PrintWriter(path, "UTF-8")) {
            for (String line : lines) {
                out.println(line);
            }
        } catch (IOException e) {
            ExtendedHeightMod.LOG.error("Cannot write self-test report", e);
        }
        for (String line : lines) {
            ExtendedHeightMod.LOG.info("[selftest] {}", line);
        }
    }
}
