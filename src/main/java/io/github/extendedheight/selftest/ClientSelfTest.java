package io.github.extendedheight.selftest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraft.world.chunk.Chunk;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.internal.FMLNetworkHandler;
import cpw.mods.fml.relauncher.Side;
import io.github.extendedheight.ExtendedHeightMod;
import io.github.extendedheight.api.ExtendedHeight;
import io.github.extendedheight.api.HeightRange;
import io.github.extendedheight.client.VoidStats;
import io.github.extendedheight.core.EhConfig;
import io.github.extendedheight.mixins.early.client.EntityRendererAccessor;
import io.github.extendedheight.net.ChunkCodec;
import io.github.extendedheight.net.ExtPackets;
import io.github.extendedheight.world.WorldTypeExtendedTest;

/** Клиентская часть самотеста client-audit: ванильный FLAT и мир extendedtest, сверка блоков и пакетов */
public final class ClientSelfTest {

    private static final int STEP_TIMEOUT_TICKS = 20 * 120;
    private static final int SETTLE_TICKS = 40;

    private final SelfTest report;
    private final ConcurrentLinkedQueue<Runnable> serverTasks = new ConcurrentLinkedQueue<>();
    private int step;
    private int stepTicks;
    private int ticks;
    private boolean done;

    /** Позиция игрока в расширенном мире на старте проверок */
    private int bx, bz;
    /** Ожидаемые блоки сервера после правок: "x,y,z" -> блок */
    private final Map<String, Block> expected = new HashMap<>();
    private ClientEntityTests entities;

    private ClientSelfTest(SelfTest report) {
        this.report = report;
    }

    /** Вызывается по окончании загрузки, после записи аудита миксинов в report */
    static void start(SelfTest report) {
        ExtPackets.forceWireRoundTrip = true;
        // Окно теста может терять фокус — иначе ваниль поставит одиночную игру на паузу
        net.minecraft.client.Minecraft.getMinecraft().gameSettings.pauseOnLostFocus = false;
        FMLCommonHandler.instance()
            .bus()
            .register(new ClientSelfTest(report));
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Runnable task;
        while ((task = serverTasks.poll()) != null) {
            try {
                task.run();
            } catch (Throwable t) {
                ExtendedHeightMod.LOG.error("Self-test server task failed", t);
                report.check("server task", false, t);
            }
        }
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || done) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        ticks++;
        stepTicks++;
        if (stepTicks > STEP_TIMEOUT_TICKS) {
            report
                .check("client step " + step + " completed within " + STEP_TIMEOUT_TICKS / 20 + " s", false, "timeout");
            finish(mc);
            return;
        }
        try {
            if (runStep(mc)) {
                step++;
                stepTicks = 0;
            }
        } catch (Throwable t) {
            ExtendedHeightMod.LOG.error("Client self-test crashed in step {}", step, t);
            report.check("client step " + step + " without exception", false, t);
            finish(mc);
        }
    }

    private boolean inWorld(Minecraft mc) {
        return mc.theWorld != null && mc.thePlayer != null && mc.getIntegratedServer() != null;
    }

    /** @return true, когда шаг завершён */
    private boolean runStep(Minecraft mc) {
        switch (step) {
            case 0:
                if (mc.currentScreen == null || ticks < 20) {
                    return false;
                }
                staticChecks();
                report.info("opening a vanilla singleplayer world");
                mc.launchIntegratedServer(
                    "eh-selftest-" + System.currentTimeMillis(),
                    "eh-selftest",
                    new WorldSettings(20261018L, WorldSettings.GameType.CREATIVE, false, false, WorldType.FLAT));
                return true;
            case 1:
                if (!inWorld(mc) || stepTicks < SETTLE_TICKS) {
                    return false;
                }
                report.check("singleplayer world loaded, player in the world", true, null);
                report.check(
                    "client world is vanilla (no extended range requested)",
                    !ExtendedHeight.isExtended(mc.theWorld),
                    ExtendedHeight.getRange(mc.theWorld));
                report.check(
                    "integrated server overworld is vanilla",
                    !ExtendedHeight.isExtended(
                        mc.getIntegratedServer()
                            .worldServerForDimension(0)),
                    null);
                report.check("vanilla world: no extended packet sent or received", countersZero(), counters());
                leaveWorld(mc);
                return true;
            case 2:
                // Даём встроенному серверу первого мира остановиться перед следующим
                if (stepTicks < SETTLE_TICKS) {
                    return false;
                }
                report.info("opening an extendedtest singleplayer world");
                mc.launchIntegratedServer(
                    "eh-selftest-ext-" + System.currentTimeMillis(),
                    "eh-selftest-ext",
                    new WorldSettings(
                        20261018L,
                        WorldSettings.GameType.CREATIVE,
                        false,
                        false,
                        WorldTypeExtendedTest.instance()));
                return true;
            case 3:
                if (!inWorld(mc) || stepTicks < SETTLE_TICKS || !clientChunksAround(mc, 1)) {
                    return false;
                }
                checkExtendedWorldSynced(mc);
                scheduleServerEdits(mc);
                return true;
            case 4:
                if (stepTicks < SETTLE_TICKS) {
                    return false;
                }
                compareExpected(mc, "server edits reach the client (extended S23/S22/S21)");
                checkCounter("ExtBlockChange", ExtPackets.BLOCK, 2);
                checkCounter("ExtMultiBlockChange", ExtPackets.MULTI, 1);
                checkCounter("ExtChunkData (partial)", ExtPackets.CHUNK, 1);
                teleport(mc, bx, -100, bz, -101, true);
                return true;
            case 5:
                if (stepTicks < SETTLE_TICKS) {
                    return false;
                }
                checkPlayerAt(mc, -100);
                playerDigAndPlace(mc, -101);
                return true;
            case 6:
                if (stepTicks < SETTLE_TICKS) {
                    return false;
                }
                compareExpected(mc, "player dig/place at y -101/-100 (extended C07/C08)");
                teleport(mc, bx, 300, bz, 299, false);
                return true;
            case 7:
                if (stepTicks < SETTLE_TICKS) {
                    return false;
                }
                checkPlayerAt(mc, 300);
                playerDigAndPlace(mc, 299);
                return true;
            case 8:
                if (stepTicks < SETTLE_TICKS) {
                    return false;
                }
                compareExpected(mc, "player dig/place at y 299/300 (extended C07/C08)");
                checkCounter("ExtPlayerDigging", ExtPackets.DIGGING, 2);
                checkCounter("ExtBlockPlacement", ExtPackets.PLACEMENT, 2);
                entities = new ClientEntityTests(report, serverTasks, serverWorld(mc), bx + 40, bz);
                entities.buildRoom();
                return true;
            case 9:
                if (stepTicks < SETTLE_TICKS || !clientChunksAround(mc, 1)) {
                    return false;
                }
                checkPlayerAt(mc, ClientEntityTests.Y);
                checkVoidEffects(mc);
                entities.spawnEntities();
                return true;
            case 10:
                // Фитиль TNT 80 тиков; наблюдаем до взрыва на сервере
                entities.observe(mc);
                if (!entities.tntDone()) {
                    return false;
                }
                entities.checkItemsAndTnt();
                entities.checkPathCowAndStartTempt();
                return true;
            case 11:
                entities.observe(mc);
                if (stepTicks < 160) {
                    return false;
                }
                entities.checkTempt(mc);
                report.info("extended packets sent/received: " + counters());
                finish(mc);
                return true;
            default:
                return false;
        }
    }

    // ---- проверки без мира ----

    private void staticChecks() {
        report.check(
            "S26 chunk limit for 36 sections = 4 (5.A)",
            ChunkCodec.maxChunksPerBulk(36) == 4,
            ChunkCodec.maxChunksPerBulk(36));
        report.check(
            "S26 chunk limit for 64 sections = 2 (5.A)",
            ChunkCodec.maxChunksPerBulk(64) == 2,
            ChunkCodec.maxChunksPerBulk(64));
        report.check(
            "S26 chunk limit for 16 sections = vanilla 5",
            ChunkCodec.maxChunksPerBulk(16) == 5,
            ChunkCodec.maxChunksPerBulk(16));

        ModContainer self = Loader.instance()
            .getIndexedModList()
            .get(ExtendedHeightMod.MODID);
        report.check(
            "mod version is semantic MAJOR.MINOR.PATCH and matches the build",
            self != null && self.getVersion()
                .matches("\\d+\\.\\d+\\.\\d+")
                && self.getVersion()
                    .equals(io.github.extendedheight.Tags.VERSION),
            self == null ? null : self.getVersion());
        report.check(
            "FML knows the API package (ExtendedHeightAPI " + ExtendedHeight.API_VERSION + ")",
            cpw.mods.fml.common.ModAPIManager.INSTANCE.hasAPI("ExtendedHeightAPI"),
            null);

        Map<String, String> mods = new HashMap<>();
        for (ModContainer mod : Loader.instance()
            .getActiveModList()) {
            mods.put(mod.getModId(), mod.getVersion());
        }
        String accepted = FMLNetworkHandler.checkModList(mods, Side.CLIENT);
        report.check("handshake: client with the mod is accepted", accepted == null, accepted);
        mods.remove(ExtendedHeightMod.MODID);
        String rejected = FMLNetworkHandler.checkModList(mods, Side.CLIENT);
        report.check(
            "handshake: client without the mod gets a clear reason",
            rejected != null && rejected.contains("requires the mod ExtendedHeight"),
            rejected);
        report.check(
            "handshake: server without the mod is accepted by the client",
            FMLNetworkHandler.checkModList(mods, Side.SERVER) == null,
            FMLNetworkHandler.checkModList(mods, Side.SERVER));
    }

    // ---- расширенный мир ----

    private boolean clientChunksAround(Minecraft mc, int radius) {
        int cx = MathHelper.floor_double(mc.thePlayer.posX) >> 4;
        int cz = MathHelper.floor_double(mc.thePlayer.posZ) >> 4;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                Chunk chunk = mc.theWorld.getChunkFromChunkCoords(cx + dx, cz + dz);
                if (chunk.isEmpty()) {
                    return false;
                }
            }
        }
        return true;
    }

    private WorldServer serverWorld(Minecraft mc) {
        return mc.getIntegratedServer()
            .worldServerForDimension(0);
    }

    private void checkExtendedWorldSynced(Minecraft mc) {
        HeightRange expectedRange = HeightRange.of(WorldTypeExtendedTest.MIN_Y, WorldTypeExtendedTest.MAX_Y);
        report.check(
            "server overworld range = -256..319",
            expectedRange.equals(ExtendedHeight.getRange(serverWorld(mc))),
            ExtendedHeight.getRange(serverWorld(mc)));
        report.check(
            "client world range = -256..319 (height table sync)",
            expectedRange.equals(ExtendedHeight.getRange(mc.theWorld)),
            ExtendedHeight.getRange(mc.theWorld));
        checkCounter("HeightTableSync", ExtPackets.TABLE, 1);
        checkCounter("ExtMapChunkBulk", ExtPackets.BULK, 1);

        bx = MathHelper.floor_double(mc.thePlayer.posX);
        bz = MathHelper.floor_double(mc.thePlayer.posZ);
        report.info("extended world: player at " + bx + ", " + MathHelper.floor_double(mc.thePlayer.posY) + ", " + bz);
        Chunk clientChunk = mc.theWorld.getChunkFromChunkCoords(bx >> 4, bz >> 4);
        report.check(
            "client chunk has 36 sections",
            clientChunk.getBlockStorageArray().length == 36,
            clientChunk.getBlockStorageArray().length);

        // Каждый блок 3 x 3 чанков вокруг игрока по всему диапазону
        int mismatches = 0;
        String first = null;
        WorldServer server = serverWorld(mc);
        int cx = bx >> 4, cz = bz >> 4;
        for (int x = (cx - 1) * 16; x < (cx + 2) * 16; x++) {
            for (int z = (cz - 1) * 16; z < (cz + 2) * 16; z++) {
                for (int y = expectedRange.minY; y <= expectedRange.maxY; y++) {
                    if (!same(server, mc.theWorld, x, y, z)) {
                        mismatches++;
                        if (first == null) {
                            first = describe(server, mc.theWorld, x, y, z);
                        }
                    }
                }
            }
        }
        report.check(
            "client blocks = server blocks, 3x3 chunks, y -256..319",
            mismatches == 0,
            mismatches + " mismatches, first " + first);
        report.check(
            "ground: bedrock at y -64, wool at y -256 and -1, grass at 63",
            mc.theWorld.getBlock(bx, -64, bz) == Blocks.bedrock && mc.theWorld.getBlock(bx, -256, bz) == Blocks.wool
                && mc.theWorld.getBlock(bx, -1, bz) == Blocks.wool
                && mc.theWorld.getBlock(bx, 63, bz) == Blocks.grass,
            null);
    }

    private static boolean same(World a, World b, int x, int y, int z) {
        return a.getBlock(x, y, z) == b.getBlock(x, y, z) && a.getBlockMetadata(x, y, z) == b.getBlockMetadata(x, y, z);
    }

    private static String describe(World server, World client, int x, int y, int z) {
        return String.format(
            "(%d,%d,%d) server %s:%d client %s:%d",
            x,
            y,
            z,
            Block.blockRegistry.getNameForObject(server.getBlock(x, y, z)),
            server.getBlockMetadata(x, y, z),
            Block.blockRegistry.getNameForObject(client.getBlock(x, y, z)),
            client.getBlockMetadata(x, y, z));
    }

    private void expect(int x, int y, int z, Block block) {
        expected.put(x + "," + y + "," + z, block);
    }

    /** Серверные правки {@link NetEdits} вокруг чанка игрока */
    private void scheduleServerEdits(Minecraft mc) {
        List<NetEdits.Edit> edits = NetEdits.serverEdits(bx, bz);
        expected.clear();
        for (NetEdits.Edit e : edits) {
            expect(e.x, e.y, e.z, e.block);
        }
        WorldServer world = serverWorld(mc);
        serverTasks.add(() -> {
            for (NetEdits.Edit e : edits) {
                world.setBlock(e.x, e.y, e.z, e.block, 0, 3);
            }
        });
    }

    /** Телепорт в (x + 0.5, y, z + 0.5): cave — вырезает карман 5x3x5, иначе платформа 5x5 на floorY */
    private void teleport(Minecraft mc, int x, int y, int z, int floorY, boolean cave) {
        WorldServer world = serverWorld(mc);
        expected.clear();
        serverTasks.add(() -> {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (cave) {
                        for (int dy = 0; dy < 3; dy++) {
                            world.setBlock(x + dx, y + dy, z + dz, Blocks.air, 0, 3);
                        }
                    } else {
                        world.setBlock(x + dx, floorY, z + dz, Blocks.stone, 0, 3);
                    }
                }
            }
            EntityPlayerMP player = (EntityPlayerMP) world.playerEntities.get(0);
            player.fallDistance = 0;
            player.playerNetServerHandler.setPlayerLocation(x + 0.5D, y, z + 0.5D, 0, 0);
        });
    }

    /** Затемнение и частицы пустоты на y -100 с конфигом по умолчанию */
    private void checkVoidEffects(Minecraft mc) {
        EntityRendererAccessor fog = (EntityRendererAccessor) mc.entityRenderer;
        float sum = fog.eh$getFogRed() + fog.eh$getFogGreen() + fog.eh$getFogBlue();
        report.check("default config: fog colour at y -100 is not black (no void darkening)", sum > 0.05F, sum);

        int x = MathHelper.floor_double(mc.thePlayer.posX), z = MathHelper.floor_double(mc.thePlayer.posZ);
        boolean followMinY = EhConfig.voidParticlesFollowMinY;
        double intensity = EhConfig.voidParticleIntensity;
        try {
            int defaults = voidParticles(mc, x, z);
            EhConfig.voidParticleIntensity = 1.0D;
            int full = voidParticles(mc, x, z);
            report.check(
                "default config: void particles at y -100, about half of intensity 1",
                defaults > 0 && defaults < full * 0.75D && defaults > full * 0.25D,
                defaults + " vs " + full);
            EhConfig.voidParticlesFollowMinY = true;
            int followed = voidParticles(mc, x, z);
            report.check(
                "voidParticlesFollowMinY: no void particles at y -100 (zone is below minY + 8)",
                followed == 0,
                followed);
            EhConfig.voidParticlesFollowMinY = false;
            EhConfig.voidParticleIntensity = 0.0D;
            int none = voidParticles(mc, x, z);
            report.check("voidParticleIntensity 0: no void particles", none == 0, none);
        } finally {
            EhConfig.voidParticlesFollowMinY = followMinY;
            EhConfig.voidParticleIntensity = intensity;
        }
    }

    private static int voidParticles(Minecraft mc, int x, int z) {
        int before = VoidStats.particles;
        for (int i = 0; i < 20; i++) {
            mc.theWorld.doVoidFogParticles(x, ClientEntityTests.Y + 1, z);
        }
        return VoidStats.particles - before;
    }

    private void checkPlayerAt(Minecraft mc, int y) {
        double py = mc.thePlayer.posY - mc.thePlayer.yOffset;
        report.check(
            "player at y " + y + " on the client and alive",
            Math.abs(py - y) < 1.5D && !mc.thePlayer.isDead && mc.thePlayer.getHealth() > 0,
            "y " + py + ", dead " + mc.thePlayer.isDead);
    }

    /** Ломает блок пола в двух к востоку и ставит камень в двух к западу */
    private void playerDigAndPlace(Minecraft mc, int floorY) {
        int digX = bx + 2, placeX = bx - 2;
        mc.playerController.clickBlock(digX, floorY, bz, 1);
        ItemStack stone = new ItemStack(Blocks.stone, 64);
        int slot = mc.thePlayer.inventory.currentItem;
        mc.thePlayer.inventory.mainInventory[slot] = stone;
        mc.playerController.sendSlotPacket(stone, 36 + slot);
        boolean placed = mc.playerController.onPlayerRightClick(
            mc.thePlayer,
            mc.theWorld,
            stone,
            placeX,
            floorY,
            bz,
            1,
            Vec3.createVectorHelper(placeX + 0.5D, floorY + 1, bz + 0.5D));
        report.check("client accepts placing on top of y " + floorY, placed, null);
        expected.clear();
        expect(digX, floorY, bz, Blocks.air);
        expect(placeX, floorY + 1, bz, Blocks.stone);
    }

    private void compareExpected(Minecraft mc, String name) {
        WorldServer server = serverWorld(mc);
        List<String> wrong = new ArrayList<>();
        for (Map.Entry<String, Block> e : expected.entrySet()) {
            String[] p = e.getKey()
                .split(",");
            int x = Integer.parseInt(p[0]), y = Integer.parseInt(p[1]), z = Integer.parseInt(p[2]);
            if (server.getBlock(x, y, z) != e.getValue() || !same(server, mc.theWorld, x, y, z)) {
                wrong.add(describe(server, mc.theWorld, x, y, z));
            }
        }
        report.check(
            name + ", " + expected.size() + " blocks",
            wrong.isEmpty(),
            wrong.size() + " wrong, first " + (wrong.isEmpty() ? null : wrong.get(0)));
    }

    // ---- счётчики ----

    private static boolean countersZero() {
        for (int i = 0; i < ExtPackets.NAMES.length; i++) {
            if (ExtPackets.SENT.get(i) != 0 || ExtPackets.RECEIVED.get(i) != 0) {
                return false;
            }
        }
        return true;
    }

    private static String counters() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ExtPackets.NAMES.length; i++) {
            sb.append(ExtPackets.NAMES[i])
                .append(' ')
                .append(ExtPackets.SENT.get(i))
                .append('/')
                .append(ExtPackets.RECEIVED.get(i))
                .append("; ");
        }
        return sb.toString();
    }

    private void checkCounter(String name, int slot, int min) {
        int sent = ExtPackets.SENT.get(slot), received = ExtPackets.RECEIVED.get(slot);
        report.check(
            name + ": at least " + min + " sent and received",
            sent >= min && received >= min,
            sent + "/" + received);
    }

    // ---- конец ----

    private void leaveWorld(Minecraft mc) {
        if (mc.theWorld != null) {
            mc.theWorld.sendQuittingDisconnectingPacket();
            mc.loadWorld(null);
            // Как GuiIngameMenu: без экрана камера прочтёт thePlayer = null -> NPE
            mc.displayGuiScreen(new GuiMainMenu());
        }
    }

    private void finish(Minecraft mc) {
        done = true;
        leaveWorld(mc);
        report.writeReport();
        mc.shutdown();
    }
}
