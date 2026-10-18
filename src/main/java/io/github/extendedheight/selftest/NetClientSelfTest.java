package io.github.extendedheight.selftest;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiDisconnected;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.common.MinecraftForge;

import cpw.mods.fml.client.FMLClientHandler;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import io.github.extendedheight.ExtendedHeightMod;
import io.github.extendedheight.api.ExtendedHeight;
import io.github.extendedheight.api.HeightRange;
import io.github.extendedheight.net.ExtPackets;
import io.github.extendedheight.world.WorldTypeExtendedTest;

/** Клиент сетевого самотеста: net-client проверяет синхронизацию и правки, net-nomod — отказ без мода */
public final class NetClientSelfTest {

    private static final int STEP_TIMEOUT_TICKS = 20 * 120;
    private static final int SETTLE_TICKS = 60;
    private static final int BX = 3, BZ = 3;

    private final SelfTest report;
    private final String host;
    private final int port;
    private final boolean noMod;
    private int step, stepTicks, ticks;
    private boolean done;
    /** "x,y,z" -> ожидаемый блок, проверяется на клиенте и по ответам сервера */
    private final Map<String, Block> expected = new ConcurrentHashMap<>();
    private final Map<String, String> serverBlocks = new ConcurrentHashMap<>();

    private NetClientSelfTest(SelfTest report, String server, boolean noMod) {
        this.report = report;
        String[] hp = (server == null ? "127.0.0.1:25599" : server).split(":");
        this.host = hp[0];
        this.port = Integer.parseInt(hp[1]);
        this.noMod = noMod;
    }

    static void start(SelfTest report, String server, boolean noMod) {
        NetClientSelfTest test = new NetClientSelfTest(report, server, noMod);
        FMLCommonHandler.instance()
            .bus()
            .register(test);
        MinecraftForge.EVENT_BUS.register(test);
    }

    @SubscribeEvent
    public void onChat(ClientChatReceivedEvent event) {
        String text = event.message.getUnformattedText();
        if (text.startsWith("ehtest:block ")) {
            String[] a = text.substring(13)
                .split(" ");
            serverBlocks.put(a[0] + "," + a[1] + "," + a[2], a[3] + ":" + a[4]);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || done) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        ticks++;
        if (++stepTicks > STEP_TIMEOUT_TICKS) {
            report.check("network client step " + step + " completed in time", false, "timeout");
            finish(mc);
            return;
        }
        try {
            if (noMod ? noModStep(mc) : step(mc)) {
                step++;
                stepTicks = 0;
            }
        } catch (Throwable t) {
            ExtendedHeightMod.LOG.error("Network client self-test crashed in step {}", step, t);
            report.check("network client step " + step + " without exception", false, t);
            finish(mc);
        }
    }

    private boolean connect(Minecraft mc) {
        if (mc.currentScreen == null || ticks < 20) {
            return false;
        }
        report.info("connecting to " + host + ":" + port + (noMod ? " (mod hidden in the handshake)" : ""));
        // Путь через список серверов: только тут FML взводит защёлку рукопожатия
        FMLClientHandler.instance()
            .setupServerList();
        FMLClientHandler.instance()
            .connectToServer(new GuiMainMenu(), new ServerData("ehtest", host + ":" + port));
        return true;
    }

    private boolean noModStep(Minecraft mc) {
        switch (step) {
            case 0:
                return connect(mc);
            case 1:
                if (mc.theWorld != null) {
                    report.check("client without the mod is not let into the world", false, "joined");
                    finish(mc);
                    return true;
                }
                if (!(mc.currentScreen instanceof GuiDisconnected)) {
                    return false;
                }
                String reason = disconnectReason((GuiDisconnected) mc.currentScreen);
                report.check(
                    "client without the mod: disconnected with a clear reason",
                    reason != null && reason.contains(NetServerSelfTest.EXPECTED_REJECTION),
                    reason);
                report.info("disconnect reason: " + reason);
                finish(mc);
                return true;
            default:
                return false;
        }
    }

    private static String disconnectReason(GuiDisconnected gui) {
        for (Field f : GuiDisconnected.class.getDeclaredFields()) {
            if (IChatComponent.class.isAssignableFrom(f.getType())) {
                try {
                    f.setAccessible(true);
                    IChatComponent c = (IChatComponent) f.get(gui);
                    return c == null ? null : c.getUnformattedText();
                } catch (IllegalAccessException e) {
                    return e.toString();
                }
            }
        }
        return null;
    }

    private boolean step(Minecraft mc) {
        boolean settled = mc.theWorld != null && mc.thePlayer != null && stepTicks >= SETTLE_TICKS;
        switch (step) {
            case 0:
                return connect(mc);
            case 1:
                if (!settled) {
                    return false;
                }
                HeightRange expectedRange = HeightRange.of(WorldTypeExtendedTest.MIN_Y, WorldTypeExtendedTest.MAX_Y);
                report.check(
                    "network: client world range = -256..319 (height table from the dedicated server)",
                    expectedRange.equals(ExtendedHeight.getRange(mc.theWorld)),
                    ExtendedHeight.getRange(mc.theWorld));
                received("HeightTableSync", ExtPackets.TABLE, 1);
                mc.thePlayer.sendChatMessage("ehtest:tp " + BX + " 64 " + BZ + " none");
                return true;
            case 2:
                if (!settled || !chunksAround(mc, BX >> 4, BZ >> 4)) {
                    return false;
                }
                received("ExtMapChunkBulk", ExtPackets.BULK, 1);
                checkLayout(mc);
                for (NetEdits.Edit e : NetEdits.serverEdits(BX, BZ)) {
                    expected.put(e.x + "," + e.y + "," + e.z, e.block);
                }
                mc.thePlayer.sendChatMessage("ehtest:edits " + BX + " " + BZ);
                return true;
            case 3:
                if (!settled) {
                    return false;
                }
                compareClient(mc, "network: server block changes below 0 and above 255 reach the client");
                received("ExtBlockChange", ExtPackets.BLOCK, 2);
                received("ExtMultiBlockChange", ExtPackets.MULTI, 1);
                received("ExtChunkData (partial)", ExtPackets.CHUNK, 1);
                mc.thePlayer.sendChatMessage("ehtest:tp " + BX + " -100 " + BZ + " cave");
                return true;
            case 4:
            case 6:
                if (!settled) {
                    return false;
                }
                int y = step == 4 ? -100 : 300;
                double py = mc.thePlayer.posY - mc.thePlayer.yOffset;
                report.check(
                    "network: player at y " + y + " and alive",
                    Math.abs(py - y) < 1.5D && mc.thePlayer.getHealth() > 0,
                    py);
                digAndPlace(mc, y - 1);
                return true;
            case 5:
            case 7:
                if (serverBlocks.size() < expected.size() && stepTicks < 200) {
                    return false;
                }
                compareWithServer(mc, "network: dig/place at y " + (step == 5 ? "-101/-100" : "299/300"));
                if (step == 5) {
                    mc.thePlayer.sendChatMessage("ehtest:tp " + BX + " 300 " + BZ + " platform");
                    return true;
                }
                report.check(
                    "network: ExtPlayerDigging and ExtBlockPlacement sent",
                    ExtPackets.SENT.get(ExtPackets.DIGGING) >= 2 && ExtPackets.SENT.get(ExtPackets.PLACEMENT) >= 2,
                    ExtPackets.SENT.get(ExtPackets.DIGGING) + "/" + ExtPackets.SENT.get(ExtPackets.PLACEMENT));
                mc.thePlayer.sendChatMessage("ehtest:done");
                return true;
            case 8:
                // Даём «done» уйти до отключения
                if (stepTicks < 20) {
                    return false;
                }
                finish(mc);
                return true;
            default:
                return false;
        }
    }

    private boolean chunksAround(Minecraft mc, int cx, int cz) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (mc.theWorld.getChunkFromChunkCoords(cx + dx, cz + dz)
                    .isEmpty()) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Каждый блок 3 x 3 чанков вокруг (0, 0) против генератора: слои, бедрок, столб-маркер */
    private void checkLayout(Minecraft mc) {
        World w = mc.theWorld;
        Chunk chunk = w.getChunkFromChunkCoords(0, 0);
        report.check(
            "network: client chunk has 36 sections",
            chunk.getBlockStorageArray().length == 36,
            chunk.getBlockStorageArray().length);
        int mismatches = 0;
        String first = null;
        for (int x = -16; x < 32; x++) {
            for (int z = -16; z < 32; z++) {
                for (int y = WorldTypeExtendedTest.MIN_Y; y <= WorldTypeExtendedTest.MAX_Y; y++) {
                    Block block;
                    int meta;
                    if (y <= WorldTypeExtendedTest.TOP_Y) {
                        block = WorldTypeExtendedTest.groundBlock(y);
                        meta = WorldTypeExtendedTest.groundMeta(y);
                    } else if (x == 0 && z == 0) {
                        block = WorldTypeExtendedTest.columnBlock(y);
                        meta = WorldTypeExtendedTest.columnMeta(y);
                    } else {
                        block = Blocks.air;
                        meta = 0;
                    }
                    if (w.getBlock(x, y, z) != block || (block != Blocks.air && w.getBlockMetadata(x, y, z) != meta)) {
                        mismatches++;
                        if (first == null) {
                            first = x + ","
                                + y
                                + ","
                                + z
                                + " is "
                                + Block.blockRegistry.getNameForObject(w.getBlock(x, y, z))
                                + ":"
                                + w.getBlockMetadata(x, y, z);
                        }
                    }
                }
            }
        }
        report.check(
            "network: layers every 16 blocks, bedrock at -64 and the column up to 319 (3x3 chunks, y -256..319)",
            mismatches == 0,
            mismatches + " mismatches, first " + first);
    }

    private void digAndPlace(Minecraft mc, int floorY) {
        int digX = BX + 2, placeX = BX - 2;
        expected.clear();
        serverBlocks.clear();
        mc.playerController.clickBlock(digX, floorY, BZ, 1);
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
            BZ,
            1,
            Vec3.createVectorHelper(placeX + 0.5D, floorY + 1, BZ + 0.5D));
        report.check("network: client accepts placing on top of y " + floorY, placed, null);
        expected.put(digX + "," + floorY + "," + BZ, Blocks.air);
        expected.put(placeX + "," + (floorY + 1) + "," + BZ, Blocks.stone);
        for (String key : expected.keySet()) {
            mc.thePlayer.sendChatMessage("ehtest:get " + key.replace(',', ' '));
        }
    }

    private void compareClient(Minecraft mc, String name) {
        List<String> wrong = new ArrayList<>();
        for (Map.Entry<String, Block> e : expected.entrySet()) {
            String[] p = e.getKey()
                .split(",");
            Block b = mc.theWorld.getBlock(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]));
            if (b != e.getValue()) {
                wrong.add(e.getKey() + " is " + Block.blockRegistry.getNameForObject(b));
            }
        }
        report.check(
            name + ", " + expected.size() + " blocks",
            wrong.isEmpty(),
            wrong.size() + " wrong, first " + (wrong.isEmpty() ? null : wrong.get(0)));
    }

    private void compareWithServer(Minecraft mc, String name) {
        compareClient(mc, name + ", client");
        List<String> wrong = new ArrayList<>();
        for (Map.Entry<String, Block> e : expected.entrySet()) {
            String server = serverBlocks.get(e.getKey());
            String want = Block.getIdFromBlock(e.getValue()) + ":0";
            if (!want.equals(server)) {
                wrong.add(e.getKey() + " is " + server + ", want " + want);
            }
        }
        report.check(name + ", server", wrong.isEmpty(), wrong.isEmpty() ? null : wrong.get(0));
    }

    private void received(String name, int slot, int min) {
        int r = ExtPackets.RECEIVED.get(slot);
        report.check("network: " + name + " received at least " + min, r >= min, r);
    }

    private void finish(Minecraft mc) {
        done = true;
        if (mc.theWorld != null) {
            mc.theWorld.sendQuittingDisconnectingPacket();
            mc.loadWorld(null);
            mc.displayGuiScreen(new GuiMainMenu());
        }
        report.writeReport();
        mc.shutdown();
    }
}
