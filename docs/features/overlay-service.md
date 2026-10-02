# 📜 Документация модуля: Служба плавающих оверлеев (`ForegroundOverlayService`)

Модуль `ForegroundOverlayService` (`com.flyme.fscrn.overlay.ForegroundOverlayService`) является ключевым компонентом приложения Flyme Tweak. Служба работает в фоновом режиме (Foreground Service) и обеспечивает отображение управляющих оверлейных кнопок поверх всех окон на автомобильных мультимедийных системах (в частности, LynkCo на базе Android).

---

## 🎯 Назначение модуля

В автомобилях LynkCo и схожих головных устройствах на Android 12+ присутствуют два широкоформатных дисплея. Стандартные приложения часто открываются в ограниченном или стандартном окне. Данный модуль решает следующие задачи:
1. **Быстрый запуск (`Quick Launch`):** Отображает всегда доступную плавающую кнопку-меню для мгновенного вызова выбранных пользователем приложений из любого места системы.
2. **Переключение полноэкранного режима (`Fullscreen Toggle`):** При запуске целевых приложений отображает динамическую кнопку разворота. При нажатии приложение перезапускается на вторичном дисплее (`Display ID 1003`), принудительно разворачивая его на полный экран.
3. **Поддержка двух дисплеев:** Дублирует и зеркалирует плавающие кнопки на вторичном виртуальном/физическом дисплее, чтобы элементы управления не пропадали при переносе окон.

---

## 🏗 Архитектура и поток данных (Data Flow)

### 1. Инициализация и запуск Foreground Service
* Служба запускается как Foreground Service с каналом уведомлений `OverlayServiceChannel` (ID уведомления: `1`) и приоритетом `IMPORTANCE_LOW`.
* При создании (`onCreate`) инициализируются:
  * Основной `WindowManager` (`defaultWindowManager`).
  * Контекст вторичного дисплея (`setupSecondaryDisplayContext()`).
  * Подписка на изменения настроек `SharedPreferences.OnSharedPreferenceChangeListener`.
  * Периодический опрос активного приложения через `UsageStatsManager`.

```
[Система Android]
       │
       ▼
[ForegroundOverlayService]
       ├──► [NotificationManager] (Foreground Notification ID 1)
       ├──► [WindowManager - Primary Display] ────► [Quick Launch / Fullscreen Views]
       ├──► [WindowManager - Secondary Display 1003] ─► [Mirrored Overlay Views]
       └──► [UsageStatsManager Polling Loop (1s)]
```

### 2. Отслеживание активного приложения (UsageStatsManager Polling)
* В режиме `Foreground Service` служба каждые **1000 миллисекунд (1 секунда)** через `Handler` выполняет опрос событий `UsageStatsManager.queryEvents(...)` за последние 2 секунды.
* При обнаружении события `ACTIVITY_RESUMED` извлекается имя пакета приложения (`packageName`).
* При смене текущего пакета вызывается метод `handlePackageChange(newPackage)`.

### 3. Дублирование оверлеев на второй экран (Display ID 1003)
* Метод `setupSecondaryDisplayContext()` запрашивает `DisplayManager` для поиска дисплея с ID `1003`.
* Если дисплей найден:
  * Создается контекст дисплея через `createDisplayContext(secondaryDisplay)`.
  * На Android 12+ (API 31+) создается оконный контекст `createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)`.
  * Получается отдельный `secondaryWindowManager`.
* При отображении кнопок их горизонтальная гравитация (`Gravity.START` / `Gravity.END`) на втором экране **инвертируется** относительно первого экрана, обеспечивая удобный доступ с обеих сторон автомобильной панели.

---

## 🧩 Ключевые компоненты и файлы

| Компонент / Файл | Роль и ответственность |
| :--- | :--- |
| `ForegroundOverlayService.java` | Основной класс службы (`app/src/main/java/com/flyme/fscrn/overlay/ForegroundOverlayService.java`). |
| `R.layout.overlay_layout` | Разметка плавающей кнопки (`app/src/main/res/layout/overlay_layout.xml`), содержащая `ImageView` (`@id/overlay_image_view`). |
| `SharedPreferences` | Файл настроек `<package_name>_preferences`, из которого считываются параметры и списки приложений. |
| `UsageStatsManager` | Системный сервис Android для отслеживания текущего приложения в фокусе без необходимости в службе AccessibilityService. |

---

## ⚙️ Конфигурация и параметры SharedPreferences

Служба автоматически реагирует на изменение следующих ключей в `SharedPreferences`:

| Ключ SharedPreferences | Тип данных | Назначение |
| :--- | :--- | :--- |
| `quick_launch_enabled` | `boolean` | Включение/выключение плавающей кнопки быстрого запуска. |
| `quick_launch_apps` | `Set<String>` | Набор имен пакетов приложений, отображаемых в меню быстрого запуска. |
| `ql_position_x` | `String` | Горизонтальное положение кнопки Quick Launch (`"left"`, `"center"`, `"right"`). |
| `ql_position_y` | `int` | Вертикальная позиция кнопки Quick Launch в пикселях. |
| `ql_button_size` | `int` | Размер кнопки Quick Launch в `dp` (по умолчанию `48`). |
| `fullscreen_overlay_enabled` | `boolean` | Включение/выключение кнопки разворота на весь экран. |
| `fullscreen_apps` | `Set<String>` | Набор имен пакетов приложений, для которых должна появляться кнопка полноэкранного режима. |
| `fs_position_x` | `String` | Горизонтальное положение кнопки Fullscreen (`"left"`, `"center"`, `"right"`). |
| `fs_position_y` | `int` | Вертикальная позиция кнопки Fullscreen в пикселях. |
| `fs_button_size` | `int` | Размер кнопки Fullscreen в `dp` (по умолчанию `48`). |

---

## 🔄 Состояния и логика работы

### Режимы кнопки Fullscreen Toggle
1. **Скрыта:** Текущее фоновое приложение не входит в список `fullscreen_apps` или функция отключена в настройках (`fullscreen_overlay_enabled == false`).
2. **Обычный режим (Иконка `ic_fullscreen_enter` `<>`):** Целевое приложение запущено в стандартном окне.
3. **Развернутый режим (Иконка `ic_fullscreen_exit` `><`):** Приложение переведено на Display ID 1003.

### Переключение режима полноэкранного отображения (`toggleFullscreen()`)
При нажатии на кнопку полноэкранного режима:
1. Флаг `isTargetAppFullscreen` переключается (`true` / `false`).
2. Сохраняется имя текущего активного пакета в `activeFullscreenPackage`.
3. Запрашивается `getLaunchIntentForPackage(...)` для целевого приложения.
4. Добавляются флаги `Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP`.
5. При включении полноэкранного режима передаются опции запуска:
   ```java
   Bundle bundle = ActivityOptions.makeBasic()
           .setLaunchDisplayId(1003)
           .toBundle();
   startActivity(launchIntent, bundle);
   ```
6. При выходе из полноэкранного режима приложение запускается обычным образом без параметров `ActivityOptions`.

### Обработка жестов и перетаскивания (`setupDragAndClick`)
* Служба отслеживает `MotionEvent` на кнопках оверлея.
* Для отличия короткого клика от перетаскивания используется системный порог сдвига `ViewConfiguration.get(context).getScaledTouchSlop()`:
  * Если вертикальный сдвиг по оси Y превышает порог, событие расценивается как перетаскивание, и параметр `WindowManager.LayoutParams.y` обновляется в реальном времени.
  * При отпускании пальца (`ACTION_UP`) новое значение `y` сохраняется в `SharedPreferences` (`ql_position_y` или `fs_position_y`).
  * Если жест не выходил за пределы порога touch slop, выполняется передаваемое действие `onClick` (`showQuickLaunchMenu` или `toggleFullscreen`).

---

## 🛡 Граничные случаи и обработка ошибок (Edge Cases)

1. **Отсутствие второго дисплея (Display ID 1003):**
   * Если `displayManager.getDisplay(1003)` возвращает `null`, `secondaryContext` приравнивается к `this`, а `secondaryWindowManager` к `defaultWindowManager`. Повторное создание оверлея на втором экране пропускается, приложение корректно работает в одноэкранном режиме.

2. **Временная смена фокуса на системный лончер или SystemUI:**
   * В методе `handlePackageChange` явно игнорируются пакеты `com.android.launcher3` и `com.android.systemui`. Это предотвращает сброс состояния полноэкранного режима и исчезновение кнопки при открытии шторки уведомлений или нажатии кнопки Домой.

3. **Пустой список приложений для быстрого запуска:**
   * При попытке открыть меню быстрого запуска без выбранных приложений выводится Toast-уведомление `"Нет приложений для быстрого запуска!"` без всплытия диалогового окна.

4. **Ошибка запуска приложения с Display ID 1003:**
   * Вызов `startActivity(launchIntent, bundle)` обернут в блок `try-catch`. В случае если прошивка устройства блокирует запуск с указанием дисплея 1003, происходит fallback запуск приложения через стандартный `startActivity(launchIntent)`.
