# PIXEL'S JOURNAL - MULTI-SCREEN & AUTOMOTIVE UI QUIRKS ONLY

## 2025-02-23 - Dynamic UI Scaling & Automotive Densities
**Screen Context:** Automotive 5120x1600 / Split Zone 2560x1600 & Mobile Standard
**Layout Trap:**
Overriding `Configuration.fontScale` or `densityDpi` programmatically in `attachBaseContext` compounds across configuration changes, causing severe UI distortion and double-scaled fonts on ultrawide vehicle head units (sw600dp).
**Solution:**
Rely on standard Android resource qualifiers (`values-sw600dp/dimens.xml`) rather than runtime programmatic scale overrides. `sw600dp` targets both full 5120x1600 display and 2560x1600 split zones effectively, expanding touch targets (≥60dp) and font sizes (20sp titles) without corrupting system density metrics.

## 2025-02-23 - Theme Attributes & Day/Night Contrast
**Screen Context:** Automotive Head Units Day/Night Driving Context
**Layout Trap:**
Hardcoded color values (e.g. `#1F000000` or `#EEEEEE`) in card stroke colors, text colors, or update banners remain bright during night mode or low-contrast during daylight.
**Solution:**
Use theme color attributes `?attr/colorOnPrimary`, `?android:attr/textColorPrimary`, `?android:attr/textColorSecondary`, and `?attr/colorControlHighlight`. Vector drawables in header controls must use `app:tint="?attr/colorOnPrimary"` to dynamically follow header background primary themes in both light and dark modes.
