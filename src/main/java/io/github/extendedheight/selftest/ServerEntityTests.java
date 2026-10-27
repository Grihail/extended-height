package io.github.extendedheight.selftest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.entity.item.EntityFallingBlock;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.passive.EntityCow;
import net.minecraft.entity.passive.EntityPig;
import net.minecraft.init.Blocks;
import net.minecraft.pathfinding.PathEntity;
import net.minecraft.pathfinding.PathPoint;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.ChunkPosition;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.SpawnerAnimals;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;

import io.github.extendedheight.api.ExtendedHeight;
import io.github.extendedheight.api.HeightRange;
import io.github.extendedheight.mixins.early.SpawnerAnimalsInvoker;

/** Шаг 3 плана (сущности и спавн ниже 0), фаза write; каждая проверка падает на ванили */
final class ServerEntityTests {

    /** Столбцы вдали от остальных построек фазы write */
    private static final int BASE_X = 400, BASE_Z = 400;

    private final SelfTest report;
    private final WorldServer ext;
    private final WorldServer overworld;
    private final HeightRange range;

    ServerEntityTests(SelfTest report, WorldServer ext, WorldServer overworld) {
        this.report = report;
        this.ext = ext;
        this.overworld = overworld;
        this.range = ExtendedHeight.getRange(ext);
    }

    void run() {
        // Сущности добавляются только в загруженные чанки — сначала грузим всё вокруг
        for (int cx = (BASE_X >> 4) - 3; cx <= ((BASE_X + 96) >> 4) + 3; cx++) {
            for (int cz = (BASE_Z >> 4) - 3; cz <= (BASE_Z >> 4) + 3; cz++) {
                ext.getChunkFromChunkCoords(cx, cz);
            }
        }
        spawnPosition();
        monsterSpawnBelowZero();
        naturalSpawningBelowZero();
        fallingBlocks();
        fallingBlockEntity();
        safePoint();
        entityLists();
        biomesAt();
    }

    /** Вариант C: IBiomeAtProvider мира спрашивают спавн-листы и влажность */
    @SuppressWarnings("unchecked")
    private void biomesAt() {
        int x = BASE_X + 128, z = BASE_Z;
        net.minecraft.world.gen.ChunkProviderGenerate vanillaGen = new net.minecraft.world.gen.ChunkProviderGenerate(
            ext,
            1L,
            false);
        SelfTestWorldProvider.biomesByHeight = true;
        try {
            boolean ghastBelow = spawnsGhast(vanillaGen.getPossibleCreatures(EnumCreatureType.monster, x, -200, z));
            boolean ghastAbove = spawnsGhast(vanillaGen.getPossibleCreatures(EnumCreatureType.monster, x, 100, z));
            report.check(
                "biome at (x, y, z): vanilla generator spawn list at y -200 is the hell biome's (ghasts), at y 100 not",
                ghastBelow && !ghastAbove,
                ghastBelow + " / " + ghastAbove);
            report.check(
                "biome at (x, y, z): humidity at y -100 is the swamp's (high), getBiome gives swampland/hell",
                ext.provider.isBlockHighHumidity(x, -100, z)
                    && ExtendedHeight.getBiome(ext, x, -100, z) == net.minecraft.world.biome.BiomeGenBase.swampland
                    && ExtendedHeight.getBiome(ext, x, -200, z) == net.minecraft.world.biome.BiomeGenBase.hell,
                ExtendedHeight.getBiome(ext, x, -100, z).biomeName);
        } finally {
            SelfTestWorldProvider.biomesByHeight = false;
        }
        report.check(
            "biome at (x, y, z): without the provider's answer the column biome is used",
            ExtendedHeight.getBiome(ext, x, -100, z) == ext.getBiomeGenForCoords(x, z),
            ExtendedHeight.getBiome(ext, x, -100, z).biomeName);
    }

    private static boolean spawnsGhast(List<?> list) {
        if (list == null) {
            return false;
        }
        for (Object o : list) {
            if (((net.minecraft.world.biome.BiomeGenBase.SpawnListEntry) o).entityClass
                == net.minecraft.entity.monster.EntityGhast.class) {
                return true;
            }
        }
        return false;
    }

    /** 7.6: ваниль y = nextInt(top + 15) — не ниже 0 и падает при верхе ниже -15 */
    private void spawnPosition() {
        int cx = BASE_X >> 4, cz = BASE_Z >> 4;
        ext.setBlock(BASE_X, -200, BASE_Z, Blocks.stone, 0, 3);
        Chunk low = ext.getChunkFromChunkCoords(cx, cz);
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        Throwable error = null;
        try {
            for (int i = 0; i < 300; i++) {
                ChunkPosition p = SpawnerAnimalsInvoker.eh$randomSpawnPosition(ext, cx, cz);
                minY = Math.min(minY, p.chunkPosY);
                maxY = Math.max(maxY, p.chunkPosY);
            }
        } catch (Throwable t) {
            error = t;
        }
        int top = low.getTopFilledSegment() + 15;
        report.check(
            "7.6 spawn position in a chunk whose top is y -193: no exception, y within -256..-194",
            error == null && minY >= range.minY && maxY < top,
            error != null ? error : minY + ".." + maxY + " (top " + top + ")");

        ext.setBlock(BASE_X + 16, 300, BASE_Z, Blocks.stone, 0, 3);
        ext.setBlock(BASE_X + 16, -200, BASE_Z, Blocks.stone, 0, 3);
        int below = 0;
        minY = Integer.MAX_VALUE;
        for (int i = 0; i < 300; i++) {
            ChunkPosition p = SpawnerAnimalsInvoker.eh$randomSpawnPosition(ext, cx + 1, cz);
            minY = Math.min(minY, p.chunkPosY);
            if (p.chunkPosY < 0) {
                below++;
            }
        }
        report.check(
            "7.6 spawn positions of a chunk with blocks from -200 up to 300 reach below 0 (and not below its lowest section)",
            below > 0 && minY >= -208,
            below + " of 300 below 0, lowest " + minY);
    }

    /** Естественный спавн монстра в тёмной комнате на y -100 проходит те же проверки, что выше 0 */
    private void monsterSpawnBelowZero() {
        int x0 = BASE_X + 160, z0 = BASE_Z, floor = -101;
        for (int x = x0 - 3; x <= x0 + 3; x++) {
            for (int z = z0 - 3; z <= z0 + 3; z++) {
                for (int y = floor; y <= floor + 4; y++) {
                    boolean shell = y == floor || y == floor + 4 || Math.abs(x - x0) == 3 || Math.abs(z - z0) == 3;
                    ext.setBlock(x, y, z, shell ? Blocks.stone : Blocks.air, 0, 3);
                }
            }
        }
        int y = floor + 1;
        EnumDifficulty difficulty = ext.difficultySetting;
        ext.difficultySetting = EnumDifficulty.NORMAL;
        try {
            boolean location = SpawnerAnimals.canCreatureTypeSpawnAtLocation(EnumCreatureType.monster, ext, x0, y, z0);
            EntityZombie zombie = new EntityZombie(ext);
            zombie.setLocationAndAngles(x0 + 0.5D, y, z0 + 0.5D, 0.0F, 0.0F);
            boolean canSpawn = zombie.getCanSpawnHere();
            report.check(
                "monster spawning in a dark room at y -100: location accepted and the zombie can spawn there",
                location && canSpawn,
                "location " + location
                    + ", getCanSpawnHere "
                    + canSpawn
                    + ", sky light "
                    + ext.getSavedLightValue(EnumSkyBlock.Sky, x0, y, z0)
                    + ", light "
                    + ext.getBlockLightValue(x0, y, z0)
                    + ", collisions "
                    + ext.getCollidingBoundingBoxes(zombie, zombie.boundingBox)
                        .size()
                    + ", liquid "
                    + ext.isAnyLiquid(zombie.boundingBox)
                    + ", path weight "
                    + zombie.getBlockPathWeight(x0, y, z0));
        } finally {
            ext.difficultySetting = difficulty;
        }
    }

    /** Настоящий цикл спавна заполняет тёмную полость на y -100 зомби; фейк-игрок висит на 40 выше */
    private void naturalSpawningBelowZero() {
        int x0 = BASE_X + 256, z0 = BASE_Z, floor = -101;
        for (int x = x0; x < x0 + 32; x++) {
            for (int z = z0; z < z0 + 32; z++) {
                ext.setBlock(x, floor, z, Blocks.stone, 0, 2);
                ext.setBlock(x, floor + 4, z, Blocks.stone, 0, 2);
            }
        }
        FakePlayer player = FakePlayerFactory.getMinecraft(ext);
        player.setPosition(x0 + 16, floor + 40, z0 + 16);
        EnumDifficulty difficulty = ext.difficultySetting;
        ext.difficultySetting = EnumDifficulty.NORMAL;
        SelfTestWorldProvider.testMonsters = Collections
            .singletonList(new BiomeGenBase.SpawnListEntry(EntityZombie.class, 100, 4, 4));
        ext.playerEntities.add(player);
        int below = 0, other = 0;
        try {
            SpawnerAnimals spawner = new SpawnerAnimals();
            for (int i = 0; i < 200; i++) {
                spawner.findChunksForSpawning(ext, true, false, false);
            }
        } finally {
            ext.playerEntities.remove(player);
            SelfTestWorldProvider.testMonsters = null;
            ext.difficultySetting = difficulty;
            for (Object o : new ArrayList<Object>(ext.loadedEntityList)) {
                if (o instanceof EntityZombie) {
                    EntityZombie zombie = (EntityZombie) o;
                    if (zombie.posY < 0 && zombie.posY > floor) {
                        below++;
                    } else {
                        other++;
                    }
                    zombie.setDead();
                }
            }
        }
        report.check(
            "natural spawning (vanilla findChunksForSpawning) puts zombies into a dark cavity at y -100",
            below > 0,
            below + " zombies in the cavity, " + other + " elsewhere");
    }

    /** 8.4: ваниль падает только при y >= 0, мгновенное падение до y > 0 */
    private void fallingBlocks() {
        int x = BASE_X + 32, z = BASE_Z;
        ext.setBlock(x, -111, z, Blocks.stone, 0, 3);
        ext.setBlock(x, -100, z, Blocks.sand, 0, 2);
        net.minecraft.block.BlockFalling.fallInstantly = true;
        try {
            Blocks.sand.updateTick(ext, x, -100, z, new Random(1));
        } finally {
            net.minecraft.block.BlockFalling.fallInstantly = false;
        }
        report.check(
            "8.4 instant fall at y -100: the sand lands on the stone at -111",
            ext.getBlock(x, -100, z) == Blocks.air && ext.getBlock(x, -110, z) == Blocks.sand,
            Block.blockRegistry.getNameForObject(ext.getBlock(x, -100, z)) + " / "
                + Block.blockRegistry.getNameForObject(ext.getBlock(x, -110, z)));

        int x2 = x + 4;
        ext.setBlock(x2, -100, z, Blocks.gravel, 0, 2);
        AxisAlignedBB box = AxisAlignedBB.getBoundingBox(x2 - 1, -102, z - 1, x2 + 2, -98, z + 2);
        int before = ext.getEntitiesWithinAABB(EntityFallingBlock.class, box)
            .size();
        Blocks.gravel.updateTick(ext, x2, -100, z, new Random(1));
        int after = ext.getEntitiesWithinAABB(EntityFallingBlock.class, box)
            .size();
        report.check(
            "8.4 gravel at y -100 over air starts falling (a falling block entity appears)",
            after > before,
            before + " -> " + after);

        int x3 = x + 8;
        ext.setBlock(x3, -111, z, Blocks.stone, 0, 3);
        ext.setBlock(x3, -100, z, Blocks.dragon_egg, 0, 2);
        net.minecraft.block.BlockFalling.fallInstantly = true;
        try {
            Blocks.dragon_egg.updateTick(ext, x3, -100, z, new Random(1));
        } finally {
            net.minecraft.block.BlockFalling.fallInstantly = false;
        }
        report.check(
            "8.4 dragon egg instant fall at y -100 lands on the stone at -111",
            ext.getBlock(x3, -110, z) == Blocks.dragon_egg,
            Block.blockRegistry.getNameForObject(ext.getBlock(x3, -110, z)));
    }

    /** 7.5: ваниль роняет падающий блок предметом через 100 тиков при y < 1 */
    private void fallingBlockEntity() {
        int x = BASE_X + 48, z = BASE_Z;
        EntityFallingBlock e = new EntityFallingBlock(ext, x + 0.5D, -100.5D, z + 0.5D, Blocks.sand, 0);
        ext.spawnEntityInWorld(e);
        e.field_145812_b = 150;
        e.onUpdate();
        report.check("7.5 falling block at y -100 older than 100 ticks keeps falling (not dropped)", !e.isDead, "dead");
        e.setDead();
    }

    /** 7.8: ванильный getSafePoint не ищет ниже y 0 — путь уходит с уступа в воздух */
    private void safePoint() {
        int x = BASE_X + 64, z = BASE_Z;
        // Верхний пол на -100 (x..x+3), нижний на -103 (x+4..x+9)
        for (int dx = -1; dx <= 10; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                ext.setBlock(x + dx, dx <= 3 ? -101 : -104, z + dz, Blocks.stone, 0, 3);
            }
        }
        EntityCow cow = new EntityCow(ext);
        cow.setLocationAndAngles(x + 0.5D, -100, z + 0.5D, 0, 0);
        PathEntity path = ext.getEntityPathToXYZ(cow, x + 8, -103, z, 16.0F, true, false, false, true);
        boolean ok = path != null;
        String bad = path == null ? "no path" : null;
        if (path != null) {
            PathPoint last = path.getFinalPathPoint();
            ok = last.yCoord == -103;
            if (!ok) {
                bad = "ends at y " + last.yCoord;
            }
            for (int i = 0; i < path.getCurrentPathLength() && ok; i++) {
                PathPoint p = path.getPathPointFromIndex(i);
                if (!ext.getBlock(p.xCoord, p.yCoord - 1, p.zCoord)
                    .getMaterial()
                    .isSolid()) {
                    ok = false;
                    bad = "point " + p.xCoord + "," + p.yCoord + "," + p.zCoord + " stands on air";
                }
            }
        }
        report
            .check("7.8 path down a 3-block ledge at y -100: ends on the lower floor, every point on ground", ok, bad);
    }

    /** 1.8: ваниль кладёт сущности ниже y 16 в список 0 с chunkCoordY 0 */
    @SuppressWarnings("unchecked")
    private void entityLists() {
        int x = BASE_X + 96, z = BASE_Z;
        EntityPig low = new EntityPig(ext);
        low.setLocationAndAngles(x + 0.5D, -100, z + 0.5D, 0, 0);
        ext.spawnEntityInWorld(low);
        EntityPig high = new EntityPig(ext);
        high.setLocationAndAngles(x + 0.5D, 300, z + 0.5D, 0, 0);
        ext.spawnEntityInWorld(high);
        Chunk chunk = ext.getChunkFromBlockCoords(x, z);
        int lowIndex = -7 - range.minSection(), highIndex = 18 - range.minSection();
        report.check(
            "1.8 entity at y -100: chunkCoordY -7, in the list of section -7",
            low.chunkCoordY == -7 && chunk.entityLists[lowIndex].contains(low),
            low.chunkCoordY);
        report.check(
            "1.8 entity at y 300: chunkCoordY 18, in the list of section 18",
            high.chunkCoordY == 18 && chunk.entityLists[highIndex].contains(high),
            high.chunkCoordY);
        List<?> nearLow = ext.getEntitiesWithinAABB(
            EntityPig.class,
            AxisAlignedBB.getBoundingBox(x - 2, -102, z - 2, x + 3, -98, z + 3));
        List<?> mid = ext
            .getEntitiesWithinAABB(EntityPig.class, AxisAlignedBB.getBoundingBox(x - 2, 0, z - 2, x + 3, 200, z + 3));
        report.check(
            "1.8 AABB search finds the entity at y -100 and nothing between 0 and 200",
            nearLow.contains(low) && mid.isEmpty(),
            nearLow.size() + " / " + mid.size());
        low.setPosition(x + 0.5D, -50, z + 0.5D);
        ext.updateEntityWithOptionalForce(low, false);
        report.check(
            "1.8 entity moved to y -50 changes to the list of section -4",
            low.chunkCoordY == -4 && chunk.entityLists[-4 - range.minSection()].contains(low)
                && !chunk.entityLists[lowIndex].contains(low),
            low.chunkCoordY);
        low.setDead();
        high.setDead();

    }
}
