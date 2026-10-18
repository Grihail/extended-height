# Patch map: всё, что в 1.7.10 зависит от высоты мира

Источник: декомпилированные исходники, которые выдаёт RetroFuturaGradle
(`build/rfg/minecraft-src`, Forge 1.7.10-10.13.4.1614, MCP stable_12). Список собран поиском по
коду, а не по памяти. Искал: `>> 4` в индексах массивов, `256`/`255`, `< 0`/`>= 0`/`> 0` рядом с y,
`getHeight()`/`getActualHeight()`, `storageArrays`/`getBlockStorageArray`, `-64`, `63`/`64`,
маски `1 << l` и `65535`, узкие типы (`readByte`/`readUnsignedByte`/`readShort`) в пакетах.
Цвета, звук, GUI и прочий шум отфильтрован вручную.

Статус: **разведка, патчей ещё нет.** Колонка «Риск» — насколько вероятно, что патч сломает
что-то или окажется неполным, а не насколько важен сам пункт.

---

## 0. Архитектурные решения, от которых зависят все патчи

| # | Решение | Почему |
| --- | --- | --- |
| 0.1 | **Внутри чанка y остаётся реальным.** `ExtendedBlockStorage.yBase` = реальный y (−256, −240, …). Смещение есть только в индексе массива: `index = (y >> 4) − minSection`. | Моды массово зовут `chunk.getBlock(x, worldY, z)` и `ebs.getYLocation()`. Сдвиг локальных координат в чанке сломал бы их все. `getYLocation()` уже реальный, поэтому сохранение и случайные тики работают без правок. |
| 0.2 | **Доступ к `Chunk.storageArrays[y >> 4]` патчится редиректами доступа к элементам массива** (`@Redirect(at = @At(value = "FIELD", args = "array=get"/"array=set"))`) с проверкой границ, причём только в методах, где индекс вычислен из y. Циклы `for l < storageArrays.length` не трогаются. | Это один механизм вместо `@Overwrite` десятка методов, и он меньше конфликтует с FastCraft и ArchaicFix. Для ванильного мира (`minSection = 0`, 16 секций) редирект — тождественная функция. |
| 0.3 | **Ограничения диапазона:** `minY ≤ 0`, `maxY ≥ 255`, обе границы кратны 16 (`maxY + 1` кратно 16), `minY ≥ −512`, число секций ≤ 64. Цель −256…319 = 36 секций. | Ванильный диапазон 0…255 всегда внутри мира, поэтому код, который проверяет «y в 0…255», ничего не ломает, а только недорабатывает. ≤ 64 секций — маска влезает в `long`. `minY ≥ −512` гарантирует, что сентинел `-999` в `precipitationHeightMap` и однобайтовый тег `Y` не пересекаются с реальными значениями. |
| 0.4 | **Ванильные миры = ванильный код.** Горячие пути (`World.getBlock`, `Chunk.getBlock`) правятся через `@ModifyConstant` (`256` → поле мира, `expandZeroConditions` для `y < 0`): для ванильного мира поле равно 256/0. Остальное — `@Inject(HEAD, cancellable)` с ранним `return` для нерасширенных миров. | Нет аллокаций `CallbackInfo` в горячих путях. Поведение ванильных миров доказуемо прежнее, это проверяет тест байтового сравнения сохранений. |
| 0.5 | **Где хранится диапазон:** в `level.dat`, тег `ExtendedHeight` (таблица dim → minY/maxY), через миксин в `WorldInfo`. Тег пишется, **только если есть хоть одно расширенное измерение.** Диапазон фиксируется в `World.<init>` сразу после `provider.registerWorld(this)` и до `createChunkProvider()`, то есть до первого чанка. | Ванильный `level.dat` не меняется. Измерение, у которого уже есть регион-файлы, но нет записи в таблице, навсегда остаётся ванильным: расширение не включится задним числом. |
| 0.6 | **Опт-ин:** `ExtendedHeight.register(int dim, minY, maxY)`, `ExtendedHeight.register(WorldType, minY, maxY)` (только для dim 0) или интерфейс `IExtendedHeightProvider` на `WorldProvider`. | Вариант через `WorldType` нужен Reverworld. Вариант через интерфейс нужен модовым измерениям. |
| 0.7 | **Сеть:** мод обязателен на клиенте, если он есть на сервере (`@NetworkCheckHandler` с внятным сообщением). Клиент с модом может заходить на сервер без мода — тогда все миры ванильные. Пакеты в расширенном формате — **новые ID в `EnumConnectionState.PLAY`**. Они шлются только для расширенного мира и только если y вне 0…255. В остальных случаях летит ванильный пакет. | Новые ID обрабатываются в той же очереди, что и ванильные пакеты, поэтому порядок «чанк → изменение блока» сохраняется. У FML-каналов в 1.7.10 обработчики работают в netty-потоке, и порядок там не гарантирован. Ванильный сервер такие ID никогда не шлёт, поэтому клиент с модом его понимает. |
| 0.8 | **Сентинелы «ничего нет»:** пустая колонка `heightMap` = `minY` (в ванили 0), пустой чанк `getTopFilledSegment()` = `minY` (в ванили 0), «дождь некуда» = `minY` (в ванили −1), `getTopSolidOrLiquidBlock` «не нашли» = `minY − 1` (в ванили −1). | В расширенном мире 0 и −1 — настоящие координаты. Для ванильного мира все значения совпадают с прежними. |

---

## 1. Хранение — `Chunk`, `ExtendedBlockStorage`

| # | Место | Что зависит от высоты | Что делаем | Риск |
| --- | --- | --- | --- | --- |
| 1.1 | `Chunk.<init>(World,int,int)` | `new ExtendedBlockStorage[16]`, `entityLists = new List[16]` | Размер = число секций мира. Кэшируем `minSection` в `@Unique`-поле чанка. | Низкий |
| 1.2 | `Chunk.<init>(World,Block[],int,int)`, `<init>(World,Block[],byte[],int,int)` | Высота берётся как `length / 256`, индекс `j1 >> 4` | Индекс со смещением: ванильный генератор кладёт свои 0…255 на реальные y 0…255. Расширенный генератор работает через API (раздел 8). | Низкий |
| 1.3 | `Chunk.getBlock`, `getBlockMetadata` | `y >> 4 < storageArrays.length` и индекс `[y >> 4]` | Редирект доступа к массиву с проверкой границ; вне диапазона — `air`/0. **Горячий путь.** | Средний (производительность; FastCraft и ArchaicFix тоже трогают `Chunk`) |
| 1.4 | `Chunk.func_150807_a` (setBlock), `setBlockMetadata` | `storageArrays[y >> 4]` (чтение и запись) | Редирект get/set. `new ExtendedBlockStorage(y >> 4 << 4)` уже даёт реальный y. | Средний |
| 1.5 | `Chunk.getSavedLightValue`, `setLightValue`, `getBlockLightValue` | `storageArrays[y >> 4]` | Редирект. | Низкий |
| 1.6 | `Chunk.getTopFilledSegment` | Для пустого чанка возвращает 0 | Возвращать `minY`. Все вызывающие делают `+15` и идут вниз (разделы 3, 7). | Средний: моды могут считать 0 признаком пустоты |
| 1.7 | `Chunk.getAreLevelsEmpty` | Клэмп 0…255, индекс `k >> 4` | Клэмп `minY…maxY`, индекс со смещением. Используется в `ChunkCache` для рендера. | Низкий |
| 1.8 | `Chunk.addEntity`, `removeEntityAtIndex`, `getEntitiesWithinAABBForEntity`, `getEntitiesOfTypeWithinAAAB` | Индекс списка = `floor(posY / 16)` с клэмпом 0…15 | `chunkCoordY` остаётся реальной секцией с клэмпом `minSection…maxSection`; индекс списка = `chunkCoordY − minSection`. Сравнение в `World.updateEntityWithOptionalForce` работает без правок. | Средний: моды, читающие `entityLists[chunkCoordY]` напрямую, получат AIOOBE в расширенном мире |
| 1.9 | `Chunk.enqueueRelightChecks` | `4096 = 16 × 256`, `j = queued % 16` — индекс секции, `y = j << 4` | Лимит `sections × 256`, `y = minY + (j << 4)`. | Низкий |
| 1.10 | `Chunk.fillChunk` (клиент) | `1 << l` для `l < storageArrays.length` — маска `int` | Маска `long`, индекс = номер бита (раздел 5). | Высокий (формат сети) |
| 1.11 | `Chunk.getBlockStorageArray()` | Отдаёт сырой массив | Не меняется. Моды, которые индексируют `[y >> 4]`, в расширенном мире промахнутся на y < 0. Исправить нельзя — пункт для README. | Высокий для чужих модов, вне нашего контроля |
| 1.12 | `ExtendedBlockStorage` | Ничего не зависит, кроме `yBase` | Без изменений. | — |
| 1.13 | `EmptyChunk` | Заглушки, `getHeightValue` = 0 | Без изменений: заглушка возвращает `air`. | Низкий |

## 2. World API — `World`, `ChunkCache`, `WorldProvider`, сервер

| # | Место | Что зависит | Что делаем | Риск |
| --- | --- | --- | --- | --- |
| 2.1 | `World.getBlock` | `y >= 0 && y < 256` | `@ModifyConstant` → `minY`/`maxY+1` мира. **Горячий путь.** | Средний (производительность) |
| 2.2 | `World.blockExists` | То же | То же. Через него работают `/setblock`, `/summon`, `/testforblock` и `getBiomeGenForCoords`. | Низкий |
| 2.3 | `World.checkChunksExist` (а через него `doChunksNearChunkExist`) | `maxY >= 0 && minY < 256` | Границы мира. **Без этого не обновляется свет ниже 0** (`updateLightByType` → `doChunksNearChunkExist(…, 17)`), не работают течение и всплытие (`handleMaterialAcceleration`, `isMaterialInBB`) и тики. | Средний |
| 2.4 | `World.setBlock(x,y,z,Block,int,int)` | `y < 0`, `y >= 256` | Границы мира. | Низкий |
| 2.5 | `World.getBlockMetadata`, `setBlockMetadataWithNotify` | То же | То же. | Низкий |
| 2.6 | `World.getFullBlockLightValue`, `getBlockLightValue_do` | `y < 0` → 0, `y >= 256` → клэмп 255 | Клэмп `minY…maxY`. От этого зависит свет для спавна мобов. | Низкий |
| 2.7 | `World.getSkyBlockTypeBrightness` (клиент), `getSavedLightValue`, `setLightValue` | Клэмп и проверки 0/256 | Границы мира. | Низкий |
| 2.8 | `World.getTileEntity` | `y >= 0 && y < 256` | Границы мира (`setTileEntity`/`removeTileEntity` y не проверяют). | Низкий |
| 2.9 | `World.canBlockFreezeBody`, `canSnowAtBody` | `y >= 0 && y < 256` | Границы мира. | Низкий |
| 2.10 | `World.getBlockLightOpacity` (Forge) | `y < 0 \|\| y >= 256` | Границы мира. | Низкий |
| 2.11 | `World.getTopSolidOrLiquidBlock` | Цикл `k > 0`, «не нашли» = −1 | Цикл до `minY`, «не нашли» = `minY − 1`. Зовётся из: `SpawnerAnimals.performWorldGenSpawning`, `BiomeDecorator` (песок, глина), `Entity`/`WorldProvider` (точка респавна), деревень, храмов. | Средний |
| 2.12 | `World.getHeightValue`, `getChunkHeightMapMinimum` | 0, если чанк не загружен | `minY` для расширенного мира. | Низкий |
| 2.13 | `Chunk.getPrecipitationHeight` (+ `World.getPrecipitationHeight`) | Цикл `i1 > 0`, «нет» = −1 | Цикл до `minY`, «нет» = `minY`. От этого зависят дождь и снег на клиенте, молния, намерзание льда, `isRainingAt`. | Низкий |
| 2.14 | `WorldProvider.getHeight()` = 256, `getActualHeight()` = 256/128 | Верхняя граница для поршней, дверей, маяка, порталов, Forge-жидкостей | `maxY + 1` для расширенных миров. Плюс новый API `ExtendedHeight.getMinY(World)` — в 1.7.10 нет аналога. | Средний: моды используют `getHeight()` как «размер массива» |
| 2.15 | `ChunkCache` (кэш для рендера и AI): `getBlock`, `getBlockMetadata`, `func_72810_a`, `func_72812_a` (яркость), `getHeight()` = 256, `extendedLevelsInChunkCache` | Проверки 0…256 и клэмпы 255 | Границы мира. Без этого **рендер и пути мобов не видят блоков вне 0…255.** | Высокий |
| 2.16 | `MinecraftServer.buildLimit` (`max-build-height`, клэмп 64…256) → `NetHandlerPlayServer.processPlayerDigging`/`processPlayerBlockPlacement` (`y >= getBuildLimit()`) | Глобальный лимит строительства на сервер | В расширенном измерении сравнивать с `maxY + 1`. Иначе выше 255 молча нельзя строить и копать. | Средний |
| 2.17 | `ItemBlock.onItemUse` | `y == 255 && solid` → нельзя ставить | `y == maxY`. | Низкий |
| 2.18 | `World.<init>` (оба конструктора) | — | Точка фиксации диапазона: после `provider.registerWorld(this)`, до `createChunkProvider()`. Спавн-зона грузится внутри конструктора (`initialize`), поэтому позже нельзя. | Средний |
| 2.19 | `WorldInfo` (`<init>(NBTTagCompound)`, `updateTagCompound`), `DerivedWorldInfo` для других измерений | — | Хранение таблицы диапазонов (0.5). `WorldServerMulti` использует `DerivedWorldInfo`, поэтому таблица всегда читается из `WorldInfo` измерения 0. | Низкий |

## 3. Свет

| # | Место | Что зависит | Что делаем | Риск |
| --- | --- | --- | --- | --- |
| 3.1 | `Chunk.generateSkylightMap` | Циклы `l > 0` (heightMap) и `i1 > 0` (заливка), индекс `[i1 >> 4]` | Циклы до `minY`, индекс со смещением. Небесный свет стартует с верхней заполненной секции + 15: секции выше пустые, и `getSavedLightValue` для пустой секции отдаёт 15 по `canBlockSeeTheSky`. Значит, свет фактически «начинается с maxY», как и в ванили со 256. | Средний |
| 3.2 | `Chunk.generateHeightMap` (клиент) | `l > 0` | До `minY`. | Низкий |
| 3.3 | `Chunk.relightBlock` | **`heightMap[...] & 255`** (отрицательная высота превращается в мусор), циклы `i1 > 0`, индекс | Убрать маску для расширенного мира, циклы до `minY`, индекс со смещением. | Высокий: самое тонкое место света |
| 3.4 | `Chunk.func_150811_f` (досвечивание при `isLightPopulated`) | `l > 63 \|\| l > 0 && !flag1`, `i1 == 255 && l < 63` — эвристика уровня моря | Нижняя граница цикла — `minY`. Эвристику 63 оставить: это оптимизация, не корректность. | Средний |
| 3.5 | `Chunk.recheckGaps`, `checkSkylightNeighborHeight`, `updateSkylightNeighborHeight` | `doChunksNearChunkExist(x, 0, z, 16)` — y = 0 внутри мира | Без изменений: реальные высоты уже корректны. | Низкий |
| 3.6 | `World.updateLightByType` | Очередь кодирует **относительные** ±32 по каждой оси — абсолютных лимитов y нет | Без изменений; хватает 2.3 и 2.7. | Низкий |
| 3.7 | `World.computeLightValue` | `canBlockSeeTheSky` → `heightMap` | Без изменений (работает через 1.x и 3.x). | Низкий |
| 3.8 | `heightMap` / `heightMapMinimum` | «Нет блоков» = 0 | «Нет блоков» = `minY` (0.8). | Средний |

## 4. Сохранение

| # | Место | Что зависит | Что делаем | Риск |
| --- | --- | --- | --- | --- |
| 4.1 | `AnvilChunkLoader.writeChunkToNBT` | `setByte("Y", (byte)(yLoc >> 4 & 255))` | **Без изменений:** для секций −32…63 байт кодирует знак корректно (−16 → `0xF0` → при чтении снова −16). `HeightMap` — `int[]`, отрицательные значения сохраняются как есть. | Низкий |
| 4.2 | `AnvilChunkLoader.readChunkFromNBT` | `new ExtendedBlockStorage[16]`, `arr[b1]` по знаковому байту → AIOOBE для `Y < 0` или `> 15` | Размер = число секций мира, индекс `b1 − minSection`. Секции вне диапазона — в лог и пропуск, без краша. | Средний |
| 4.3 | Ванильные миры | — | Формат и байты не меняются (редирект тождественный, тег в `level.dat` не пишется). **Проверка:** тест сохраняет одинаковый мир с модом и без и сравнивает NBT. | — |
| 4.4 | `level.dat` | — | Тег `ExtendedHeight` пишется только при наличии расширенных измерений (0.5). | Низкий |
| 4.5 | Forge `ChunkIOProvider`/`ChunkIOExecutor` (асинхронная загрузка) | Идут через `readChunkFromNBT` | Без изменений. | Низкий |
| 4.6 | Открытие расширенного сохранения **без мода** | Ванильный `readChunkFromNBT` упадёт с AIOOBE | Исправить нельзя — пункт для README. | Высокий (пользовательская ошибка) |
| 4.7 | `ChunkLoader` (конвертер McRegion → Anvil) | `& 255` и т. п. | Не применяется: старые миры никогда не бывают расширенными. | — |

## 5. Сеть

| # | Место | Что зависит | Что делаем | Риск |
| --- | --- | --- | --- | --- |
| 5.1 | `S21PacketChunkData` | Маски секций: `short` на проводе, `int` внутри; чтение по 16 битам; **статический буфер 196 864 байт = ровно 16 секций** (36 секций дадут AIOOBE в `func_149269_a`); `func_149275_c()` | Расширенный вариант пакета (новый ID): маски `long` + `minSection`, буфер под N секций. Внутренние поля тоже хранят `long`: в одиночной игре пакет передаётся объектом без сериализации. | Высокий |
| 5.2 | `S26PacketMapChunkBulk` | То же, плюс `func_149269_a(chunk, true, 65535)` (маска «все» = 16 бит), буфер распаковки `func_149275_c() × count` | Расширенный вариант. **Лимит кадра 2 МиБ** (21-битный varint в `NettyVarint21FrameDecoder`): 5 чанков × 36 секций в худшем случае ≈ 2,2 МБ до сжатия. Для расширенных миров уменьшить число чанков в пакете или считать по размеру. | Высокий |
| 5.3 | `PlayerManager.PlayerInstance.flagChunkForUpdate` / `sendChunkUpdate` | `flagsYAreasToUpdate \|= 1 << (y >> 4)` (`int`, отрицательный сдвиг); `locationOfBlockChange` — `short` с упаковкой `x<<12 \| z<<8 \| y` (**y — 8 бит**); декодирование `& 255` | Для расширенного мира — `long`-маска и параллельный `int[]` с позициями. | Высокий |
| 5.4 | `S22PacketMultiBlockChange` + `NetHandlerPlayClient.handleMultiBlockChange` | Запись `short`, y = `& 255` | Расширенный вариант (y — `short`). | Средний |
| 5.5 | `S23PacketBlockChange` | y — `unsignedByte` | Расширенный вариант при y вне 0…255. | Средний |
| 5.6 | `S28PacketEffect` (частицы ломания блока 2001, звуки дверей и т. п.) | y — `readByte & 255` | Расширенный вариант. | Средний |
| 5.7 | `S0APacketUseBed` | y — **знаковый** `readByte` (в ванили уже сломан для y > 127) | Расширенный вариант. | Низкий |
| 5.8 | `C07PacketPlayerDigging` | y — `unsignedByte` | Расширенный вариант (клиент шлёт его только при y вне 0…255). | Средний |
| 5.9 | `C08PacketPlayerBlockPlacement` | y — `unsignedByte`; сентинел «использовать предмет» `(-1,-1,-1, dir=255)` | Расширенный вариант. Сентинел определяется по `direction == 255`, поэтому y = −1 не ломает логику — проверю по `processPlayerBlockPlacement`. | Средний |
| 5.10 | Проверены, **правок не требуют** | — | `S24` (y — `short`), `S25` (`int`), `S33`/`C12` (таблички, `short`), `S35` (`short`), `S36` (`int`), `S05` (`int`), сущности `S0C`/`S0E`/`S0F`/`S10`/`S11`/`S18`/`S2C` (`int` ×32), `S14`–`S17` (относительные смещения), `S08` (`double`), `S27` (центр — `float`, блоки — смещения байтом), `S29` (`int` ×8), `S2A` (`float`), FML `EntitySpawnMessage`/`EntityAdjustMessage`/`OpenGui` (`int`). | — |
| 5.11 | `NetHandlerPlayClient.handleChunkData`, `handleMapChunkBulk`; `WorldClient.doPreChunk` | `invalidateBlockReceiveRegion` и `markBlockRangeForRenderUpdate` по y 0…256; выгрузка чанка при `mask == 0` | Диапазон мира; проверка выгрузки по всей `long`-маске. | Средний |
| 5.12 | Синхронизация диапазонов | Клиент создаёт `Chunk` сам (`ChunkProviderClient`), и ему нужен диапазон **до** первого чанка | Новый пакет «таблица dim → диапазон», шлётся при логине и смене измерения раньше чанков. Тот же механизм, что и в 0.7. | Средний |
| 5.13 | Рукопожатие | По умолчанию FML требует мод с обеих сторон | `@NetworkCheckHandler`: сервер отказывает клиенту без мода с понятным текстом; клиент принимает сервер без мода. | Низкий |
| 5.14 | Регистрация новых ID в `EnumConnectionState.PLAY` | — | Через accessor на `func_150751_a`/`func_150756_b`. Конфликт возможен, если другой мод займёт те же ID — выберу редкие и опишу в README. | Средний |

## 6. Клиентский рендер

| # | Место | Что зависит | Что делаем | Риск |
| --- | --- | --- | --- | --- |
| 6.1 | `RenderGlobal.loadRenderers` | `renderChunksTall = 16`, `WorldRenderer` на `y = l × 16` | Высота = число секций, `y = minY + l × 16`. Display-листов становится в 2,25 раза больше (36/16). | Высокий (память, конфликт с OptiFine) |
| 6.2 | `RenderGlobal.markRenderersForNewPosition` | `y = l2 × 16`, `minBlockY`/`maxBlockY` | `minY + l2 × 16`. | Средний |
| 6.3 | `RenderGlobal.markBlocksForUpdate` | `bucketInt(y,16) % renderChunksTall` — **заворачивание:** y = −16 помечает рендерер на y = 240 | Индекс `bucket − minSection`, вне диапазона — пропуск. | Средний |
| 6.4 | `WorldRenderer.updateRenderer` | `ChunkCache` + `extendedLevelsInChunkCache` | Чинится через 2.15 и 1.7. | Средний |
| 6.5 | `RenderGlobal.renderSky` | `playerY − getHorizon() < 0` → тёмная нижняя полусфера | `getHorizon()` для расширенного мира по умолчанию = `minY`, настраивается через API или `WorldProvider`/`WorldType.getHorizon` (Forge-хук уже есть). | Низкий |
| 6.6 | `EntityRenderer.setupFog`, `updateFogColor` | Void fog: `posY × getVoidFogYFactor()` — затемнение при y < 8 | Считать от `posY − minY`. | Низкий |
| 6.7 | `EntityRenderer.renderRainSnow`, `addRainParticles` | Через `getPrecipitationHeight` | Без отдельных правок (чинится через 2.13). Проверить в игре. | Низкий |
| 6.8 | Облака (`getCloudHeight()` = 128, порядок отрисовки по `posY < 128`) | — | Без изменений. | — |
| 6.9 | Отсечение по фрустуму, occlusion, сортировка | Работают по списку `WorldRenderer` | Без изменений, проверить в игре. | Низкий |

## 7. Сущности, игрок, мобы, команды

| # | Место | Что зависит | Что делаем | Риск |
| --- | --- | --- | --- | --- |
| 7.1 | `Entity.onEntityUpdate` | `posY < −64` → `kill()` | `posY < minY − 64`. | Низкий |
| 7.2 | `EntityMinecart.onUpdate` | `posY < −64` → `kill()` (свой дубль) | То же. | Низкий |
| 7.3 | `EntityLivingBase.moveEntityWithHeading` (клиент, чанк не загружен) | `posY > 0` → `motionY = −0.1`, иначе 0 | `minY`. Иначе игрок «висит» на y = 0, пока грузятся нижние чанки. | Низкий |
| 7.4 | `Entity.preparePlayerToSpawn` (клиент) | `while (posY > 0)` | `minY`. | Низкий |
| 7.5 | `EntityFallingBlock.onUpdate` | `j < 1 \|\| j > 256` → выпасть предметом | `minY + 1 … maxY + 1`. | Низкий |
| 7.6 | `SpawnerAnimals.func_151350_a` (случайная точка спавна) | `rand.nextInt(topFilledSegment + 15)` → мобы не спавнятся ниже 0. **Если верхняя заполненная секция отрицательная — `nextInt(≤ 0)` бросает `IllegalArgumentException`, краш тика.** | `minY + rand.nextInt(top + 16 − minY)`. | Высокий (краш) |
| 7.7 | `SpawnerAnimals.performWorldGenSpawning` | `getTopSolidOrLiquidBlock` | Чинится через 2.11. | Низкий |
| 7.8 | `PathFinder.getSafePoint` (поиск точки приземления) | `while (y > 0)` | `minY`. | Низкий |
| 7.9 | `PathPoint.makeHash` | `y & 255` | **Без изменений:** коллизия возможна только у точек на расстоянии 256 по y, а дальность поиска пути на порядок меньше. Упомяну в README. | Низкий |
| 7.10 | `Teleporter` (порталы Нижнего мира) | Поиск `getActualHeight() − 1 … >= 0`, клэмп при постройке | `minY…maxY`. Нужно, только если порталы ведут в расширенное измерение. | Средний |
| 7.11 | `CommandSetSpawnpoint`, `CommandSetDefaultSpawnpoint` | y ограничен 0…256 | Границы мира. | Низкий |
| 7.12 | `CommandSpreadPlayers` | Скан сверху вниз с 256 до 0 | Границы мира. | Низкий |
| 7.13 | `CommandTeleport` | y без ограничений (`min = max = 0` отключает проверку) | Без изменений. | — |
| 7.14 | NBT сущностей и тайлов, `NextTickListEntry`, `TileTicks` | `double`/`int` | Без изменений: ничего не урезается до байта. | — |

## 8. Генерация

| # | Место | Что зависит | Что делаем | Риск |
| --- | --- | --- | --- | --- |
| 8.1 | `Chunk(World, Block[], byte[], x, z)` | Высота = `length / 256`, раскладка `x × k × 16 \| z × k \| y` от y = 0 | Новый API `ExtendedChunkPrimer`: массив на `16 × 16 × H` от `minY`, `primer.set(x, y, z, block, meta)` с реальным y и `primer.toChunk(world, cx, cz)` — сразу в секции, затем `generateSkylightMap()`. | Средний |
| 8.2 | Populate | Работает через `World.getBlock`/`setBlock` | Чинится через раздел 2. | Низкий |
| 8.3 | Ванильный `BiomeDecorator` и `BiomeGen*.decorate` | `nextInt(getHeightValue() × 2)`, `nextInt(h + 32)` → **`IllegalArgumentException`, если колонка пустая или её верх ≤ 0** | Библиотека не патчит: это ответственность генератора. В README — предупреждение для Reverworld; если генератор вызывает ванильные декораторы для нижнего мира, нужен свой декоратор или смещение. | Высокий для Reverworld |
| 8.4 | `BlockFalling` / `BlockDragonEgg` (`fallInstantly` во время генерации) | `y >= 0`, `while (y > 0)` | `minY`. | Низкий |
| 8.5 | `BlockPistonBase` (`canPushBlock`, `tryExtend`) | `j1 <= 0 \|\| j1 >= getHeight()` | `minY`/`maxY`. Иначе поршни не толкают на y ≤ 0. | Низкий |
| 8.6 | `BlockLilyPad.canBlockStay`, `BlockMushroom.canBlockStay` | `y >= 0 && y < 256` | Границы мира. | Низкий |
| 8.7 | `BlockDoor.canPlaceBlockAt`, `TileEntityBeacon` | `getHeight()` | Чинится через 2.14. | — |
| 8.8 | Forge `BlockFluidFinite` | `otherY < 0 \|\| otherY >= getHeight()` | `minY`. | Низкий |
| 8.9 | `ItemMap` (цвета карты) | Циклы `l4 > 0` | Нижняя граница — `minY`. Косметика. | Низкий |
| 8.10 | `WorldServer.func_147456_g` (случайные тики, молния, лёд и снег) | Тики идут по `storageArrays` + `getYLocation()`, погода — через `getPrecipitationHeight` | Без изменений. | — |

## 9. Совместимость (предварительно, проверка — в вехе 5)

| Мод | Что трогает | Ожидание |
| --- | --- | --- |
| OptiFine | Заменяет `RenderGlobal`/`WorldRenderer`, свои рендер-чанки и туман | В расширенных мирах почти наверняка конфликт. Цель — не ломать ванильные миры. |
| FastCraft | Патчит свет, `Chunk`, `RenderGlobal` | Высокий риск. Проверить, что игра хотя бы запускается. |
| Angelica (GTNH) | Свой рендерер | Расширенные миры не поддерживаются без отдельной интеграции. |
| ArchaicFix (свой «phosphor»-движок света), Hodgepodge, CoreTweaks | `Chunk`, свет, `AnvilChunkLoader` | Проверить по их миксинам, есть ли пересечения с редиректами. |
| NotEnoughIDs / EndlessIDs / ChunkAPI | Формат `ExtendedBlockStorage`, `S21`/`S26`, `AnvilChunkLoader` | Прямое пересечение по сети и сохранению. Как минимум задокументировать; возможно, интегрироваться через ChunkAPI. |

Всё в этой таблице — по общему знанию об этих модах, а не по чтению их кода. Перед вехой 5
нужно прочитать их миксины.

---

## Порядок вех, привязанный к разделам

1. **Хранение + World API + сохранение (сервер):** 0.x, 1.1–1.9, 1.11–1.13, 2.1–2.14, 2.16–2.19,
   4.x. Проверка: блоки на y −200 и 300 ставятся, читаются и переживают перезапуск; NBT ванильного
   мира с модом совпадает с NBT без мода.
2. **Свет:** 3.x (плюс 2.3, 2.6, 2.7). Проверка: значения небесного и блочного света во всём
   диапазоне на тестовом генераторе.
3. **Сеть + рендер:** 1.10, 2.15, 5.x, 6.x. Проверка: юнит-тесты сериализации пакетов (round-trip),
   плюс ручная проверка в игре.
4. **Сущности, мобы, генерация, API:** 7.x, 8.x, тип мира `extendedtest`.
5. **Совместимость и полировка:** 9.x, README.
