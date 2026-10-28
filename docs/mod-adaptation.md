# Адаптация модов к ExtendedHeight

Как сделать, чтобы мод правильно работал в мирах с высотой ниже 0 и выше 255.
API: пакет `io.github.extendedheight.api` ([README, «API»](../README.md#api)).

## Подключение

```groovy
// dependencies.gradle
compileOnly("io.github.extendedheight:extendedheight:1.0.1:api")
```

| Зависимость | `@Mod(dependencies = ...)` | Вызовы API |
|---|---|---|
| Обязательная (генератор мира) | `required-after:extendedheight@[1.0,)` | напрямую |
| Необязательная | `after:extendedheight` | только за `Loader.isModLoaded("extendedheight")` |

```java
static int minY(World world) {
    return Loader.isModLoaded("extendedheight") ? ExtendedHeight.getMinY(world) : 0;
}
```

Без проверки `isModLoaded` при отсутствии мода — `NoClassDefFoundError`.

## Любой мод

Ванильный код (естественный спавн, ИИ, пути, свет, сеть) уже исправлен: мобы из `EntityRegistry.addSpawn` спавнятся
ниже 0 без доработок. Адаптировать нужно только код, где высота зашита.

| Ловушка | Замена |
|---|---|
| `rand.nextInt(256)`, `nextInt(world.getHeight())` — y от 0 | `minY + rand.nextInt(world.getHeight() - minY)` |
| `y < 0`, `y > 255`, `posY > 0` как граница мира | `ExtendedHeight.getMinY(world)` / `getMaxY(world)` |
| `(int) posY` — ниже 0 даёт блок выше | `MathHelper.floor_double(posY)` |
| `chunk.getBlockStorageArray()[y >> 4]` | `[ExtendedHeight.getRange(world).sectionIndex(y)]` |
| y байтом или `short`-маской секций в своих пакетах | `int` y, `long`-маска |
| `getBiomeGenForCoords(x, z)` там, где y известен | `ExtendedHeight.getBiome(world, x, y, z)` |
| `IWorldGenerator` (руды, постройки) | генерирует только в 0…255; для остального диапазона — свой проход |

## Генератор мира

Пример полного генератора — тип мира самотеста `extendedtest` (`selftest/WorldTypeExtendedTest`).
Разбор для Reverworld — [reverworld-compat.md](reverworld-compat.md).

### Диапазон

`ExtendedHeight.register(...)` в pre-init/init или `IExtendedHeightProvider` на `WorldProvider`.
Диапазон фиксируется при первом создании измерения ([README, «Когда диапазон применяется»](../README.md#когда-диапазон-применяется)).

### Чанк: `ExtendedChunkPrimer`

Ванильный `Chunk(World, Block[], byte[], x, z)` раскладывает массив только на 0…255.

```java
public Chunk provideChunk(int cx, int cz) {
    ExtendedChunkPrimer primer = new ExtendedChunkPrimer(world);
    HeightRange r = primer.range();
    for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
        for (int y = r.minY; y <= r.maxY; y++) primer.setBlock(x, y, z, blockAt(...), metaAt(...));
        primer.setBiome(x, z, columnBiome(...));
    }
    return primer.toChunk(world, cx, cz);   // секции, биомы, generateSkylightMap()
}
```

- y — настоящая высота; секции создаются только под не-воздух; один праймер — один чанк.
- По секциям 16³: `setBlock(x, sectionY * 16 + ly, z, ...)`.
- `generateSkylightMap` не сканирует верхний ряд верхней секции и нижний ряд мира (как ванильный y 255).

### Populate

`populate` вызывается на колонку, `World.getBlock/setBlock` работают во всём диапазоне. Декоратор по секциям:

```java
for (int sy = r.minSection(); sy <= r.maxSection(); sy++) decorate(world, cx * 16 + 8, sy * 16 + 8, cz * 16 + 8);
```

### Биом по высоте: `IBiomeAtProvider`

На `WorldProvider` или `WorldType` (измерение 0):

```java
public BiomeGenBase getBiome(World world, int x, int y, int z, BiomeGenBase columnBiome) {
    return y < BEDROCK_Y ? MY_LOWER_BIOME : null;   // null — биом колонки
}
```

- Вызывается на обеих сторонах и очень часто (цвета на вершину): дёшево и детерминированно.
- Учитывается в цветах блоков, F3, небе, погоде, спавне, слизнях, снеговиках, жителях, кровати, костной муке.
- Код, вызывающий `getBiomeGenForCoords(x, z)` напрямую (и другие моды), видит биом колонки.

### Спавн мобов

Естественный спавн берёт y от нижней до верхней непустой секции чанка (patch-map 7.6, Hodgepodge — 7.6a). Список мобов —
с учётом y:

```java
public List getPossibleCreatures(EnumCreatureType type, int x, int y, int z) {
    return ExtendedHeight.getBiome(world, x, y, z).getSpawnableList(type);
}
```

Спавн при генерации (`performWorldGenSpawning`) ставит мобов только на верх колонки; ниже — свой спавн в populate.

### Небо и пустота

| Что | Чем |
|---|---|
| Туман и частицы пустоты | `IVoidEffectsProvider` ([README, «Эффекты пустоты»](../README.md#эффекты-пустоты)) |
| Небо | Forge `WorldProvider.setSkyRenderer` |
| Горизонт | `WorldType.getHorizon` |

### Ловушки ванильной генерации

Библиотека их не чинит. h — `world.getHeightValue(x, z)`, в расширенном мире h может быть ≤ 0.

| Поведение | Где |
|---|---|
| Падают при h ≤ −32 (`nextInt(h + 32)`) — не вызывать `decorate()` | `BiomeGenForest`, `BiomeGenPlains`, `BiomeGenSavanna`, `BiomeGenTaiga` `.decorate` |
| Ставят декорации на y 0…2h, никогда ниже 0 | `BiomeDecorator.genDecorations`, `BiomeGenJungle.decorate` |
| Работают только в 0…255 | руды `BiomeDecorator`, `ChunkProviderGenerate.populate`, `MapGenCaves`, `MapGenRavine`, ванильные структуры, песок/глина/гравий |
| Дают верх колонки, а не уровень постройки | `World.getTopSolidOrLiquidBlock`, `Village.getAverageGroundLevel` |
| Работают при любом h | деревья, большие грибы |

Ниже 0: свой декоратор или вызов ванильного со сдвигом координат в 0…255 и переносом результата.
