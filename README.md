# ExtendedHeight

Мод-библиотека для Minecraft 1.7.10 (Forge 10.13.4.1614): расширенный вертикальный диапазон мира для отдельных
измерений, например y −256…319. Измерения, не включившие расширение, работают как в ванили — по поведению и по
формату сохранений.

> **Статус: вехи 1–2 и 3a из 5** (хранение, World API, сохранение, свет, сеть и рендер в минимальном объёме). В
> расширенный мир можно зайти и строить в нём (тестовый тип мира `extendedtest`). Не готовы: частицы и свет на
> клиенте, небо и туман у границ (3b), сущности выше 256 и мобы (4), совместимость (5).
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
  патчит игру через Mixin. Проверено с **UniMixins 0.3.1** (сборка `unimixins-0.3.1.jar`, «all-in-one»). Другие
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

Библиотека расширяет хранение и World API, но ванильный код генерации рассчитан на 0…255, и **библиотека его не
чинит**. Если генератор (например, Reverworld) зовёт ванильные декораторы для частей мира ниже 0, нужно учитывать
следующее. Здесь h — `world.getHeightValue(x, z)`; в расширенном мире h может быть ≤ 0.

**Падают (`IllegalArgumentException` из `Random.nextInt`) при h ≤ −32** — `nextInt(h + 32)` без защиты:

- `BiomeGenForest.decorate` — цветы/кусты;
- `BiomeGenPlains.decorate` — два вызова (цветы);
- `BiomeGenSavanna.decorate` — высокая трава;
- `BiomeGenTaiga.decorate` — папоротники/трава.

**Не падают, но ставят декорации на y 0…2h (никогда ниже 0, при h ≤ 0 — на y = 0)**. Эти методы идут через
Forge-обёртку `BiomeDecorator.nextInt` (`i ≤ 1 → 0`):

- `BiomeDecorator.genDecorations`: цветы, трава, мёртвые кусты, кувшинки, грибы, тростник, тыквы, кактусы
  (`nextInt(h × 2)` или `nextInt(h + 32)`);
- `BiomeGenJungle.decorate`: дыни (своя защита Forge `height < 1 → 1`).

**Работают только в 0…255:**

- `BiomeDecorator.generateOres` / `genStandardOre1` / `genStandardOre2`: фиксированные интервалы (уголь 0…128,
  алмазы 0…16 и т. д.);
- `ChunkProviderGenerate.populate`: озёра, лавовые озёра, данжи (`nextInt(256)` и подобные);
- `MapGenCaves`, `MapGenRavine`, ванильные структуры: работают с массивом `Block[]` высотой 256;
- `BiomeDecorator`: песок, глина и гравий у воды — через `getTopSolidOrLiquidBlock`, то есть на верхней поверхности
  колонки.

Что делать: для частей мира ниже 0 — свой декоратор или вызов ванильного с координатами, сдвинутыми в 0…255, а
затем перенос результата. Деревья и большие грибы ставятся на h — это работает при любом h.

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

Предварительный список, проверка будет в вехе 5: OptiFine, FastCraft, Angelica (свои рендереры и свет),
ArchaicFix/Hodgepodge/CoreTweaks (миксины в `Chunk` и свет), NotEnoughIDs/EndlessIDs/ChunkAPI (формат секций и
пакетов чанков). Цель — как минимум не ломать миры без расширения.

## Разработка и тесты

- `gradlew test` — юнит-тесты (индексация секций, ограничения диапазона).
- `powershell -ExecutionPolicy Bypass -File scripts/selftest.ps1 [-Env dev|obf|both] [-NoClient]` — самотест.
  Сервер запускается четыре раза: базовый прогон без миксинов, мод без расширенных миров, запись в расширенное
  измерение, перезапуск и чтение. Клиент запускается один раз: аудит миксинов, создание одиночного мира, выход.
  При `-Env both` (по умолчанию) всё выполняется дважды — в dev и в обфусцированной среде Forge с собранным
  reobf-jar и UniMixins, как в настоящей игре. Отчёты — в `run/selftest/<env>/*/selftest-result-*.txt`. Тестовые
  измерения 4242/4244 регистрируются только при наличии файла `extendedheight-selftest.properties` в рабочей
  папке. Нужно около 3 ГБ свободной памяти; при нехватке JVM падает с «insufficient memory», это не ошибка мода.
