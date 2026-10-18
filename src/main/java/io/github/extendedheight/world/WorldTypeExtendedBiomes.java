package io.github.extendedheight.world;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.IChunkProvider;

import io.github.extendedheight.api.ExtendedChunkPrimer;
import io.github.extendedheight.api.ExtendedHeight;

/** Тип мира extendedtest с биомами по высоте: ниже 0 болото, от 256 ледяные равнины; демо-места в (0, 0) */
public class WorldTypeExtendedBiomes extends WorldTypeExtendedTest {

    /** Не больше 16 символов (level-type и пакет входа) */
    public static final String NAME = "extendedbiomes";
    public static final int SURFACE_Y = TOP_Y;
    public static final int CAVE_FLOOR_Y = -40;
    public static final int PLATFORM_Y = 270;
    /** Ниже этого y — болото, от SNOW_Y — ледяные равнины, между — биом столбца */
    public static final int SWAMP_BELOW = 0;
    public static final int SNOW_Y = 256;

    private static WorldTypeExtendedBiomes instance;

    private WorldTypeExtendedBiomes() {
        super(NAME);
    }

    static void registerType() {
        if (instance == null) {
            instance = new WorldTypeExtendedBiomes();
            ExtendedHeight.register(instance, MIN_Y, MAX_Y);
        }
    }

    public static WorldTypeExtendedBiomes biomesInstance() {
        return instance;
    }

    /** Биом по высоте этого типа мира (null — биом столбца) */
    public static BiomeGenBase biomeAt(int y) {
        if (y < SWAMP_BELOW) {
            return BiomeGenBase.swampland;
        }
        return y >= SNOW_Y ? BiomeGenBase.icePlains : null;
    }

    @Override
    public BiomeGenBase getBiome(World world, int x, int y, int z, BiomeGenBase columnBiome) {
        return biomeAt(y);
    }

    @Override
    public IChunkProvider getChunkGenerator(World world, String generatorOptions) {
        return new BiomesGenerator(world);
    }

    static class BiomesGenerator extends Generator {

        BiomesGenerator(World world) {
            super(world);
        }

        @Override
        protected void addFeatures(ExtendedChunkPrimer p, int cx, int cz) {
            if (cx != 0 || cz != 0) {
                return;
            }
            // Поверхность: лужа в траве, на ней блок листвы
            demoSpot(p, SURFACE_Y);
            // Пещера: воздух над травяным полом, светокамень в потолке, шахта наверх
            for (int x = 3; x <= 13; x++) {
                for (int z = 3; z <= 13; z++) {
                    for (int y = CAVE_FLOOR_Y + 1; y <= CAVE_FLOOR_Y + 5; y++) {
                        p.setBlock(x, y, z, Blocks.air, 0);
                    }
                    p.setBlock(x, CAVE_FLOOR_Y, z, Blocks.grass, 0);
                    if (x % 3 == 1 && z % 3 == 1) {
                        p.setBlock(x, CAVE_FLOOR_Y + 6, z, Blocks.glowstone, 0);
                    }
                }
            }
            for (int x = 12; x <= 13; x++) {
                for (int z = 12; z <= 13; z++) {
                    for (int y = CAVE_FLOOR_Y + 6; y <= SURFACE_Y; y++) {
                        p.setBlock(x, y, z, Blocks.air, 0);
                    }
                }
            }
            demoSpot(p, CAVE_FLOOR_Y);
            // Платформа: слой травы с камнем под водой
            for (int x = 3; x <= 13; x++) {
                for (int z = 3; z <= 13; z++) {
                    p.setBlock(x, PLATFORM_Y, z, Blocks.grass, 0);
                }
            }
            demoSpot(p, PLATFORM_Y);
        }

        /** Вода x/z 4..6 в травяном полу на floorY, листва x 9..11 / z 4..6 */
        private static void demoSpot(ExtendedChunkPrimer p, int floorY) {
            for (int x = 4; x <= 6; x++) {
                for (int z = 4; z <= 6; z++) {
                    p.setBlock(x, floorY - 1, z, Blocks.stone, 0);
                    p.setBlock(x, floorY, z, Blocks.water, 0);
                }
            }
            Block leaves = Blocks.leaves;
            for (int x = 9; x <= 11; x++) {
                for (int z = 4; z <= 6; z++) {
                    for (int y = floorY + 1; y <= floorY + 2; y++) {
                        // Мета 4: дуб без проверки гниения
                        p.setBlock(x, y, z, leaves, 4);
                    }
                }
            }
        }
    }
}
