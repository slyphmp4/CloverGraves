# MODERNIZATION VERDICT

**Verdict: NEEDS CLEANUP** (исходное состояние, 12.09.2026).

## Target summary

Paper/Cardboard 26.2, Java 25, Paper API 26.2.build.110-stable; Gradle 9.7.1,
Shadow 9.6.1, один модуль Java. Артефакт: `build/libs/CloverGraves-2.1.0.jar`.
Поддержка старых Minecraft, Spigot и Folia не заявлена. CI использует Java 25
и фиксированный коммит Cardboard. Локально Git отсутствует; исходники сохранены
в `.audit/baseline.zip`. Исходный `gradlew.bat build` проходит.

## Plan, before implementation

### SAFE AUTOMATIC FIXES

- BUILD / IMPORTANT: разрешить объединение JDBC ServiceLoader descriptors в Shadow;
  исходная сборка предупреждает о потере дубликатов до transformer.
- BUILD / NORMAL: закрепить SHA-256 дистрибутива Gradle; включить точные предупреждения javac.
- BUG / NORMAL: вычислять центр блока без `Location.getBlock()`, чтобы не загружать чанки.
- BUG / NORMAL: учитывать изменение Y при первом успешном поиске безопасного места.
- CLEANUP: убрать ложное описание настройки aliases в README; регистрация идёт из plugin.yml.

### REVIEW BEFORE FIXING

Проанализированы потоки и контракты; исправления разрешены запросом пользователя.

- BUG / IMPORTANT: `Teleport` проверяет только наличие UUID; старые callbacks после
  отмены могут завершить новый прогрев. Привязать callback к конкретной попытке.
- BUG / IMPORTANT: отменённый сервером телепорт не возвращает списанные деньги.
- BUG / IMPORTANT: `StorageMigration` считает неуспешные save успешными и переименовывает
  исходный JSON даже при частичной миграции. Использовать SQL batch/transaction и проверять ids.
- RELIABILITY / IMPORTANT: ошибки удаления SQL проглатываются, очередь удалений теряет запись;
  JSON сообщает успешную запись при IOException. Передавать ошибку вызывающему коду и повторять.
- RELIABILITY / IMPORTANT: повреждённый data.json может быть затёрт при close;
  запретить запись после неудачного чтения, сохранять состояние только после успешного flush.
- RELIABILITY / IMPORTANT: убрать гонку между первым сохранением и удалением могилы,
  а также удержание удалённых могил в unsavedRemovalTombstones.
- BUG / IMPORTANT: мгновенный подбор и удаление должны сначала синхронизировать открытый
  инвентарь; иначе ещё не обработанный клик может дублировать предметы.
- BUG / IMPORTANT: при ошибке создания могилы возвращать предметы в путь death drops,
  а при keepInventory — восстановить исходный инвентарь.
- RELIABILITY / NORMAL: закрывать SQL provider при неуспешной инициализации.

### DO NOT AUTOMATICALLY CHANGE

- Формат предметов, публичные API/events, старые permission nodes и миграционный reader:
  используются сохранёнными данными и интеграциями.
- Не переходить на другую версию Minecraft, Folia scheduler или JUnit 6 без необходимости.
- Обновление H2 требует отдельной проверки открытия файла 2.4.240 в новой версии.

## Dependency updates

| Dependency | Current | Latest verified | Scope / embedded | Action |
| --- | --- | --- | --- | --- |
| Paper API | 26.2.build.110-stable | 26.2.build.123-stable (Javadoc) | compileOnly, test / no | сохранить целевой baseline |
| VaultAPI | 1.7 | REQUIRES CURRENT VERSION CHECK | compileOnly, test / no | сохранить |
| PlaceholderAPI | 2.12.3 | 2.12.3 | compileOnly / no | сохранить |
| bStats | 3.2.1 | 3.2.1 | implementation / relocated | сохранить |
| H2 | 2.4.240 | 2.5.250 | implementation / yes | оценить file compatibility |
| SQLite JDBC | 3.53.4.0 | 3.53.4.0 | implementation / yes | сохранить |
| MySQL JDBC | 26.7.0 | 26.7.0 | implementation / yes | сохранить |
| HikariCP | 7.1.0 | 7.1.0 | implementation / relocated | сохранить |
| JUnit BOM | 5.14.0 | 6.1.3 | test / no | major upgrade не требуется |
| Gradle | 9.7.1 | 9.7.1 | build | добавить checksum |
| Shadow | 9.6.1 | 9.6.1 | build | исправить service merge |

Проверено по Maven metadata издателей и официальной документации:
[Maven Central](https://repo.maven.apache.org/maven2/),
[PlaceholderAPI metadata](https://repo.extendedclip.com/content/repositories/placeholderapi/me/clip/placeholderapi/maven-metadata.xml),
[Paper Javadoc](https://jd.papermc.io/paper/26.2/),
[Gradle releases](https://gradle.org/releases/),
[Shadow](https://plugins.gradle.org/plugin/com.gradleup.shadow),
[Shadow service merging](https://gradleup.com/shadow/configuration/merging/),
[H2 release notes](https://github.com/h2database/h2database/releases/tag/version-2.5.250).

## Legacy, dead code and configuration

- `AxGraves`, `AxGravesAPI`, `axgraves.*` и aliases: NOT DEAD, часть контракта.
- `CardboardCompatibilitySelfTest`: NOT DEAD, запускается system property из CI.
- Paper legacy item reader: NOT DEAD, путь чтения старых данных.
- `CommandManager.reload()`: пустой публичный метод, POSSIBLY DEAD — оставить ради внешних callers.
- `command-aliases`: UNUSED, фактические aliases находятся в plugin.yml.
- `save-graves.auto-save-seconds`: fallback при отсутствии storage.flush-interval-seconds.
- JDBC, HTTP вынесены с основного потока; сериализация Bukkit ItemStack остаётся на основном.
- `History`/`Restore` используют синхронный поиск OfflinePlayer по имени: потенциальный сетевой
  вызов; требуется сохранить семантику UUID/name при замене.
- `PlayerInteractListener` перебирает все могилы на правый клик: использовать существующий chunk index.
- Очередь expiry удерживает удалённые могилы до исходного срока: ограничить накопление stale entries.
- CI actions закреплены major tags; Cardboard smoke-test присутствует, но его наличие не доказывает
  прохождение локального runtime-теста. Публикация релиза в рамках аудита не выполняется.

## Verification plan

Регрессионные тесты с ошибками SQL/JSON, отменой и повторным запуском прогрева,
проверка миграции, сборка и содержимое release JAR. Серверные сценарии (death, GUI,
Vault, Cardboard) отдельно отмечаются как проверенные или требующие runtime-проверки.

# IMPLEMENTATION RESULT

**После исправлений: MOSTLY MODERN.** Масштабная смена архитектуры или платформы не нужна.

## Changes applied / Bugs fixed

| ID | Category / priority | Trigger and fix | Verification |
| --- | --- | --- | --- |
| CG-01 | BUG / IMPORTANT | Отмена и повторный прогрев в той же точке: старые callbacks теперь проверяют идентичность попытки | TeleportWarmupsTest |
| CG-02 | BUG / IMPORTANT | Отменённый teleport: возврат списания через Vault, сообщение об оплате после успешного перемещения | компиляция и анализ ветвей; нужен Vault runtime |
| CG-03 | BUG / IMPORTANT | Миграция JSON: один SQL batch, проверка всех ids; при ошибке rollback, исходный файл не переименовывается | валидная первая и невалидная вторая запись в StorageMigrationTest |
| CG-04 | RELIABILITY / IMPORTANT | SQL remove/load: исключения доходят до caller; неуспешное удаление возвращается в очередь без бесконечного цикла | SqlGraveStorageTest, SaveGravesTest |
| CG-05 | RELIABILITY / IMPORTANT | JSON IOException: исключение вместо ложного успеха, in-memory state меняется только после записи; temp cleanup и fallback при отсутствии atomic move | JsonGraveStorageTest |
| CG-06 | RELIABILITY / IMPORTANT | Повреждённый JSON: блокировка записи после неуспешного чтения; close не перезаписывает файл; повторный load не дублирует записи | JsonGraveStorageTest |
| CG-07 | RELIABILITY / IMPORTANT | Первое сохранение одновременно с удалением: причина удаления хранится в объекте, а не в глобальной карте; после сохранения выполняется remove/retry | анализ interleavings; retry покрыт тестом, полного конкурентного runtime-теста нет |
| CG-08 | BUG / IMPORTANT | GUI → быстрый подбор/удаление: синхронизация открытого inventory, немедленная обработка close, независимые копии ItemStack перед addItem | анализ кода и компиляция; нужен игровой тест |
| CG-09 | BUG / IMPORTANT | Ошибка создания могилы: исходные death drops и XP event восстанавливаются; при keepInventory возвращается исходное содержимое | анализ обеих ветвей; нужен death-event runtime |
| CG-10 | RELIABILITY / NORMAL | Ошибка инициализации/загрузки: закрывается SQL provider; невозможность прочитать хранилище отключает плагин с ошибкой вместо продолжения как с пустой БД | SQL load failure test + анализ lifecycle |
| CG-11 | BUG / NORMAL | Центр блока вычисляется арифметически; Y-clamp отмечается как relocation | LocationUtilsTest, SafeLocationFinderTest |
| CG-12 | PERFORMANCE / NORMAL | Правый клик: поиск по существующему chunk index; огромный настроенный радиус сохраняет ограниченный fallback. Stale expiry entries периодически удаляются | анализ границ и компиляция; нагрузочный runtime не проводился |
| CG-13 | RELIABILITY / NORMAL | Restore construction failure освобождает SQL claim для повторной попытки; успешно созданная могила сразу ставится на сохранение | claim rollback tests H2/SQLite; нужен runtime construction failure |
| CG-14 | BUILD / IMPORTANT | Service descriptors JDBC объединяются до удаления дубликатов, verifyReleaseJar входит в check | verifyReleaseJar PASS, ручной ZIP audit |
| CG-15 | BUILD / NORMAL | SHA-256 Gradle; version для processResources захвачен на этапе конфигурации и объявлен input | build --warning-mode all, Gradle deprecation устранена |
| CG-16 | RELIABILITY / NORMAL | Более старое отложенное saveNow не перезаписывает уже сохранённую новую версию; shutdown пытается удалить записи и всегда закрывает provider | анализ последовательной очереди executor, сборка |

Все CG-01—CG-16 имеют высокую уверенность в указанном исходном дефекте; отсутствие
runtime-теста в таблице означает ограничение проверки исправления, а не результат PASS.
CG-01—CG-10, CG-12—CG-13, CG-16 относятся к REVIEW BEFORE FIXING и были проверены по
потокам/контрактам перед применением. CG-11, CG-14—CG-15 — SAFE AUTOMATIC FIXES.

## Dependencies updated

H2 **2.4.240 → 2.5.250**. Основание — исправления MVStore concurrency и сохранности
данных в официальных release notes, а не только наличие новой версии. REVIEW BEFORE FIXING:
сначала файл с identity key и BLOB создан старым драйвером, затем успешно открыт,
прочитан и дополнен новым; после повторного открытия обе записи сохранились.
Это синтетическая проверка совместимости, не проверка пользовательской БД.
Лог: `.audit/h2-upgrade-check.log`. Убрано избыточное testImplementation H2: test
уже наследует implementation. Остальные версии сохранены.

## Deprecated APIs migrated

`Bukkit.createInventory(..., String)` → overload с Adventure Component в GraveContents
и Cardboard self-test. Старый публичный String-параметр конструктора сохранён; legacy
цвета преобразуются через LegacyComponentSerializer. Цель 26.2 поддерживает overload;
интеграционный self-test обновлён, но локально не запускался.

## Legacy code removed / Dead code removed

Миграционные readers и публичные events/permission nodes сохранены. Удалены заменённые
приватные `restoreOnFailure` и `clearFailedRemovalTombstones`, а также глобальная карта
unsavedRemovalTombstones. Остальные неоднозначные публичные helpers не удалялись.
README теперь честно сообщает, что command-aliases не читается регистрацией команд.

## Build result

**PASS**: `gradlew.bat clean build --console=plain --warning-mode all`, затем
`gradlew.bat build --console=plain --warning-mode all` после финальной проверки shutdown/save.
Лог последнего запуска: `.audit/build-final.log`.

JAR: `build/libs/CloverGraves-2.1.0.jar`. Его service descriptor содержит
`org.h2.Driver`, `org.sqlite.JDBC`, `com.mysql.cj.jdbc.Driver`.
В JAR не обнаружены встроенные классы Bukkit/Paper/Vault/PlaceholderAPI/NMS/SLF4J;
не обнаружены дубли ZIP entries. Hikari и bStats relocated.

## Test result

**PASS: 103 теста, 0 failures, 0 errors, 0 skipped.** Исходно: 90 тестов.
13 добавленных сценариев покрывают отказ и повтор SQL/JSON операций, транзакционный
откат миграции, SQLite file backend, освобождение restore claim, повторный прогрев,
геометрию размещения. HTML: `build/reports/tests/test/index.html`.

## Remaining warnings

- 11 предупреждений javac при полной перекомпиляции: getDescription, getUnsafe,
  BukkitObject streams, legacy display name и Bukkit PlayerProfile/SkullMeta.
  Они DEPRECATED BUT SUPPORTED на целевом compile API. Не заменены без проверки
  Cardboard и совместимости сериализованных предметов. Подтверждённых removed API нет.
- SQLite native loader на Java 25 предупреждает о native access. Для процесса, который
  использует SQLite, JVM flag `--enable-native-access=ALL-UNNAMED` разрешает загрузку.
  JVM пользователя и настройки его сервера не изменялись.
- Gradle warning об execution-time Task.project и Shadow service-merge warning устранены.

## Deferred changes / Manual review required

- **Runtime проверка обязательна перед production**: смерть с keepInventory и без,
  исключение при создании визуалов, быстрый подбор после GUI-click, два игрока у GUI,
  отмена/повтор teleport и отказ Vault, chunk unload/reload, рестарт с живыми могилами,
  Cardboard TextDisplay self-test. Сервера в рамках этой проверки не запускались.
- Реальное подключение MySQL и сервер Vault не тестировались. H2 и SQLite тестировались.
- Поиск OfflinePlayer по имени в history/restore остаётся синхронным. Перевод на UUID/
  profile resolver требует согласованного поведения для online/offline-mode серверов;
  категория PERFORMANCE, NORMAL, DO NOT AUTOMATICALLY CHANGE без такой политики.
- JSON fallback при заполненной SQL БД сохраняет старую политику: автоматическое
  объединение двух хранилищ не выполняется. Нужна отдельная миграция с устранением дублей,
  категория RELIABILITY, REVIEW BEFORE FIXING; данные автоматически не объединялись.
- Аварийное завершение JVM между claim исторической записи и сохранением новой могилы
  всё ещё требует ручного восстановления. Исправлен обычный отказ конструктора; полностью
  crash-atomic restore требует транзакционного переноса SQL-записи и отдельного дизайна.
- Новые могилы во время асинхронного startup/load и поздняя загрузка миров требуют игрового
  теста; изменения политики запуска и очередности загрузки не вносились.
- Bukkit global reload/Folia не объявлены поддержанными. CI actions major tags оставлены;
  обязательный remote Cardboard CI и release publication не запускались локально.
- VaultAPI latest: REQUIRES CURRENT VERSION CHECK; для него обновление не предлагается.

## Expected behavior changes / Compatibility status

Платформа **Paper/Cardboard 26.2, Java 25** сохранена. Форматы config.yml, messages.yml,
JSON и SQL schema не изменены. Новая версия H2 встроена в JAR. При нечитабельном storage
плагин теперь отключается с диагностикой; исправленное исключение не скрывается как
пустая БД. Неуспешные удаления повторяются при следующем flush. Старые callbacks не
могут завершить новый teleport; отменённое перемещение вызывает возврат оплаты.
При отсутствии Vault сохраняется прежняя политика бесплатного teleport с предупреждением.

## Release readiness

**READY WITH NOTES — готово к серверной проверке, production runtime не подтверждён.**
Рабочие данные сервера, GitHub и опубликованные релизы не менялись. Исходная версия
доступна в `.audit/baseline.zip`; кодовый diff — `.audit/source-changes.diff`.
