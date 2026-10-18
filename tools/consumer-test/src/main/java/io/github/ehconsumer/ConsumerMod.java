package io.github.ehconsumer;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.init.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldType;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartedEvent;
import io.github.extendedheight.api.ExtendedHeight;
import io.github.extendedheight.api.HeightRange;

/** Только публичный API: тип мира -256..319, на старте сервера проверка блоков ниже 0 и выше 255 */
@Mod(modid = "ehconsumer", name = "EH Consumer Test", version = "1.0.0",
    dependencies = "required-after:extendedheight@[1.0,)")
public class ConsumerMod {

    static WorldType type;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        type = new WorldType("ehconsumer");
        ExtendedHeight.register(type, -256, 319);
    }

    @Mod.EventHandler
    public void serverStarted(FMLServerStartedEvent event) {
        MinecraftServer server = MinecraftServer.getServer();
        List<String> lines = new ArrayList<>();
        boolean ok = true;
        WorldServer world = server.worldServerForDimension(0);
        HeightRange range = ExtendedHeight.getRange(world);
        ok &= check(lines, "API version 1.x", ExtendedHeight.API_VERSION.startsWith("1."), ExtendedHeight.API_VERSION);
        ok &= check(lines, "overworld range -256..319", range.equals(HeightRange.of(-256, 319)), range);
        int[] ys = { -256, -100, -1, 0, 255, 256, 300, 319 };
        for (int y : ys) {
            world.setBlock(0, y, 0, Blocks.gold_block, 0, 3);
            ok &= check(lines, "set/get block at y " + y, world.getBlock(0, y, 0) == Blocks.gold_block,
                world.getBlock(0, y, 0));
        }
        ok &= check(lines, "y -257 and 320 are outside", !world.setBlock(0, -257, 0, Blocks.stone, 0, 3)
            && !world.setBlock(0, 320, 0, Blocks.stone, 0, 3), null);
        lines.add("OVERALL: " + (ok ? "PASS" : "FAIL"));
        try (PrintWriter out = new PrintWriter("consumer-result.txt", "UTF-8")) {
            lines.forEach(out::println);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        server.initiateShutdown();
    }

    private static boolean check(List<String> lines, String name, boolean ok, Object actual) {
        lines.add((ok ? "PASS " : "FAIL ") + name + (ok ? "" : " (actual: " + actual + ")"));
        return ok;
    }
}
