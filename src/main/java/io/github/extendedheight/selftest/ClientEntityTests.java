package io.github.extendedheight.selftest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityTNTPrimed;
import net.minecraft.entity.passive.EntityCow;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.WorldServer;

/** Сущности ниже 0 в комнате y -100..-97: предметы, TNT, коровы; клиент сверяется со снимком сервера */
final class ClientEntityTests {

    static final int Y = -100;

    private final SelfTest report;
    private final Queue<Runnable> serverTasks;
    private final WorldServer world;
    final int x0, z0;

    private static final class Snap {

        final double x, y, z;
        final boolean dead;
        final String what;
        /** Остаток фитиля TNT, -1 для других сущностей */
        final int fuse;

        Snap(Entity e) {
            x = e.posX;
            y = e.posY;
            z = e.posZ;
            dead = e.isDead;
            fuse = e instanceof EntityTNTPrimed ? ((EntityTNTPrimed) e).fuse : -1;
            what = e instanceof EntityItem ? "item " + ((EntityItem) e).getEntityItem()
                : e.getClass()
                    .getSimpleName();
        }
    }

    private volatile Map<Integer, Snap> snapshot = new HashMap<>();
    private volatile int tntId = -1;
    private volatile boolean tntExploded;
    private volatile int cowId = -1, temptCowId = -1;
    private volatile double cowStartX, cowStartZ, temptStartDist;
    private volatile boolean pathFound;

    /** Результаты наблюдения на клиенте */
    private int tntTicksSeen, tntTicksMissing, tntTicksWrongPos;
    private String tntFirstProblem;
    private int itemChecks, itemProblems;
    private String itemFirstProblem;

    ClientEntityTests(SelfTest report, Queue<Runnable> serverTasks, WorldServer world, int x0, int z0) {
        this.report = report;
        this.serverTasks = serverTasks;
        this.world = world;
        this.x0 = x0;
        this.z0 = z0;
    }

    private AxisAlignedBB room() {
        return AxisAlignedBB.getBoundingBox(x0 - 9, Y - 2, z0 - 9, x0 + 10, Y + 5, z0 + 10);
    }

    /** Вырезает комнату и телепортирует игрока в центр */
    void buildRoom() {
        serverTasks.add(() -> {
            for (int x = x0 - 8; x <= x0 + 8; x++) {
                for (int z = z0 - 8; z <= z0 + 8; z++) {
                    for (int y = Y; y < Y + 4; y++) {
                        world.setBlock(x, y, z, Blocks.air, 0, 3);
                    }
                }
            }
            EntityPlayerMP player = player();
            player.inventory.mainInventory[player.inventory.currentItem] = null;
            player.fallDistance = 0;
            player.playerNetServerHandler.setPlayerLocation(x0 + 0.5D, Y, z0 + 0.5D, 0, 0);
        });
    }

    private EntityPlayerMP player() {
        return (EntityPlayerMP) world.playerEntities.get(0);
    }

    /** Предметы, TNT и корова на пути; с этого начинаются снимки */
    void spawnEntities() {
        serverTasks.add(() -> {
            int cx = x0 + 3, cz = z0 + 3;
            world.setBlock(cx, Y, cz, Blocks.chest, 0, 3);
            IInventory chest = (IInventory) world.getTileEntity(cx, Y, cz);
            chest.setInventorySlotContents(0, new ItemStack(Blocks.cobblestone, 7));
            chest.setInventorySlotContents(5, new ItemStack(Items.iron_ingot, 3));
            world.func_147480_a(cx, Y, cz, true);
            world.setBlock(x0 + 3, Y, z0 - 3, Blocks.dirt, 0, 3);
            world.func_147480_a(x0 + 3, Y, z0 - 3, true);
            player().dropPlayerItemWithRandomChoice(new ItemStack(Items.diamond), false);

            EntityTNTPrimed tnt = new EntityTNTPrimed(world, x0 - 6.5D, Y, z0 - 6.5D, null);
            tnt.fuse = 80;
            world.spawnEntityInWorld(tnt);
            tntId = tnt.getEntityId();

            EntityCow cow = new EntityCow(world);
            cow.setLocationAndAngles(x0 + 6.5D, Y, z0 - 6.5D, 0, 0);
            world.spawnEntityInWorld(cow);
            cowId = cow.getEntityId();
            cowStartX = cow.posX;
            cowStartZ = cow.posZ;
            // PathNavigate планирует только для моба на земле; свежий ещё не тикал
            cow.onGround = true;
            pathFound = cow.getNavigator()
                .tryMoveToXYZ(x0 + 6.5D, Y, z0 + 6.5D, 1.0D);
        });
    }

    /** Каждый клиентский тик: заказывает снимок сервера и проверяет последний */
    void observe(Minecraft mc) {
        serverTasks.add(this::takeSnapshot);
        Map<Integer, Snap> snap = snapshot;
        for (Map.Entry<Integer, Snap> e : snap.entrySet()) {
            Snap s = e.getValue();
            if (s.dead) {
                continue;
            }
            Entity c = mc.theWorld.getEntityByID(e.getKey());
            String problem = null;
            if (c == null || c.isDead) {
                problem = "missing on the client";
            } else
                if (Math.abs(c.posX - s.x) > 1.5D || Math.abs(c.posY - s.y) > 1.5D || Math.abs(c.posZ - s.z) > 1.5D) {
                    problem = String.format(
                        "client at %.2f,%.2f,%.2f, server at %.2f,%.2f,%.2f",
                        c.posX,
                        c.posY,
                        c.posZ,
                        s.x,
                        s.y,
                        s.z);
                } else if (c instanceof EntityItem && ((EntityItem) c).getDataWatcher()
                    .getWatchableObjectItemStack(10) == null) {
                        problem = "client item has no stack";
                    }
            if (e.getKey() == tntId) {
                // Клиент сам считает фитиль и убирает TNT на тик-два раньше снимка сервера
                if (s.fuse <= 3) {
                    continue;
                }
                tntTicksSeen++;
                if (problem != null) {
                    if (problem.startsWith("missing")) {
                        tntTicksMissing++;
                    } else {
                        tntTicksWrongPos++;
                    }
                    if (tntFirstProblem == null) {
                        tntFirstProblem = "TNT " + problem + " (server tick snapshot " + tntTicksSeen + ")";
                    }
                }
            } else if (s.what.startsWith("item")) {
                itemChecks++;
                if (problem != null) {
                    itemProblems++;
                    if (itemFirstProblem == null) {
                        itemFirstProblem = s.what + ": " + problem;
                    }
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void takeSnapshot() {
        Map<Integer, Snap> snap = new HashMap<>();
        for (Entity e : (List<Entity>) world.getEntitiesWithinAABB(Entity.class, room())) {
            if (!(e instanceof EntityPlayerMP)) {
                snap.put(e.getEntityId(), new Snap(e));
            }
        }
        Entity tnt = world.getEntityByID(tntId);
        if (tntId >= 0 && tnt == null && world.getBlock(x0 - 7, Y - 1, z0 - 7) == Blocks.air) {
            tntExploded = true;
        }
        snapshot = snap;
    }

    boolean tntDone() {
        return tntExploded;
    }

    void checkItemsAndTnt() {
        report.check(
            "items below 0 (chest contents, block drop, player drop) present on the client at the server position",
            itemChecks > 0 && itemProblems == 0,
            itemChecks + " checks, " + itemProblems + " problems, first " + itemFirstProblem);
        report.check(
            "primed TNT at y -100 present on the client for its whole fuse",
            tntTicksSeen > 60 && tntTicksMissing == 0 && tntTicksWrongPos == 0,
            tntTicksSeen + " ticks, missing "
                + tntTicksMissing
                + ", wrong position "
                + tntTicksWrongPos
                + ", first "
                + tntFirstProblem);
        report.check("primed TNT at y -100 exploded (floor below destroyed)", tntExploded, null);
    }

    /** Проверяет корову на пути, затем спавнит корову за пшеницей и даёт игроку пшеницу */
    void checkPathCowAndStartTempt() {
        Snap cow = snapshot.get(cowId);
        report.check("cow at y -100: path found", pathFound, null);
        double moved = cow == null ? -1 : Math.hypot(cow.x - cowStartX, cow.z - cowStartZ);
        report.check("cow at y -100 walks along its path (moved > 4 blocks)", moved > 4.0D, moved);
        serverTasks.add(() -> {
            Entity old = world.getEntityByID(cowId);
            if (old != null) {
                old.setDead();
            }
            EntityCow c = new EntityCow(world);
            c.setLocationAndAngles(x0 + 0.5D, Y, z0 + 7.5D, 0, 0);
            world.spawnEntityInWorld(c);
            temptCowId = c.getEntityId();
            EntityPlayerMP player = player();
            temptStartDist = c.getDistanceToEntity(player);
            player.inventory.mainInventory[player.inventory.currentItem] = new ItemStack(Items.wheat);
        });
    }

    void checkTempt(Minecraft mc) {
        Snap cow = snapshot.get(temptCowId);
        double dist = cow == null ? -1 : Math.hypot(cow.x - (x0 + 0.5D), cow.z - (z0 + 0.5D));
        report.check(
            "cow at y -100 follows the player holding wheat (distance " + String.format("%.1f", temptStartDist)
                + " -> < 4)",
            cow != null && dist < 4.0D,
            dist);
        Entity client = mc.theWorld.getEntityByID(temptCowId);
        report.check(
            "tempted cow: client position matches the server",
            client != null && cow != null
                && Math.abs(client.posX - cow.x) < 1.5D
                && Math.abs(client.posZ - cow.z) < 1.5D,
            client == null ? "missing" : client.posX + "," + client.posZ);
    }
}
