# Отчёт по моду «ExtendedHeight» (проект «Chunks», 1.0.0)

Дата: 22.10.2026. Копия исходников: `_work\ports\ExtendedHeight` (оригинал `P:\Personal\Coding\Chunks` не менялся; в копию не попали `build`, `.gradle`, `run`, журналы сбоев). Сборка: Java 25.0.4.1 (Temurin), состав — `_work\reports\mods.lock.md`.

## Кратко

* **Сборка:** проект уже был на шаблоне GTNH; в этой среде собирается без правок (`BUILD SUCCESSFUL`, 39 с, 13 юнит-тестов без ошибок, spotless проходит). Файлы сборки идентичны шаблону, кроме `settings.gradle.kts` (имя проекта `extendedheight`). Исходный код **не менялся**.
* **Главный результат: с настройками базовой сборки по умолчанию сервер с ExtendedHeight не запускается.** Причина — освещение Phosphor из ArchaicFix (`enablePhosphor=true`). С `enablePhosphor=false` («режим совместимости», решение владельца) мод работает.
* **Второй результат: в режиме совместимости на ванильном рендере дальность прорисовки больше 16 в расширенном мире роняет клиент** (дальность 32, как в замерах FPS владельца). Причина в самом моде (раздел «Проблемы»). На дальности 16 всё работает.
* Накладные расходы мода в обычных мирах — в пределах шума (+1,3 %); утечек нет.
* Два вопроса владельцу (раздел «Вопросы»).

| Критерий (раздел 8 руководства) | Итог | Доказательство |
|---|---|---|
| 1. Собирается | выполнено | `eh_build1.log`; jar `extendedheight-1.0.0.jar` (+ `-api`, `-dev`, `-sources`) |
| 2. Сервер на Java 25 без ошибок | **выполнено только в режиме совместимости**; с Phosphor — падение | `eh_check.server.log` (падение), `eh_check2.server.log` (норма), краш-отчёт — раздел «Проблемы» |
| 3. Клиент на Java 25 без ошибок | выполнено в режиме совместимости на дальности 16 (вход в расширенный мир, 60 с, новых ошибок нет); на дальности 32 — **падение** | `eh_join_compat16_*`, `eh_join_compat_*` |
| 4. Нет предупреждений о конфликтующих миксинах | выполнено в режиме совместимости (57 миксинов применены на клиенте, ошибок применения нет); с Phosphor — **конфликт**; с Angelica на сервере/в мире не проверялось (см. «Совместимость») | журналы |
| 5. Время такта не хуже порога | выполнено для обычных миров (+1,3 % к контролю без мода в той же конфигурации); для расширенного мира сравнить не с чем (см. ниже) | таблица |
| 6. Нет роста числа объектов мода между циклами | выполнено | 13 → 13 экземпляров (+0,0 %); MAT — в подозрениях только данные мира |
| 7. Классы под Java 8 | выполнено | «классов: 137, новее Java 8: 0» |

## Проблемы и исправления

### Проблема 1. Конфликт с Phosphor (ArchaicFix): сервер не запускается

* Место: `src/main/java/io/github/extendedheight/mixins/early/MixinChunk.java` — `@Redirect` на доступ `storageArrays[…]` в `Chunk.getSavedLightValue` (`func_76614_a`).
* Доказательство: `InvalidInjectionException: @At("FIELD") on net/minecraft/world/chunk/Chunk::eh$getSection … cannot inject into net/minecraft/world/chunk/Chunk::func_76614_a(…)I merged by org.embeddedt.archaicfix.mixins.common.lighting.MixinChunk` — `Mixin apply for mod extendedheight failed … MixinChunk`, `FATAL`, краш при создании мира. Журнал: `_work\reports\eh_check.server.log`, краш-отчёт `_work\sv_eh\crash-reports\crash-2026-10-22_00.43.39-server.txt` (удалён при повторных запусках; текст в журнале).
* Причина: ArchaicFix (`enablePhosphor=true`) переписывает `Chunk.getSavedLightValue`, и миксин ExtendedHeight не находит в методе доступ к массиву секций, который он перенаправляет. Совпадает с предупреждением в собственной документации ExtendedHeight (`docs/patch-map.md`, раздел 9: «ArchaicFix (свой phosphor-движок света) … проверить»).
* Исправление: **не вносилось в код**; решение — конфигурация `B:enablePhosphor=false` в `config/archaicfix.cfg` (режим совместимости). Проверка: `eh_check2.server.log` — мир −256…319 создан (`Dimension 0: new dimension, height range -256..319 stored in level.dat`), игрок телепортирован на y = −200, 300, −100; новых замечаний нет.
* Цена: Phosphor ускоряет освещение, и его отключение заметно увеличивает время такта (см. таблицу: **4,20 мс без Phosphor против 3,27 мс с Phosphor** на одном сценарии, без мода). Одиночные прогоны; разница ~28 % выходит за шум (шум между прогонами 3–5 %).

### Проблема 2. Падение клиента на дальности прорисовки больше 16 (ванильный рендер, расширенный мир)

* Место: `src/main/java/io/github/extendedheight/mixins/early/client/MixinRenderGlobal.java`, строка 38 `EH_MAX_WIDE = 34`, строки 81–96 (`eh$ensureCapacity`): для расширенной высоты выделяются `34 × 34 × sectionCount` списков отображения и запросов окклюзии — рассчитано на дальность 16 («render distance 16 → 33, plus one»).
* Доказательство: при дальности 32 вход в мир `extendedtest` ⇒ `IndexOutOfBoundsException` в `RenderGlobal.loadRenderers` (`RenderGlobal.func_72712_a`, `IntBuffer.get`), «Ticking screen»; отчёт `Reverworld без Angelica\.minecraft\crash-reports\crash-2026-10-22_00.47.03-client.txt`, журнал `_work\reports\eh_join_compat_cli.fml-client.log`. На дальности 16 тот же вход без ошибок (`eh_join_compat16_cli.fml-client.log`). Для ванильных измерений на дальности 32 тот же клиент работает (замер владельца 66 FPS в обычном мире): ширину там обеспечивает ArchaicFix (`raiseMaxRenderDistance=true`), а в расширенном мире свои буферы выделяет ExtendedHeight.
* **Исправление (по решению владельца 22.10.2026: «ограничить 16»).** Код изменён: в расширенном мире ванильный рендерер и дальняя плоскость камеры используют **не больше 16 чанков**, сама настройка игрока не меняется и сохраняется как была (в обычных мирах дальность 32 работает по-прежнему).
  * `core/RenderDistanceLimit.java` (новый; `clamp(extended, distance)`, константа 16; тест `RenderDistanceLimitTest`);
  * `MixinRenderGlobal.eh$limitRenderDistance` — `@Redirect` чтения `GameSettings.renderDistanceChunks` в `loadRenderers` и `sortAndRender` (оба чтения должны совпадать, иначе `sortAndRender` перезагружает рендереры каждый кадр);
  * `MixinEntityRendererDistance` (новый, зарегистрирован в `mixins.extendedheight.json`) — то же для дальней плоскости (`setupCameraTransform`), чтобы туман не уходил за 16 чанков.
  * Проверка: сборка с тестами (`eh_build_limit.log`, `BUILD SUCCESSFUL`); клиент в режиме совместимости, `renderDistance:32`, мир `extendedtest`, шахта и телепортации (`Test-Shots.ps1 -Distance 32`, `shots_limit32_*`): падения нет, журналы без новых замечаний, местность видна на всех высотах; прежнее падение (`IndexOutOfBoundsException` в `loadRenderers`) не воспроизводится.
  * Ограничение: пока игрок в расширенном мире на настройке больше 16, видно 16 чанков; оттенок неба/тумана (`updateFogColor`) считается по настройке (косметика). Слайдер в меню показывает прежнее значение. При интеграции с Angelica это ограничение снимается.

### Проблема 3. Плавающее падение клиента: Hodgepodge шлёт в расширенный мир ванильный пакет чанка S21

* **Признак.** Клиент (режим совместимости) падает с `ArrayIndexOutOfBoundsException: arraycopy: last source index 231424 out of bounds for byte[229632]` в `Chunk.fillChunk` ← `NetHandlerPlayClient.handleChunkData` ← `S21PacketChunkData.processPacket`. Одни и те же числа во всех падениях. Краш-отчёты — `Reverworld без Angelica\.minecraft\crash-reports\crash-2026-10-22_02.55.05-client.txt`, `…_03.15.07-client.txt` и др. Падение случалось в момент первой телепортации игрока в сценарии `Test-Shots.ps1` (шахта и телепортация на y 100).
* **Причина (механизм).** В Hodgepodge (`com.mitchej123.hodgepodge.mixins.hooks.ChunkGenScheduler.queueResend`, часть `throttleChunkGeneration`) после достройки чанка каждому игроку поблизости отправляется `new S21PacketChunkData(chunk, true, 65535)` — **ванильный** пакет (маска всех 16 секций, «непрерывный»). Расширенный мир обслуживается своими пакетами (`SPacketExtChunkData`/`SPacketExtMapChunkBulk`); ванильный S21 в нём недопустим. Доказательства:
  1. Байт-код: вызов `S21PacketChunkData.<init>(Chunk, boolean, int)` с константой 65535 в `queueResend`; вызывается из `processPopulationQueue`, `forceProcessAged`, `pruneAndSort` (`_work
eports\probe_export…`, разбор `javap`).
  2. Прямой признак: бот (`bot.py --log-chunks`) принял в расширенном мире два пакета `S21 chunk=(9,9)` и `(10,9)`, `cont=1 mask=0xFFFF` — ровно такие аргументы (строки лога бота: `[bot] S21 t=15.6 chunk=(9,9) cont=1 mask=0xFFFF add=0x0000 clen=251` и такая же для (10,9); сам файл `bp_full_3.bot.log` я позже случайно удалил при очистке, поэтому строка приведена здесь; в остальных ~15 бот-прогонах, `bot_probe_final.txt`, такого пакета не было — событие редкое).
  3. Выгрузка миксинов (`-Dmixin.debug.export=true`, `_work
eports\probe_export.PlayerInstance.javap.txt`, `.PlayerManager.javap.txt`): итоговый `PlayerManager$PlayerInstance` содержит обработчики ExtendedHeight (`eh$init`, `eh$flag`, `eh$sendChunkUpdate`), вызываемые **в начале** `func_151253_a` (flagChunkForUpdate) и `func_73254_a` (sendChunkUpdate) с отменой вызова — **миксин ExtendedHeight не перекрыт**. Другие миксины на `PlayerInstance` — Hodgepodge `MixinPlayerInstance` (только описания тайл-энтити, S35) — не затрагивают отправку чанков; остаточный ванильный S21 в `PlayerInstance.func_73252_b` — это выгрузка чанка (маска 0, безвредна).
  4. Все миксины и заплатки на `PlayerInstance`/`PlayerManager`/`S21PacketChunkData` в сборке: ArchaicFix `MixinPlayerManager` (увеличение дальности отправки) и `MixinSPacketChunkData` (освещение, только при Phosphor); Hodgepodge `MixinPlayerInstance`, `MixinPlayerManager_ThrottleChunkGen`, `ChunkGenScheduler` (создаёт S21), ASM `PlayerManagerTransformer`; CoreTweaks, GTNHLib, lwjgl3ify — нет.
* **Частота (клиент, один и тот же сценарий).** Полный набор модов: **5 падений из 17** (старые прогоны `Test-Shots` 4 из 7 с двумя отключёнными настройками в одном из них и новая серия `crash_probe.txt` 1 из 10). С `throttleChunkGeneration=false` — **0 из 10** (случайно при частоте 5/17 — около 3 %). Минимальный набор (без Hodgepodge, CoreTweaks, ArchaicFix, GTNHLib) — 0 из 4. Замечание владельца учтено: выводы о «виновном моде» из убирания по одному моду (0 из 3) я **отозвал** — выборки были слишком малы; причина найдена по механизму, а частоты служат подтверждением.
* **Ограничения.** Частота зависит от сценария и нагрузки (в серии с кучей 2 ГБ и паузой — 1 из 10), событие плавающее: оно требует, чтобы чанк достраивался после отправки игроку. Бот-проба (`Run-BotProbe.ps1`) поймала его 1 раз из ~8, поэтому для статистики годится только настоящий клиент (`Run-CrashProbe.ps1`).
* **Решение, принятое сейчас (настройка, не код).** `throttleChunkGeneration=false` в `config/hodgepodge.cfg` всех копий сервера (и экземпляров), вместе с остальными настройками Hodgepodge, меняющими механику (решение владельца 5; список — `_work
eports\hodgepodge_changes.md`). Цена: генерация чанков не растягивается по тактам — возможны всплески времени такта при открытии местности (замер — `01_baseline.md`, «Эталон после настройки Hodgepodge»).
* **Исправление в ExtendedHeight (решение владельца 22.10.2026: «да, в одной точке отправки, для любого мода»).** Ванильный S21 для чанка расширенного мира подменяется расширенным в той единственной точке, через которую проходит любой исходящий пакет, — `ExtPackets.onOutbound` (вызывается из `MixinNetworkManager`, там же уже подменялись S23/C07/C08). Чтобы подмена знала чанк, `MixinS21PacketChunkData` (новый) запоминает чанк, маску и признак «целый чанк» при создании пакета (только для чанков расширенных миров; интерфейс `IVanillaChunkPacket`). Правила маски (`ExtPackets.extendedMask`, юнит-тест): маска 0 — пакет выгрузки, остаётся ванильным (клиент его понимает); «целый чанк» ванили (все 16 бит) — все секции; прочие маски — биты как индексы хранилища, как в ваниле. Ванильные S26 (пакет нескольких чанков), отправленные модом напрямую, **не перехватываются** (владелец просил S21; ExtendedHeight сам для своих чанков шлёт расширенный S26).
* **Проверка** (`throttleChunkGeneration=true` **временно** включён на копии сервера, клиент без Angelica, режим совместимости; сценарий массового копания — шахта 2×2 до −250 с телепортациями, как `Test-Shots.ps1`; серия `crash_probe_s21_final.txt`). Чтобы проверка могла ловить сбой, сервер с системным свойством `-Dextendedheight.injectVanillaS21=true` каждые 3 с шлёт каждому игроку ванильные S21 (целый чанк и одна секция) — `selftest/VanillaS21Injector`:
  * **с подменой: 10 прогонов из 10 без падений**, журналы без новых замечаний;
  * **контроль, подмена выключена (`-Dextendedheight.s21Swap=false`): 3 из 3 прогонов упали** с прежним исключением (`ArrayIndexOutOfBoundsException: arraycopy: last source index 231424 out of bounds for byte[229632]`, те же числа, что в исходном падении) — проверка способна ловить сбой, и причина подтверждена прямым воспроизведением.
  После проверки `throttleChunkGeneration` снова **выключена** на всех серверах и в экземплярах (решение по Hodgepodge в силе: настройка меняет механику); копия сервера для проверки удалена.
* Коммит `ExtendedHeight`: «Replace vanilla S21 for extended-world chunks at the single outbound point» (отдельный от прочих правок).

## Совместимость с модами сборки (раздел 5)

| Мод | Результат |
|---|---|
| ArchaicFix (Phosphor включён) | **несовместим**: падение (проблема 1) |
| ArchaicFix (`enablePhosphor=false`), Hodgepodge, CoreTweaks, GTNHLib, UniMixins 0.3.2, lwjgl3ify | запускаются вместе без ошибок применения миксинов, `Check-Log.ps1` — новых замечаний нет; **но** при массовом копании в расширенном мире клиент иногда падает (проблема 3, виновник не найден) |
| Angelica | **не проверялось в мире.** На главном меню миксины ExtendedHeight (59) применяются без ошибок вместе с Angelica (`eh_client_angelica.fml-client.log`); конфликт ожидается при входе в расширенный мир — по документам обоих модов (`_work\docs\ANGELICA_EXTENDEDHEIGHT.md`). Совместный запуск — первый шаг интеграции, начатой после переноса |

Для сервера ExtendedHeight требует, чтобы клиент имел мод (`required`-проверка рукопожатия Forge: бот должен заявить `extendedheight=1.0.0`, `-BotMods` в `Run-Server.ps1`).

## Совместимость с Java 17–25 (раздел 4)

Просмотрены 111 исходных файлов (без самотестов). Нарушений нет: единственная рефлексия — `ExtPackets.java:121` (`setAccessible` на закрытом поле класса Minecraft `EnumConnectionState`, не JDK), работает на Java 25 (проверено запуском). Нет `sun.*`/`jdk.internal.*`, `Unsafe`, `SecurityManager`, `System.exit`, записи в `final`, приведения загрузчика. Статические коллекции — только `RangeRegistry` (`ConcurrentHashMap` по номеру измерения и по `WorldType`, без ссылок на миры и сущности).

## Измерения нагрузки

Сценарий: то же расписание, что базовый замер (3 мин новой территории + 6 мин загруженной), JFR, одинаковые параметры JVM, по одному 10-минутному прогону. **Важно: контроль без мода сделан с `enablePhosphor=false`**, чтобы сравнивать то же самое состояние (с Phosphor мод не запускается).

| Показатель | Базовая сборка (Phosphor вкл., без мода) | Контроль: Phosphor выкл., без мода | Мод, обычный мир (Phosphor выкл.) | Мод, расширенный мир −256…319 (Phosphor выкл.) |
|---|---|---|---|---|
| Среднее время такта, мс | 3,27 | 4,20 | **4,25** (+1,3 % к контролю) | 1,39 |
| Медиана, мс | 2,53 | 3,27 | 3,19 | 0,82 |
| 95-й процентиль окон, мс | 6,30 | 9,03 | 8,61 | 2,84 |
| Худшее окно, мс | 17,9 | 21,6 | 22,0 | 4,4 |
| Пауза GC суммарно / наибольшая, мс | 194,7 / 30,0 | 185,7 / 31,0 | 209,2 / 28,9 | 178,5 / 39,0 |
| Занято после GC, МБ (макс.) | 138 | 136 | 138 | 248 |
| «Can't keep up» | 0 | 0 | 0 | 0 |

Выводы:
* **В обычных мирах мод не замедляет сервер** (+1,3 % — в пределах шума; критерий +10 % выполнен). Против исходного базового замера (3,27 мс) разница +30 % целиком объясняется отключением Phosphor, а не модом.
* Расширенный мир в этом сценарии — плоский тестовый генератор (`WorldTypeExtendedTest`), он дешевле ванильной генерации, поэтому 1,39 мс **нельзя сравнивать** с другими столбцами; это ориентир «мод держит нагрузку с запасом» и расход памяти (+110 МБ после GC из-за секций −256…319).
* Профиль расширенного мира: **1,9 % процессорных выборок** в стеках мода (560 из 29 954). Крупнейшие: сжатие пакетов чанков `SPacketExtMapChunkBulk` / `ChunkCodec.deflate` — по 0,5 %; чтение расширенного чанка из NBT `ExtendedChunkNbt.read` и обработчик `AnvilChunkLoader … eh$readExtended` — 0,5 %; тестовый генератор — 0,5 %. Огненная диаграмма — `_work\reports\eh_ext.flame.html`, стеки — `eh_ext.collapsed`, разбор — `eh_ext.analysis.md`. Горячих мест, дающих измеримую выгоду, нет.

## Утечки

Сценарий `_work\scripts\eh_cycles.txt`: 3 цикла по 8 телепортаций в расширенном мире по всему диапазону высот с возвратом в начало и паузой; гистограммы классов после 1-го и 3-го цикла. `histodiff.py` по пакету `io.github.extendedheight`: **13 → 13 экземпляров (+0,0 %)**. MAT, подозрения на утечку по снимку после 3-го цикла (`eh_cycles.c3_Leak_Suspects.zip`): единственное подозрение — `WorldServer` → `ChunkProviderServer` (42,7 % кучи: данные загруженных чанков, обычное для мира); объектов мода среди подозрений нет.

## Статический анализ

* SpotBugs 4.10.5 (`eh_spotbugs.txt/.html`, 270 записей): подавляющее большинство — особенности миксинов: `UPM` (закрытые методы-обработчики `eh$…`, которые вызывает Mixin), `BC` («невозможное приведение» `this` миксина к целевому классу — так и задумано), `PA/EI` (открытые поля пакетных классов и миксинов). Ошибок логики не найдено.
* PMD 7.28.0 (`eh_pmd.txt/.html`): 828 замечаний стиля, не анализировались и не исправлялись (измеримого эффекта нет).

## Отвергнутые правки

Не предлагалось и не пробовалось: профиль не показывает горячих мест мода (1,9 % выборок в расширенном мире, максимум по 0,5 % на метод), поэтому оптимизировать нечего (правило 6.5).

## Наблюдения для следующей задачи (интеграции с Angelica), без изменений кода

* `MixinRenderGlobal` ExtendedHeight (клиент) правит ванильный рендер, который Angelica заменяет: в режиме Angelica его надо отключать (вариант Б из `ANGELICA_EXTENDEDHEIGHT.md`).
* Ограничение дальности ≤ 16 (проблема 2) исчезает вместе с этим миксином при интеграции.

## Вопросы владельцу

1. **Phosphor и скорость сервера.** Режим совместимости (`enablePhosphor=false`) нужен, чтобы ExtendedHeight вообще запускался, но он замедляет сервер примерно на 28 % (4,20 мс против 3,27 мс; одиночные прогоны). Варианты: (а) принять цену; (б) сделать ExtendedHeight совместимой с Phosphor (миксин в `Chunk.getSavedLightValue` придётся строить иначе; Phosphor рассчитан на 16 секций, так что нужна и проверка света — объём работы средний, начинать стоит после интеграции с Angelica, там та же область); (в) включать Phosphor только в мирах без расширения (нельзя: миксины применяются к классу целиком). Рекомендация: (а) пока, затем (б).
2. **Дальность прорисовки больше 16 на ванильном рендере в расширенном мире** (проблема 2). Варианты: (а) исправить мод: ширина считается из настройки дальности (малая правка, но это новая возможность рендера вне ванильных 16); (б) оставить как есть и в режиме совместимости ограничить дальность 16 (при интеграции с Angelica ограничение исчезает вместе с миксином); (в) ничего не менять. Рекомендация: (б), если интеграция с Angelica начнётся сразу после переноса; иначе (а).

## Чек-лист для проверки владельцем (картинка и управление)

1. Режим совместимости, дальность 16: создать мир типа `extendedtest` (и `extendedbiomes`), пролететь по высоте от −256 до 319: земля на месте, небо/туман у границ, свет, освещённость.
2. Ставить и ломать блоки на нижней и верхней границах, на 0 и 255; перерисовка секций.
3. Выйти из мира и зайти снова, сменить измерение.
4. Убедиться, что дальность 32 в расширенном мире действительно падает (подтверждение проблемы 2).

## Как повторить

```powershell
cd "G:\PrismLauncher-Windows-MSVC-Portable-8.3\instances\Reverworld"
.\_work\scripts\Build-Port.ps1 -Dir _work\ports\ExtendedHeight -Tasks build -Log eh_build
.\_work\scripts\New-ServerCopy.ps1 -Name sv_eh      # затем: server.properties -> level-type=extendedtest, в config\archaicfix.cfg enablePhosphor=false
Copy-Item _work\ports\ExtendedHeight\build\libs\extendedheight-1.0.0.jar _work\sv_eh\mods\
.\_work\scripts\Run-Server.ps1 -Name eh_check -ServerDir sv_eh -Port 25574 -BotMods 'extendedheight=1.0.0' -Script _work\scripts\eh_check.txt
.\_work\scripts\Check-Log.ps1 -Path _work\reports\eh_check.server.log
# клиент в расширенный мир (экземпляр без Angelica, дальность 16):
.\_work\scripts\Test-ClientJoin.ps1 -Name eh_join -ServerDir sv_eh -Port 25575 -ClientInstance "G:\PrismLauncher-Windows-MSVC-Portable-8.3\instances\Reverworld без Angelica" -Jars _work\ports\ExtendedHeight\build\libs\extendedheight-1.0.0.jar
# замеры: контроль без мода, мод в обычном мире, мод в расширенном мире
.\_work\scripts\Run-Measure.ps1 -DoneName ab_eh -Runs "eh_control|sv_control2|25576||_work\scripts\sc_plain.txt|;eh_vanilla|sv_eh_vanilla|25577|extendedheight=1.0.0|_work\scripts\sc_plain.txt|;eh_ext|sv_eh|25574|extendedheight=1.0.0|_work\scripts\sc_eh_ext.txt|"
py -3.12 -I .\_work\scripts\analyze_jfr.py eh_ext
.\_work\scripts\Analyze-Static.ps1 -Dir _work\ports\ExtendedHeight -Name eh
```
