package io.github.extendedheight.selftest;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.ServerChatEvent;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import io.github.extendedheight.ExtendedHeightMod;
import io.github.extendedheight.api.ExtendedHeight;
import io.github.extendedheight.api.HeightRange;
import io.github.extendedheight.net.ExtPackets;

/** Выделенный сервер сетевого самотеста: клиент с модом управляет командами чата ehtest:<команда> */
public final class NetServerSelfTest {

    private static final int TIMEOUT_TICKS = 20 * 60 * 10;
    static final String EXPECTED_REJECTION = "requires the mod ExtendedHeight";

    /** Ставится хуком рукопожатия на сервере (MixinFMLNetworkHandler) */
    public static volatile String lastRejection;

    private final SelfTest report;
    private final MinecraftServer server;
    private final Queue<Runnable> tasks = new ConcurrentLinkedQueue<>();
    private int ticks;
    private volatile boolean clientDone;
    private boolean finished;

    private NetServerSelfTest(SelfTest report, MinecraftServer server) {
        this.report = report;
        this.server = server;
    }

    static void start(SelfTest report, MinecraftServer server) {
        NetServerSelfTest test = new NetServerSelfTest(report, server);
        WorldServer world = server.worldServerForDimension(0);
        HeightRange range = ExtendedHeight.getRange(world);
        report.check(
            "dedicated server: overworld of level-type extendedtest is -256..319",
            range.equals(HeightRange.of(WorldTypeExtendedTest.MIN_Y, WorldTypeExtendedTest.MAX_Y)),
            range + ", type "
                + world.getWorldInfo()
                    .getTerrainType()
                    .getWorldTypeName());
        MinecraftForge.EVENT_BUS.register(test);
        FMLCommonHandler.instance()
            .bus()
            .register(test);
        report.info("network server ready");
        // scripts/selftest.ps1 ждёт эту строку перед запуском клиентов
        ExtendedHeightMod.LOG.info("ExtendedHeight network self-test server ready");
    }

    @SubscribeEvent
    public void onChat(ServerChatEvent event) {
        if (!event.message.startsWith("ehtest:")) {
            return;
        }
        event.setCanceled(true);
        String[] a = event.message.substring(7)
            .trim()
            .split(" ");
        EntityPlayerMP player = event.player;
        tasks.add(() -> command(player, a));
    }

    private void command(EntityPlayerMP player, String[] a) {
        WorldServer world = (WorldServer) player.worldObj;
        switch (a[0]) {
            case "tp": {
                int x = Integer.parseInt(a[1]), y = Integer.parseInt(a[2]), z = Integer.parseInt(a[3]);
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        if ("cave".equals(a[4])) {
                            for (int dy = 0; dy < 3; dy++) {
                                world.setBlock(x + dx, y + dy, z + dz, Blocks.air, 0, 3);
                            }
                        } else if ("platform".equals(a[4])) {
                            world.setBlock(x + dx, y - 1, z + dz, Blocks.stone, 0, 3);
                        }
                    }
                }
                player.fallDistance = 0;
                player.playerNetServerHandler.setPlayerLocation(x + 0.5D, y, z + 0.5D, 0, 0);
                break;
            }
            case "edits": {
                int bx = Integer.parseInt(a[1]), bz = Integer.parseInt(a[2]);
                for (NetEdits.Edit e : NetEdits.serverEdits(bx, bz)) {
                    world.setBlock(e.x, e.y, e.z, e.block, 0, 3);
                }
                break;
            }
            case "get": {
                int x = Integer.parseInt(a[1]), y = Integer.parseInt(a[2]), z = Integer.parseInt(a[3]);
                player.addChatMessage(
                    new ChatComponentText(
                        "ehtest:block " + x
                            + " "
                            + y
                            + " "
                            + z
                            + " "
                            + Block.getIdFromBlock(world.getBlock(x, y, z))
                            + " "
                            + world.getBlockMetadata(x, y, z)));
                break;
            }
            case "done":
                clientDone = true;
                report.check("dedicated server: modded client finished its checks", true, null);
                report.info("extended packets sent by the server: " + sentCounters());
                break;
            default:
                report.check("known network test command", false, a[0]);
        }
    }

    private static String sentCounters() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ExtPackets.NAMES.length; i++) {
            sb.append(ExtPackets.NAMES[i])
                .append(' ')
                .append(ExtPackets.SENT.get(i))
                .append("; ");
        }
        return sb.toString();
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || finished) {
            return;
        }
        Runnable task;
        while ((task = tasks.poll()) != null) {
            try {
                task.run();
            } catch (Throwable t) {
                ExtendedHeightMod.LOG.error("Network self-test command failed", t);
                report.check("network test command without exception", false, t);
            }
        }
        ticks++;
        String rejection = lastRejection;
        if (clientDone && rejection != null) {
            report.check(
                "dedicated server: client without the mod rejected with a clear reason",
                rejection.contains(EXPECTED_REJECTION),
                rejection);
            finish();
        } else if (ticks > TIMEOUT_TICKS) {
            report.check(
                "network test finished within " + TIMEOUT_TICKS / 20 + " s",
                false,
                "modded client done " + clientDone + ", rejection " + rejection);
            finish();
        }
    }

    private void finish() {
        finished = true;
        report.writeReport();
        server.initiateShutdown();
    }
}
