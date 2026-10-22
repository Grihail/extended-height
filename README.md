# ExtendedHeight

Мод-библиотека для Minecraft 1.7.10 (Forge 10.13.4.1614): расширенный вертикальный диапазон мира для отдельных
измерений, например y −256…319. Измерения, не включившие расширение, работают как в ванили — по поведению и по
формату сохранений.

> **Версия 1.0.0.** Готовы: хранение, World API, сохранение, свет, сеть и рендер (3a), сущности и спавн ниже 0,
> API для генераторов (`ExtendedChunkPrimer`, `IBiomeAtProvider`, `IVoidEffectsProvider`). Не готовы: часть 3b (свет
> клиента против сервера, горизонт, погода и облака у границ, худший случай S26, замеры), совместимость с OptiFine и
> другими модами (веха 5).
> Подробности — в [docs/patch-map.md](docs/patch-map.md), замеры — в [docs/perf.md](docs/perf.md).

## Подключение

Пока артефакт не опубликован в общем репозитории, его ставят в mavenLocal: `gradlew publishToMavenLocal` в этом
проекте (координаты `io.github.extendedheight:extendedheight:1.0.0`, классификаторы `api`, `dev`, `sources` и
основной обфусцированный jar). В своём моде (шаблон GTNH ExampleMod / RFG):

```groovy
// repositories.gradle
repositories { mavenLocal() }

// dependencies.gradle
dependencies {
    compileOnly("io.github.extendedheight:extendedheight:1.0.0:api")            // компиляция только против API
    devOnlyNonPublishable("io.github.extendedheight:extendedheight:1.0.0:dev")  // библиотека с миксинами в runClient/runServer
}
```

`@Mod(dependencies = "required-after:extendedheight@[1.0,)")`. В игре и в obf-запусках (`runObfServer`/`runObfClient`) в
`mods` кладётся `extendedheight-1.0.0.jar` и UniMixins. Проверено проектом-потребителем `tools/consumer-test`
(свой тип мира −256…319, только API): миксины библиотеки работают и в его dev-окружении, и в obf.

### Требования

- Minecraft 1.7.10, Forge 10.13.4.1614.
- **[UniMixins](https://github.com/LegacyModdingMC/UniMixins)** — обязателен и в клиенте, и на сервере: мод
  патчит игру через Mixin. Проверено с **UniMixins 0.3.1 и 0.3.2** («all-in-one»). Другие
  версии не проверялись; моды, которые несут свой Mixin вместо UniMixins (старые SpongeMixins / MixinBooter),
  не поддерживаются.

## API

Пакет `io.github.extendedheight.api`.

```java
// pre-init / init, до загрузки миров:
ExtendedHeight.register(myDimensionId, -256, 319);       // по ID измерения
ExtendedHeight.register(MY_WORLD_TYPE, -256, 319);        // по типу мира (только измерение 0)

// или на своём WorldProvider:
public class MyProvider extends WorldProvider implements IExtendedHeightProvider {
    public HeightRange getExtendedHeightRange() { return HeightRange.of(-256, 319); }
}

// чтение:
HeightRange r = ExtendedHeight.getRange(world);   // HeightRange.VANILLA для обычных миров
int minY = ExtendedHeight.getMinY(world);         // 0 в ванили
int maxY = ExtendedHeight.getMaxY(world);         // 255 в ванили
world.getHeight();                                // maxY + 1 для расширенного мира
```

### Ограничения диапазона

- `minY` кратно 16, `minY ≤ 0`, `minY ≥ −512`;
- `maxY + 1` кратно 16, `maxY ≥ 255`;
- не больше 64 секций.

Ванильный диапазон 0…255 всегда внутри расширенного.

### Когда диапазон применяется

Диапазон фиксируется, **когда измерение создаётся впервые**, и записывается в `level.dat` (тег `ExtendedHeight`).
Если ни одно измерение не расширено, тег не пишется. Дальше всегда действует сохранённый диапазон.

Если у измерения уже есть сохранённые чанки (непустые `*.mca` в папке `region` этого измерения), а записи в
`level.dat` нет, оно **навсегда остаётся ванильным**: в лог пишется WARN, краша нет. Поэтому регистрироваться нужно
в pre-init/init, а не позже.

Приоритет запроса: `IExtendedHeightProvider` → регистрация по ID → регистрация по `WorldType` (для измерения 0).

### Версии и стабильность API

- Версия мода — семантическая: `MAJOR.MINOR.PATCH` (сейчас **1.0.0**, задаётся `modVersion` в `gradle.properties`).
  Зависимость объявляется так: `@Mod(dependencies = "required-after:extendedheight@[1.0,)")`.
- Публичное API — **только пакет `io.github.extendedheight.api`** (он же целиком — `-api.jar`). Остальные пакеты
  (`core`, `net`, `client`, `mixins`, `world`, `selftest`) внутренние и могут меняться в любой версии.
- Версия API отдельно: `ExtendedHeight.API_VERSION` и FML-аннотация `@API(provides = "ExtendedHeightAPI",
  apiVersion = "1.0")` на пакете. MAJOR меняется только при несовместимых изменениях (удалённые или изменённые методы,
  другой формат сохранения); MINOR — при добавлениях. Удаление — через `@Deprecated` минимум на одну MINOR-версию.
- Формат сохранения (тег `ExtendedHeight` в `level.dat`, отрицательные теги `Y` секций) — часть контракта, меняется
  только с MAJOR.
- Сеть: сервер с модом требует у клиента ту же версию мода.

## Что нужно учесть генератору

Пример полного генератора — тип мира `extendedtest` (`world/WorldTypeExtendedTest`), а `extendedbiomes`
(`world/WorldTypeExtendedBiomes`) — то же плюс биомы по высоте через `IBiomeAtProvider` (ниже 0 — болото, от 256 —
ледяные равнины) и демо-площадки с травой, листвой и водой на y 63, −40 и 270 у колонны на x/z 0; подробный разбор для Reverworld —
[docs/reverworld-compat.md](docs/reverworld-compat.md).

### Колонка чанка на весь диапазон: `ExtendedChunkPrimer`

Ванильный `Chunk(World, Block[], byte[], x, z)` раскладывает массив на y 0…255 — для расширенного мира не годится.

```java
public Chunk provideChunk(int cx, int cz) {
    ExtendedChunkPrimer primer = new ExtendedChunkPrimer(world);   // диапазон мира, небесный свет по провайдеру
    HeightRange r = primer.range();
    for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
        for (int y = r.minY; y <= r.maxY; y++) primer.setBlock(x, y, z, blockAt(cx, cz, x, y, z), metaAt(...));
        primer.setBiome(x, z, columnBiome(cx * 16 + x, cz * 16 + z));  // биом колонки (ванильный массив Biomes)
    }
    return primer.toChunk(world, cx, cz);   // секции, биомы, generateSkylightMap()
}
```

- y — настоящая высота мира; секции создаются только под не-воздух; один праймер строит один чанк.
- Генератор по секциям 16³ пишет `setBlock(x, sectionY * 16 + ly, z, …)`.
- Как и в ванили, `generateSkylightMap` не смотрит самый верхний ряд верхней заполненной секции (в ванили — y 255):
  одиночный блок ровно на верхнем ряду не попадёт в карту высот до первого изменения блока рядом. Нижний ряд мира
  (`minY`) тоже не сканируется (patch-map, «Статус вехи 2»).

### Populate во всём диапазоне

`IChunkProvider.populate(provider, cx, cz)` вызывается на колонку; `World.getBlock/setBlock` работают на всём
диапазоне. Генератор, декорирующий по секциям, проходит их сам:

```java
for (int sy = r.minSection(); sy <= r.maxSection(); sy++) decorate(world, cx * 16 + 8, sy * 16 + 8, cz * 16 + 8);
```

### Биом по высоте: `IBiomeAtProvider`

В 1.7.10 биом один на колонку. Мир с разными биомами по высоте реализует `IBiomeAtProvider` на `WorldProvider` или на
`WorldType` (измерение 0):

```java
public class MyWorldType extends WorldType implements IBiomeAtProvider {
    public BiomeGenBase getBiome(World world, int x, int y, int z, BiomeGenBase columnBiome) {
        return y < BEDROCK_Y ? MY_LOWER_BIOME : null;   // null — биом колонки (columnBiome)
    }
}
```

Ничего не хранится и не передаётся: биом вычисляется из позиции, на сервере и на клиенте, очень часто (цвета блоков
на каждую вершину) — ответ должен быть дешёвым и одинаковым на обеих сторонах. Библиотека спрашивает его везде, где игра
знает точку, но берёт биом по x/z: цвет воды, травы и листвы, F3, цвет неба у камеры, осадки и погода, спавн ванильных
генераторов, слизни, снеговики, жители, кровать, цветы от костной муки (полный список — docs/reverworld-compat.md §3).
`ExtendedHeight.getBiome(world, x, y, z)` возвращает тот же ответ. **Код, который спрашивает `getBiomeGenForCoords(x, z)`
напрямую (в том числе другие моды), видит биом колонки.**

### Спавн мобов

Естественный спавн выбирает точку во всём диапазоне (patch-map 7.6) и спрашивает список у генератора с y:

```java
public List getPossibleCreatures(EnumCreatureType type, int x, int y, int z) {
    return ExtendedHeight.getBiome(world, x, y, z).getSpawnableList(type);
}
```

Спавн при генерации (`SpawnerAnimals.performWorldGenSpawning`) ставит мобов на `getTopSolidOrLiquidBlock` — то есть
только на верхнюю поверхность колонки; для нижних уровней — свой спавн в populate.

### Эффекты пустоты

Туман и частицы пустоты мир задаёт через `IVoidEffectsProvider` (раздел «Конфиг и эффекты пустоты»); небо — Forge
`WorldProvider.setSkyRenderer`, горизонт — `WorldType.getHorizon` (библиотека их не трогает).

### Ловушки ванильного кода генерации (библиотека их не чинит)

Здесь h — `world.getHeightValue(x, z)`; в расширенном мире h может быть ≤ 0.

**Падают (`IllegalArgumentException` из `Random.nextInt`) при h ≤ −32** — `nextInt(h + 32)` без защиты:
`BiomeGenForest.decorate` (цветы/кусты), `BiomeGenPlains.decorate` (два вызова), `BiomeGenSavanna.decorate` (высокая
трава), `BiomeGenTaiga.decorate` (папоротники/трава). **Не вызывайте `biome.decorate()` для колонок, где h может быть
≤ −32.**

**Не падают, но ставят декорации на y 0…2h** (никогда ниже 0; при h ≤ 0 — на y 0) — через Forge-обёртку
`BiomeDecorator.nextInt` (`i ≤ 1 → 0`): `BiomeDecorator.genDecorations` (цветы, трава, кусты, кувшинки, грибы, тростник,
тыквы, кактусы), `BiomeGenJungle.decorate` (дыни).

**Работают только в 0…255:** `BiomeDecorator.generateOres` и `genStandardOre1/2` (фиксированные интервалы: уголь
0…128, алмазы 0…16), `ChunkProviderGenerate.populate` (озёра, лава, данжи), `MapGenCaves`, `MapGenRavine`, ванильные
структуры (`Block[]` высотой 256), песок/глина/гравий `BiomeDecorator` (по верхней поверхности).

**Другие моды:** генераторы `GameRegistry.registerWorldGenerator` (`IWorldGenerator`) рассчитаны на 0…255 — их руды и
постройки окажутся только в 0…255 расширенного мира. Решите, оставлять их в своём мире или нет.

**Поверхность колонки:** `World.getTopSolidOrLiquidBlock(x, z)` и `StructureVillagePieces.Village.getAverageGroundLevel`
дают верх колонки, а не уровень, на котором строит генератор, — для построек ниже верхней поверхности задавайте высоту
сами.

Что делать с ванильными декораторами для частей мира ниже 0: свой декоратор, или вызов ванильного с координатами,
сдвинутыми в 0…255, и перенос результата. Деревья и большие грибы ставятся на h — это работает при любом h.

## Сеть

Мод нужен и на сервере, и на клиенте одной версии: сервер с модом отказывает клиенту без него с понятным
сообщением. Клиент с модом заходит на серверы без мода (там все миры ванильные). Для расширенных миров используются
новые ID пакетов в состоянии PLAY (S→C 0x60–0x64, 0x66; C→S 0x60–0x61); при коллизии с другим модом игра не
стартует, в тексте ошибки — занявший ID класс. Моды, которые сами создают `S21PacketChunkData` для чанка расширенного
мира, получат ошибку: ванильный пакет вмещает только 16 секций.

## Конфиг и эффекты пустоты

Эффекты у дна расширенного мира (клиент): ванильное затемнение пустоты (туман и небо чернеют к дну, вне творческого
режима туман сжимается) и частицы пустоты («depthsuspend»). **Мир задаёт поведение по умолчанию, конфиг игрока может
только ослабить или убрать эффекты.** Ванильные миры всегда ведут себя как без мода.

Мир выбирает эффекты, реализуя `IVoidEffectsProvider` на `WorldProvider` (любое измерение) или на `WorldType`
(измерение 0 миров этого типа):

```java
public VoidEffects getVoidEffects(World world) {
    return VoidEffects.of(false, ExtendedHeight.getMinY(world), 0.3F); // без тумана, частицы только у дна, 30 %
    // VoidEffects.none() — ни тумана, ни частиц; null — встроенное поведение библиотеки
}
```

Встроенное поведение (мир ничего не выбрал): тумана нет, частицы ниже y 8 (как в ванили — значит, во всей части мира
ниже 0), половина ванильного количества.

`config/extendedheight.cfg`, раздел `void`:

| Опция | По умолчанию | Что делает |
| --- | --- | --- |
| `voidFog` | `auto` | `auto` — как решил мир (встроенное поведение: выключено); `off` — никогда; `on` — включить и в мирах без своего выбора (от `minY`). Включить туман в мире, который его выключил, нельзя. |
| `voidParticleIntensity` | `1.0` | Множитель количества частиц, которое показывает мир: 1 — как задумал мир, 0 — нет. |
| `voidParticlesFollowMinY` | `false` | Только для миров без своего выбора: частицы ниже `minY + 8` (у дна), а не ниже y 8. |

Конфиги прошлых сборок переводятся автоматически: `voidFog = true/false` → `on/auto`, `voidParticleIntensity` (раньше —
доля от ванили, 0.5 по умолчанию) → множитель (0.5 → 1.0).

## Известные ограничения

- Моды, которые индексируют `chunk.getBlockStorageArray()[y >> 4]` напрямую, в расширенном мире промахиваются
  при y < 0. Правильно — перебирать массив и брать `ebs.getYLocation()`.
- Расширенное сохранение, открытое **без мода**, падает при загрузке чанков (ванильный загрузчик не знает секций
  с Y < 0 и Y > 15).
- В расширенном чанке `entity.chunkCoordY` — реальная секция (может быть отрицательной), а списки
  `chunk.entityLists` идут от нижней секции мира: индекс = `chunkCoordY − minSection`. Моды, которые сами читают
  `entityLists[chunkCoordY]`, при отрицательной секции выйдут за массив; правильно — `World.getEntitiesWithinAABB`.
- `PathPoint.makeHash` упаковывает y в 8 бит; коллизия возможна только у точек на расстоянии 256 по вертикали,
  что для поиска пути недостижимо.

## Совместимость

**Версии.**
- Java: классы собраны под Java 8 (версия классов 52, API Java 9+ не используются) — мод рассчитан на Java 8 и новее.
  Проверен на Java 25 (lwjgl3ify 3.0.39) и в dev-окружении; отдельный запуск на «чистой» Java 8 не выполнялся.
- Minecraft 1.7.10, Forge 10.13.4.1614.

**Что нужно установить.**
- **[UniMixins](https://github.com/LegacyModdingMC/UniMixins)** (проверено с 0.3.2) — обязателен на клиенте и на сервере.
- Сам мод нужен и на сервере, и на клиенте, версии должны совпадать (иначе рукопожатие отклонит вход в расширенный мир).
- Других обязательных модов нет; Hodgepodge, GTNHLib, ArchaicFix и Angelica для работы **не нужны**.

**Angelica — необязательно.** Если Angelica есть в сборке, интеграция включается сама: миксины на её классы
(`mixins/early/client/angelica`) применяются только при её наличии (`MixinPlugin`), без неё никаких обращений к её
классам нет. Проверено с **Angelica 2.2.35** (расширенный мир −256…319, дальность прорисовки 32, шейдеры Complementary
Reimagined r5.9.3). Миксины привязаны к внутренним классам Angelica, поэтому другие версии нужно проверять.

**Другие моды.**
- **ArchaicFix:** освещение Phosphor (`enablePhosphor`, по умолчанию включено) **несовместимо** с модом — сервер падает
  при создании расширенного мира. В `config/archaicfix.cfg` нужно `B:enablePhosphor=false` (цена — более медленное
  освещение). Остальные функции ArchaicFix проверены (0.8.0).
- **Hodgepodge** (проверен 2.7.218): планировщик чанков (`throttleChunkGeneration`) рассылал ванильные пакеты S21;
  теперь любой ванильный S21 для расширенного мира подменяется расширенным пакетом, настройку выключать не нужно.
  Проверка `earlyChunkTileCoordinateCheck` (по умолчанию включена) считает допустимыми высоты 0–255 и **роняет игру**
  при создании тайл-энтити ниже нуля (печь в деревне на y −130). Нужно `B:earlyChunkTileCoordinateCheck=false` в
  `config/hodgepodge.cfg`; проверка лишь уточняет сообщение об ошибке, на игру не влияет.
- **CoreTweaks** (0.3.4.7), **GTNHLib** (0.11.54): проверены в сборке с этим модом.
- OptiFine, FastCraft, NotEnoughIDs/EndlessIDs/ChunkAPI (формат секций и пакетов): не проверялись.

**Что изменилось для игроков.**
- В расширенном мире **без Angelica** дальность прорисовки на ванильном рендерере ограничена **16 чанками**; сама
  настройка не меняется и сохраняется как была. С Angelica ограничения нет (проверено на 32).
- Ванильные пакеты чанков от любых модов в расширенном мире подменяются расширенными: клиент больше не падает
  (`ArrayIndexOutOfBoundsException` в `Chunk.fillChunk`).
- Обычные миры и их сохранения не затрагиваются.

## Разработка и тесты

- `gradlew test` — юнит-тесты (индексация секций, ограничения диапазона).
- `powershell -ExecutionPolicy Bypass -File scripts/selftest.ps1 [-Env dev|obf|both] [-NoClient]` — самотест.
  Сервер запускается четыре раза: базовый прогон без миксинов, мод без расширенных миров, запись в расширенное
  измерение, перезапуск и чтение. Клиент запускается один раз: аудит миксинов, создание одиночного мира, выход.
  При `-Env both` (по умолчанию) всё выполняется дважды — в dev и в обфусцированной среде Forge с собранным
  reobf-jar и UniMixins, как в настоящей игре. Отчёты — в `run/selftest/<env>/*/selftest-result-*.txt`. Тестовые
  измерения 4242/4244 регистрируются только при наличии файла `extendedheight-selftest.properties` в рабочей
  папке. Нужно около 3 ГБ свободной памяти; при нехватке JVM падает с «insufficient memory», это не ошибка мода.
