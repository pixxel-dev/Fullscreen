# База знаний проекта

## Ссылки на сторонние проекты с открытым исходным кодом (Референсы)

1. **AdbLib**
   - Библиотека для реализации локального ADB-клиента на Java.
   - Ссылка: https://github.com/cgutman/AdbLib.git
   - Использование: Встроен в проект скриптом `get_adblib.sh`, используется для работы с ADB через сокет 127.0.0.1:5555.

2. **LADB**
   - Проект для реализации локального ADB-клиента с поддержкой TLS-сопряжения (Pairing Protocol) на Android 11+.
   - Ссылка: https://github.com/tytydraco/LADB
   - Примечание: Используется как референс для реализации функции "Адаптировать подключение ADB для Android 11+ с кодом сопряжения" (Backlog пункт 3).
   - Технические детали: Для подключения к Android 11+ требуется реализовать UI для ввода 6-значного кода и порта, а также установить TLS-соединение.

3. **ADBCommandCenter (Или аналогичный)**
   - Проект с примером реализации UI для отправки системных твиков через ADB.
   - Примечание: Референс для задачи "Наполнить вкладку 'Твики' полезными системными настройками через ADB" (Backlog пункт 2).

4. **Shizuku**
   - API и провайдер для работы с системным API без полного root-доступа.
   - Ссылка: https://github.com/RikkaApps/Shizuku
   - Использование: Уже частично интегрирован (в dependencies `build.gradle`), нужен для тихой установки и выполнения системных команд.

5. **MaterialFiles**
   - Открытый файловый менеджер, послужил вдохновением для UI вкладки менеджера файлов.
   - Ссылка: https://github.com/zhanghai/MaterialFiles

*(Документ будет пополняться по мере поступления новых ссылок и референсов от пользователя)*

## ADB Команды для выдачи разрешений (Для работы плавающих окон на автомобиле)
На некоторых автомобильных магнитолах (включая LynkCo) интерфейс выдачи прав может быть заблокирован. Чтобы приложение `Flyme Tweak` смогло отображать плавающие кнопки, необходимо выполнить следующие команды через ADB:

**1. Выдача разрешения на отображение поверх других окон (SYSTEM_ALERT_WINDOW):**
```bash
adb shell appops set com.flyme.fscrn SYSTEM_ALERT_WINDOW allow
```

**2. (Опционально) Выдача разрешения на доступ к статистике использования (USAGE_STATS):**
*Нужно для надежного отслеживания активных окон, если AccessibilityService отключен.*
```bash
adb shell pm grant com.flyme.fscrn android.permission.PACKAGE_USAGE_STATS
adb shell appops set com.flyme.fscrn GET_USAGE_STATS allow
```

**3. (Опционально) Включение Службы Специальных Возможностей (AccessibilityService):**
*Делает появление кнопок мгновенным без задержек.*
```bash
adb shell settings put secure enabled_accessibility_services com.flyme.fscrn/com.flyme.fscrn.service.OverlayService
adb shell settings put secure accessibility_enabled 1
```
