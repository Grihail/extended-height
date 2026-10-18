# Совместимость с Reverworld: разведка и план

Reverworld и ExtendedHeight — два отдельных мода. Reverworld зависит от библиотеки ради расширенной высоты,
библиотека о Reverworld ничего не знает. Reverworld сейчас — мод под 1.12.2 + Cubic Chunks (`P:\Personal\Coding\Reverworld`),
его перенесут на 1.7.10 отдельно. Этот документ — результат чтения его исходников (ничего не изменено и не собиралось)
и план того, что нужно сделать **в библиотеке**. API пока не меняется: план ждёт одобрения.

Ссылки вида `RW:файл` — `src/main/java/com/grihail/reverworld/<файл>` в Reverworld, `EH:файл` — эта библиотека.

## 0. Что Reverworld делает сейчас (1.12.2)

- **Один генератор на всё.** `RW:world/ReverworldGenerator` (seed + `GenSettings`) владеет пустыней (`oasis/OasisDesert`),
  слоями (`ReverworldTerrain`: бедрок, потолок), висящими чашами (`HangingPlates`) и декором (`ReverworldDecorator`).
  Игра и Studio — тонкие адаптеры: `CubeGeneratorReverworld` (Cubic Chunks), `ChunkGeneratorReverworld` (ванильный
  запасной, обрезан до 0…255), `src/studio` (превью регионов).
- **Генерация по секциям 16³:** `generateSection(sx, sy, sz, columns, SectionWriter out, kinds)`,
  `SectionWriter.set(lx, ly, lz, IBlockState)`. Декор — по «области популяции» (секция со сдвигом +8 по всем осям)
  через `GenBlocks` (мир игры или сетка превью). Деревни — только в игре (`HangingPlates.populateVillages(World…)`,
  `PlateVillage` на `StructureVillagePieces`).
- **Высота** задаётся через Cubic Chunks: `HeightLimitHandler` подменяет `CubicChunksConfig.defaultMin/MaxHeight` в момент
  инициализации мира и поднимает `MinecraftServer.buildLimit` до 320.
- **Биомы:** у колонки — `reverworld:hanging_plates` (мир вверх ногами), у кубов выше `LOWER_TOP` (y −46) — биомы пустыни
  (desert, jungle edge, river) через пер-кубовые биомы Cubic Chunks (`CubeGeneratorReverworld.generateCube`).
- **Спавн:** начальный — `SpawnHandler` (`WorldEvent.CreateSpawnPosition`), при генерации — `initialWorldGenSpawn`
  только для областей с поверхностью пустыни; обычный спавн — по биому из `world.getBiome(pos)` (с y благодаря CC).
- **Клиент:** `client/UpsideDownSkyRenderer` (`IRenderHandler` неба: ванильное небо, а под потолком мира вверх ногами —
  чёрная нижняя полусфера), `client/ClientEvents` (`FogColors`: под потолком цвет тумана — дневной, а не тёмный
  «пустотный»), `WorldTypeReverworld.getHorizon` = `MIN_Y − 64`, `getCloudHeight` = `MAX_Y + 10`.

Наблюдение по рабочему дереву Reverworld: `ReverworldDecorator` импортирует `world.caves.CaveDecorator`, но такого
файла в `world/caves` нет (там `FastNoise`, `INoiseLibrary`, `OpenSimplex2S`); в git-статусе только неотслеживаемый
`worldgen-files.zip`. Вероятно, код пещер в работе — к библиотеке не относится, но перенос начинать с компилируемого
состояния.

## 1. Подключение

**Сборка.** GTNH ExampleMod/RFG библиотеки выпускает: `extendedheight-<v>.jar` (обфусцированный, для игры), `-dev.jar`
(деобфусцированный, для dev-окружения других модов), `-api.jar` (только пакет `api`, исходники и классы), `-sources.jar`.
Reverworld (если его порт будет на том же шаблоне GTNH) подключает:

```groovy
// dependencies.gradle Reverworld
// компиляция только против API: изменения внутренностей библиотеки не задевают Reverworld
compileOnly("io.github.extendedheight:extendedheight:<v>:api")
// библиотека целиком в dev-запусках (runClient/runServer) — с миксинами
devOnlyNonPublishable("io.github.extendedheight:extendedheight:<v>:dev")
```

Пока артефакт не опубликован: `gradlew publishToMavenLocal` в библиотеке и `mavenLocal()` в репозиториях Reverworld,
или `devOnlyNonPublishable(rfg.deobf(files("libs/extendedheight-<v>.jar")))`. **Не проверено**, что миксины библиотеки
работают в dev-окружении другого проекта (refmap, UniMixins в dev) — это шаг плана (§8, шаг 2).

**Объявление зависимости.**

```java
@Mod(modid = "reverworld", ..., dependencies = "required-after:extendedheight@[1.0,)")
```

`required-after` даёт и порядок загрузки (pre-init библиотеки раньше), и понятную ошибку FML, если библиотеки нет.
В `mcmod.info` — `"dependencies": ["extendedheight"]`, `"useDependencyInformation": true` (необязательно, FML берёт из
`@Mod`). Версия в диапазоне требует, чтобы библиотека имела семантическую версию — сейчас у неё версия из git
(`7fc2496-master+…-dirty`), см. §7.

**Регистрация типа мира** — в pre-init Reverworld, до загрузки любого мира:

```java
worldType = new WorldTypeReverworld();                 // конструктор WorldType регистрирует тип
ExtendedHeight.register(worldType, -256, 319);         // EH:api/ExtendedHeight — только измерение 0 миров этого типа
```

Диапазон фиксируется при первом создании мира и хранится в `level.dat` (тег `ExtendedHeight`); старые миры без записи
остаются ванильными навсегда (patch-map 0.5). Из Reverworld уходят: `HeightLimitHandler` (высоту даёт библиотека,
лимит строительства — тоже, patch-map 2.16), зависимость от Cubic Chunks, `ICubicWorldType`. `MAX_Y = 320` в
Reverworld — исключающая граница; у библиотеки `maxY = 319` включительно.

## 2. Чего не хватает API для генератора

| Нужно Reverworld | Есть сейчас | Не хватает |
| --- | --- | --- |
| Заполнить колонку чанка на весь диапазон | Только вручную: `new Chunk(world, cx, cz)`, затем `chunk.getBlockStorageArray()` с индексом `range.sectionIndex(y)` и `new ExtendedBlockStorage(y >> 4 << 4, sky)` — так делает `EH:world/WorldTypeExtendedTest` | **`ExtendedChunkPrimer`** (patch-map 8.1): `set(x, y, z, block, meta)` с реальным y, `get`, секционная запись под генерацию по 16³ (у Reverworld `SectionWriter(lx, ly, lz, …)` → `primer.section(sy).set(lx, ly, lz, …)`), `toChunk(world, cx, cz)` с `generateSkylightMap`. Ванильный `Chunk(World, Block[], byte[], x, z)` в расширенном мире кладёт свои 256 блоков на y 0…255 — для −256…319 не годится |
| Populate во всём диапазоне | `World.setBlock/getBlock` работают во всём диапазоне (вехи 1–2); `IChunkProvider.populate(provider, cx, cz)` вызывается на колонку, Reverworld внутри сам идёт по секциям (как его `ChunkGeneratorReverworld.populate`) | Ничего обязательного; в README — пример цикла по `range.minSection()…maxSection()` со сдвигом области +8 и замечание про порядок популяции соседей (в 1.7.10 по колонкам, не по кубам) |
| minY/maxY | `ExtendedHeight.getRange(world)`, `getMinY`, `getMaxY`, `isExtended`; `HeightRange.sectionIndex` | Ничего. Studio работает без мира — Reverworld держит свои константы (`ReverworldTerrain.MIN_Y/MAX_Y`) и сверяет их с библиотекой в адаптере |
| Спавн мобов во всём диапазоне | `WorldServer.spawnRandomCreature` передаёт y в `IChunkProvider.getPossibleCreatures(type, x, y, z)` — список по высоте может вернуть генератор | **Библиотека:** `SpawnerAnimals.func_151350_a` берёт случайный y как `nextInt(topFilledSegment + 15)` — **ниже 0 мобы не появляются никогда**, а при верхе чанка ≤ −16 — `IllegalArgumentException` (patch-map 7.6). Плюс 7.3–7.5, 7.8 (клиентское падение, `preparePlayerToSpawn`, падающие блоки, `PathFinder.getSafePoint`) |
| Начальный спавн | `WorldType.getMinimumSpawnHeight`, Forge `WorldEvent.CreateSpawnPosition` — оба есть в 1.7.10 | Ничего (Reverworld оставляет свой `SpawnHandler`) |
| Лимит строительства | В расширенном мире = `maxY + 1` (2.16) | Ничего |

## 3. Биомы по высоте

**Проблема.** В 1.7.10 биом один на колонку: `Chunk.blockBiomeArray` 16×16 байт, NBT `Biomes`, 256 байт в S21/S26,
`World.getBiomeGenForCoords(x, z)` без y. У Reverworld над бедроком — пустыня (desert, jungle edge, river), под ним —
`hanging_plates` (климат и цвета равнин, без дождя). От биома зависят спавн мобов, цвет травы и листвы, цвет воды, цвет
неба, погода, имя в F3, словарь биомов Forge и проверки чужих модов.

**Что в 1.7.10 уже получает y** (проверено по исходникам Forge 1.7.10-10.13.4.1614):

- спавн: `WorldServer.spawnRandomCreature(type, x, y, z)` → `IChunkProvider.getPossibleCreatures(type, x, y, z)`
  (+ Forge-событие `PotentialSpawns` с y);
- цвет травы и листвы: `BlockGrass/BlockLeaves.colorMultiplier` → `biome.getBiomeGrassColor(x, y, z)` /
  `getBiomeFoliageColor(x, y, z)` (подпись Forge);
- температура (снег, лёд, вид осадков, цвет неба): `biome.getFloatTemperature(x, y, z)` в `World.canBlockFreezeBody`,
  `canSnowAtBody`, `getSkyColorBody`, `EntityRenderer.renderRainSnow`;
- цвет тумана — `WorldProvider.getFogColor`, плюс событие `EntityViewRenderEvent.FogColors` (с позицией камеры).

**Что y не получает:** цвет воды (`waterColorMultiplier` по колонке), имя биома в F3, `BiomeDictionary`, выбор биома
структурами и чужими генераторами (`IWorldGenerator`), `World.getBiomeGenForCoords` у чужих модов.

### Варианты

| | Вариант | Что делать | Объём | Риски |
| --- | --- | --- | --- | --- |
| **А** | Биом по высоте в библиотеке | Массив биомов по секциям (16×16×N или 4×4×4 как в 1.15+); NBT (новый тег рядом с `Biomes`, ванильный оставить для совместимости); S21/S26 расширенных пакетов; `World.getBiomeGenForCoords(x, y, z)` новым API; патчи всех мест, где игра знает y: спавн, цвета (клиент `ChunkCache` тоже), температура и погода, небо; `BiomeGenBase` по позиции камеры в F3; генерационные проверки (`WorldChunkManager`). Ванильные вызовы `getBiomeGenForCoords(x, z)` продолжают видеть «биом колонки» | **L** (десятки точек патча, формат сохранения и сети, клиент) | Высокий: новый формат сохранения (откат на старую версию теряет данные), чужие моды видят только биом колонки — несогласованность; конфликт с модами, меняющими биомы (EndlessIDs/NEID расширяют ID биомов) |
| **Б** | Один биом на колонку, высоту решает Reverworld | Колонка получает биом пустыни; Reverworld регистрирует свои подклассы этих биомов (или один «составной» биом на каждый биом пустыни), которые по y переопределяют `getBiomeGrassColor/FoliageColor(x, y, z)`, `getFloatTemperature(x, y, z)` (ниже бедрока — равнины, без дождя); спавн — `getPossibleCreatures(type, x, y, z)` генератора возвращает списки `hanging_plates` ниже бедрока; туман и небо — уже свои (`IRenderHandler`, `FogColors`) | **S–M**, вся работа в Reverworld, библиотеке — 0 кода | Цвет воды, F3, `BiomeDictionary` и чужие моды под бедроком видят пустыню; чужой спавн по биому (`EntityRegistry.addSpawn` в desert) не попадёт под бедрок, если Reverworld не смешает списки сознательно. Погода: дождь под бедрок не доходит (осадки — по `precipitationHeight`, это верх колонки), так что расхождение невидимо |
| **В** | Минимальный крючок библиотеки | Интерфейс `IHeightBiomeProvider` на `WorldType`/провайдере: «биом в точке (x, y, z)», который библиотека спрашивает только там, где сама патчит код с y (спавн, F3, цвет воды на клиенте), без хранения в чанке | **M** | Половинчатость: часть игры видит биом по высоте, часть — колонку; API, который потом трудно убрать |

### Рекомендация

**Вариант Б.** Почти всё, что видно игроку, в 1.7.10 Forge уже получает y (цвета, температура, спавн), а мир вверх
ногами отделён каменным потолком и бедроком — расхождения в воде, F3 и чужих модах там редко заметны. Это ноль
изменений формата сохранения и сети и ноль кода в библиотеке; библиотека даёт только документацию и пример. Вариант А
вернуть можно позже (как отдельная веха), если появится конкретная потребность — Б его не исключает. В отказывается:
он добавляет API без полноты.

Что библиотека делает для Б: раздел в README («Биомы по высоте в генераторе»: подкласс биома с методами `(x, y, z)`,
`getPossibleCreatures` по y, что y не получает) и, после 7.6, тест, что мобы реально спавнятся ниже 0 по списку
генератора.

## 4. Клиент: небо, туман, частицы пустоты

| Что | Как в 1.7.10 / в библиотеке | Что нужно Reverworld |
| --- | --- | --- |
| Небо | Forge `WorldProvider.setSkyRenderer(IRenderHandler)` есть; `RenderGlobal.renderSky` библиотека не трогает (её миксин `RenderGlobal` — только сетка рендереров и пометки) | Ничего. `UpsideDownSkyRenderer` переносится как есть (Tessellator 1.7.10 вместо `BufferBuilder`) |
| Горизонт | `WorldType.getHorizon(World)` (Forge) — уже переопределён Reverworld (`MIN_Y − 64`). В плане библиотеки 6.5 — «по умолчанию для расширенного мира minY» | Библиотека **не должна перебивать** переопределённый `getHorizon`. Решение: патчить только поведение по умолчанию (`WorldType.getHorizon` базового класса), а не `WorldProvider.getHorizon` — тогда переопределение типа мира всегда выигрывает |
| Облака | `WorldType.getCloudHeight` / `WorldProvider.getCloudHeight` есть; порядок отрисовки по `posY < cloudHeight` | Ничего (`MAX_Y + 10`) — проверить в 3b, что облака на 330 рисуются при камере выше и ниже |
| Цвет тумана | Ванильное затемнение (`updateFogColor`) в расширенных мирах по умолчанию выключено (`EhConfig.voidFog = false`); событие `FogColors` срабатывает **после** затемнения | Обработчик `FogColors` Reverworld работает и так; при `voidFog = false` он почти не нужен |
| Дальность тумана у дна | `setupFog` (не в творческом): выключено по умолчанию, при `voidFog = true` — от minY | Под бедроком Reverworld хочет обычный дневной туман — значение по умолчанию подходит, но **опция конфига игрока может включить его обратно**. Нужен способ миру сказать «у меня пустотного тумана нет» |
| Частицы пустоты | `doVoidFogParticles`: по умолчанию ниже y 8, половина ванильного количества; опции `voidParticlesFollowMinY`, `voidParticleIntensity` | Мир вверх ногами — огромная полость ниже 0, частицы там по умолчанию будут везде. Reverworld, вероятно, хочет «только у дна (minY)» или «нет». Нужен способ задать это для своего мира |

**Предложение для API (шаг плана 5):** клиентский интерфейс на `WorldType` или `WorldProvider`, например
`IVoidEffectsProvider { VoidEffects getVoidEffects(World world); }`, где `VoidEffects` — неизменяемые
`fog` (вкл/выкл/от minY), `particlesZoneTop` (y или «нет»), `particleIntensity`. Порядок: значение мира задаёт **поведение
по умолчанию** для этого мира; конфиг игрока остаётся множителем интенсивности и «выключателем» (игрок может только
убрать эффекты, но не навязать миру пустотный туман, который мир отключил). Для миров без интерфейса — как сейчас.

## 5. Ловушки для генератора

| Ловушка | Где | Кто учитывает |
| --- | --- | --- |
| `BiomeDecorator.genDecorations`: `nextInt(h × 2)`, `nextInt(h + 32)` через обёртку Forge — при h ≤ 0 декор только на y 0 | patch-map 8.3 (а) | Reverworld: **не вызывать** `biome.decorate()` (его `ReverworldDecorator` свой — так и есть сейчас). Если колонка получает ванильный биом (вариант Б), убедиться, что провайдер не зовёт `decorate` этого биома |
| `BiomeGenForest/Plains/Savanna/Taiga.decorate`: сырой `nextInt(h + 32)` → `IllegalArgumentException` при h ≤ −32 | 8.3 (б) | То же |
| Ванильные руды (`generateOres` в 0…64), озёра, данжи, `MapGenCaves/Ravine`, структуры `MapGen*` — только 0…255 | 8.3 (в) | Reverworld не использует (свои руды `ReverworldDecorator.ORES`, свои пещеры, деревни вручную) |
| **Чужие генераторы** (`GameRegistry.registerWorldGenerator` → `IWorldGenerator.generate` из `Chunk.populateChunk`) рассчитывают на 0…255: руды других модов окажутся в слое «под пустыней» (y ~2…52), в мир вверх ногами не попадут никогда | Forge | Reverworld: решить политику (отключать чужие генераторы в своём мире или нет); библиотека ничего не исправляет и не должна |
| `World.getTopSolidOrLiquidBlock(x, z)` — верх колонки (поверхность пустыни, ~63…310), а не поверхность чаши | исправлено 2.11 | Reverworld: деревни на чашах ставит по своей высоте земли (`PlateVillage` уже так делает); то же для `StructureVillagePieces.Village.getAverageGroundLevel` (в 1.7.10 — `max(getTopSolidOrLiquidBlock, provider.getAverageGroundLevel())`, то есть верх колонки) |
| `BlockFalling.fallInstantly` во время генерации (`while (y > 0)`) | patch-map 8.4, не исправлено | Библиотека (шаг 3). Reverworld и сейчас избегает гравия в висящем мире; песок пустыни лежит над пещерами выше 0 |
| Спавн мобов при генерации (`performWorldGenSpawning`) — по `getTopSolidOrLiquidBlock`, то есть только на верхней поверхности | 7.7 | Reverworld: мобы для чаш — свой спавн при популяции, если нужен |
| Обычный спавн: случайный y от 0 | 7.6 | Библиотека (шаг 3) |
| `Chunk(World, Block[], byte[], x, z)` кладёт массив на y 0…255 | 8.1 | Библиотека: `ExtendedChunkPrimer` (шаг 4) |
| Свет: генератор, пишущий секции напрямую, должен вызвать `generateSkylightMap()`; досвечивание — ванильное | вехи 1–2 | Reverworld (`toChunk` примера делает это сам) |
| `PathPoint.makeHash` — `y & 255`: коллизия точек пути на расстоянии 256 по y | 7.9 | Никто: дальность поиска пути на порядок меньше |

## 6. Раскладка высот Reverworld

| Слой | y (по `RW:world/ReverworldTerrain` и README Reverworld) |
| --- | --- |
| Верх мира | `MAX_Y = 320` (исключ.) → 319; горы пустыни до ~310, облака на 330 |
| Пустыня | поверхность ~62…182, уровень воды оазисов 63 (`DESERT_LEVEL = −8 + 71`), `DESERT_BASE = −8` |
| Под пустыней | ~2…52 (пещеры, озёра), ниже — камень до бедрока |
| Бедрок | ядро y −63, −62, «бахрома» до 2 блоков с каждой стороны → **−65…−60** (`BEDROCK_LEVEL = −62`) |
| Мир вверх ногами | ≥ 16 блоков камня под бедроком, нижняя поверхность потолка, чаши висят в 3…25 блоках под ним; вниз до −252 (`LOWEST_SURFACE = MIN_Y + 4`) |
| Низ мира | `MIN_Y = −256` |

В задании указан бедрок −64…−60; по коду и README Reverworld он −65…−60 (ядро −63…−62 плюс бахрома до 2 блоков). На
ограничения это не влияет.

**Ограничения библиотеки** (`EH:api/HeightRange.validate`): `minY` кратно 16 и ≤ 0 (−256 ✓), `maxY + 1` кратно 16 и
`maxY ≥ 255` (320 ✓), `minY ≥ −512` ✓, секций ≤ 64 (36 ✓). Ванильные 0…255 внутри диапазона ✓. **Раскладка
укладывается без изменений.** Лимит S26 при 36 секциях — 4 чанка на пакет (patch-map 5.A).

## 7. Стабильность API

- **Публичное — только пакет `io.github.extendedheight.api`** (`ExtendedHeight`, `HeightRange`, `IExtendedHeightProvider`
  и то, что добавит этот план). Всё остальное (`core`, `net`, `client`, `mixins`, `world`, `selftest`) — внутреннее, может
  меняться в любой версии; `-api.jar` содержит только `api` и потому сам это обеспечивает при компиляции против него.
- **Версия мода — семантическая** (`MAJOR.MINOR.PATCH`, git-тег `v1.0.0`), вместо текущей версии из git-хэша.
  Несовместимые изменения API — только с MAJOR; новые методы — MINOR. Удаление — через `@Deprecated` минимум на одну
  MINOR-версию.
- **Версия API отдельно:** FML-аннотация `@API(owner = "extendedheight", provides = "ExtendedHeightAPI", apiVersion = "1.0")`
  в `api/package-info.java` и константа `ExtendedHeight.API_VERSION`. Reverworld проверяет её в pre-init и пишет понятную
  ошибку при несовпадении MAJOR.
- **Сеть:** сервер с библиотекой требует у клиента **ту же версию** (`ExtendedHeightMod.rejectionReason`). Для
  сборки модпака это нормально; после 1.0 можно ослабить до «тот же MAJOR.MINOR» — отдельное решение.
- **Формат сохранения** (`level.dat` → `ExtendedHeight`, теги `Y` секций) — часть контракта: меняется только с MAJOR и с
  миграцией.
- **`mcmod.info`** библиотеки — до сих пор шаблон ExampleMod (описание «An example mod…», чужой URL): исправить.

## 8. План работ для библиотеки

Порядок выбран так, чтобы Reverworld мог начать перенос как можно раньше (шаги 1–2), а изменения API шли малыми
порциями с проверкой самотестом.

| # | Шаг | Объём | Риски |
| --- | --- | --- | --- |
| 1 | **Метаданные и версия:** настоящий `mcmod.info`, семантическая версия (тег), `@API` в `package-info`, `ExtendedHeight.API_VERSION`, README «Стабильность API» | S | Низкий. Версия меняет имя jar и диапазон в `dependencies` Reverworld |
| 2 | **Подключение из другого проекта:** `publishToMavenLocal`, минимальный проект-потребитель (тип мира + `register`) с `compileOnly(:api)` и `devOnlyNonPublishable(:dev)`; убедиться, что миксины библиотеки применяются в dev-окружении потребителя и в obf-сборке | S–M | Средний: refmap и UniMixins в чужом dev-окружении (так и сломался `World.<init>` — только obf-проверка надёжна) |
| 3 | **Сущности и спавн (часть вехи 4), нужная Reverworld:** 7.6 (случайный y спавна от minY), 7.3–7.5, 7.8, 8.4 (`fallInstantly`), 1.8 (оптимизация списков сущностей по секциям) — с проверками в самотесте (мобы появляются ниже 0 по `getPossibleCreatures` с y) | M | Средний: `SpawnerAnimals` — горячий путь; 7.6 сейчас может уронить тик мира с чанком, у которого верх ниже −16 |
| 4 | **`ExtendedChunkPrimer` (API):** запись по реальному y и посекционно, `toChunk`, тест: чанк из праймера = чанк, построенный `setBlock`; замер скорости против ванильного `Chunk(World, Block[], byte[], …)` | M | Низкий–средний: свет и `heightMap` нового чанка (сверять с `LightReference` самотеста) |
| 5 | **Клиентские эффекты мира (API):** `IVoidEffectsProvider` (§4); горизон 6.5 по умолчанию только для базового `WorldType.getHorizon`; проверка, что переопределённый `getHorizon` и свой `IRenderHandler` неба не трогаются | S–M | Низкий; порядок «мир против конфига игрока» — зафиксировать в README |
| 6 | **Документация для генераторов:** README «Что нужно учесть генератору» — биомы по высоте (вариант Б с примером подкласса биома), populate по секциям, чужие `IWorldGenerator`, `getTopSolidOrLiquidBlock`, `Village.getAverageGroundLevel` | S | Нет |
| 7 | Остальное из 3b (клиентский свет против серверного, погода и облака у границ, худший случай S26, производительность) и веха 5 (совместимость) — общий план библиотеки, не блокирует перенос | M–L | Высокий для веха 5 (OptiFine/FastCraft/Angelica), не для Reverworld |
| — | **Не делать сейчас:** биомы по высоте в библиотеке (вариант А) — только если вариант Б окажется недостаточным | L | см. §3 |

**Что мешает Studio при переносе** (это забота Reverworld, но влияет на то, как он будет звать библиотеку):

- генератор говорит на языке 1.12: `IBlockState`, `BlockPos`, `BlockStone.EnumType`, `Biome`, `MathHelper` из
  `net.minecraft.util.math` (`ReverworldTerrain`, `HangingPlates`, `PlateVillage`, `ReverworldDecorator`, `Landmark`),
  `SectionWriter(… IBlockState)` — в 1.7.10 это блок + метаданные; у Studio то же в `Palette` (карта `IBlockState → цвет`);
- `studio/Launcher` вызывает `Bootstrap.register()` — в 1.7.10 такого класса нет, а `Blocks` под Forge берёт блоки из
  реестра FML (`GameData`), который вне игры не инициализируется сам. Нужен способ получить блоки без FML или собственная
  абстракция материалов;
- у пустыни уже есть независимая от Minecraft абстракция материалов (`oasis/Mat`) — расширить её на весь генератор и
  переводить в блоки только в адаптерах (игра, Studio) убрало бы обе проблемы;
- **библиотеку звать только из адаптеров** (`ChunkProvider`/тип мира), а не из ядра генератора: тогда Studio не нужен
  jar библиотеки, а ядро не зависит от её версии. Нужное ядру (minY/maxY) — константы Reverworld, сверяемые с
  `ExtendedHeight.getRange(world)` в адаптере.

**Что нужно от пользователя:** одобрить план или поправить (особенно §3 — вариант Б, и §4 — форму `IVoidEffectsProvider`).
До одобрения API не меняется.
