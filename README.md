# ExtendedHeight

Мод-библиотека для Minecraft 1.7.10 (Forge 10.13.4.1614): расширенный вертикальный диапазон мира для отдельных
измерений, например y −256…319. Измерения, не включившие расширение, работают как в ванили — по поведению и по
формату сохранений.

> **Статус: вехи 1–2 из 5** (хранение, World API, сохранение, свет — на сервере). Сеть, клиентский рендер,
> сущности и API генератора ещё не готовы. Играть в расширенном мире на клиенте пока нельзя.
> Подробности — в [docs/patch-map.md](docs/patch-map.md), замеры — в [docs/perf.md](docs/perf.md).

## Подключение

Пока артефакт не опубликован, его можно собрать локально (`gradlew build`) или подключить через mavenLocal
(`gradlew publishToMavenLocal`). Затем в `dependencies.gradle` своего мода:

```groovy
dependencies {
    implementation("io.github.extendedheight:extendedheight:<версия>:dev")
}
```

Мод использует миксины через UniMixins: UniMixins нужен в рантайме.

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

## Известные ограничения

- Моды, которые индексируют `chunk.getBlockStorageArray()[y >> 4]` напрямую, в расширенном мире промахиваются
  при y < 0. Правильно — перебирать массив и брать `ebs.getYLocation()`.
- Расширенное сохранение, открытое **без мода**, падает при загрузке чанков (ванильный загрузчик не знает секций
  с Y < 0 и Y > 15).
- `PathPoint.makeHash` упаковывает y в 8 бит; коллизия возможна только у точек на расстоянии 256 по вертикали,
  что для поиска пути недостижимо.

## Совместимость

Предварительный список, проверка будет в вехе 5: OptiFine, FastCraft, Angelica (свои рендереры и свет),
ArchaicFix/Hodgepodge/CoreTweaks (миксины в `Chunk` и свет), NotEnoughIDs/EndlessIDs/ChunkAPI (формат секций и
пакетов чанков). Цель — как минимум не ломать миры без расширения.

## Разработка и тесты

- `gradlew test` — юнит-тесты (индексация секций, ограничения диапазона).
- `powershell -ExecutionPolicy Bypass -File scripts/selftest.ps1` — серверный самотест: четыре запуска dev-сервера
  (базовый без миксинов, мод без расширенных миров, запись в расширенное измерение, перезапуск и чтение).
  Отчёты лежат в `run/selftest/*/selftest-result-*.txt`. Тестовые измерения 4242/4244 регистрируются только при
  наличии файла `extendedheight-selftest.properties` в рабочей папке сервера.
