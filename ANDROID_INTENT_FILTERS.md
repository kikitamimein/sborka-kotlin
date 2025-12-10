# Настройка Intent Filters для Flet (Альтернативный метод)

## Проблема

Параметр `--android-intent-filters` не поддерживается в Flet 0.28.3.

## Решение: Ручная настройка после сборки

Для добавления Intent Filters в уже собранный APK нужно:

### Вариант 1: Обновить Flet (когда появится поддержка)

Следите за обновлениями Flet: https://github.com/flet-dev/flet/releases

### Вариант 2: Ручное редактирование AndroidManifest.xml

1. Распакуйте APK:
```bash
apktool d app-release.apk
```

2. Отредактируйте `AndroidManifest.xml`, добавив в `<activity>`:
```xml
<intent-filter>
    <action android:name="android.intent.action.VIEW" />
    <category android:name="android.intent.category.DEFAULT" />
    <category android:name="android.intent.category.BROWSABLE" />
    <data android:mimeType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" />
    <data android:mimeType="application/vnd.ms-excel" />
</intent-filter>
```

3. Пересоберите APK:
```bash
apktool b app-release.apk -o app-modified.apk
```

4. Подпишите APK:
```bash
jarsigner -keystore your-keystore.jks app-modified.apk your-alias
```

### Вариант 3: Использовать Flutter напрямую

Если нужны Intent Filters, можно собрать приложение через Flutter:

1. Экспортируйте Flutter проект:
```bash
flet build apk --export-only
```

2. Отредактируйте `android/app/src/main/AndroidManifest.xml`

3. Соберите через Flutter:
```bash
cd build/flutter
flutter build apk
```

## Текущее состояние

На данный момент APK собирается **без Intent Filters**. Все остальные функции работают:
- ✅ Поддержка .xls файлов
- ✅ Автосохранение сессии
- ✅ Функция "Поделиться" (page.share)
- ✅ Промежуточная генерация файлов
- ❌ Открытие через "Открыть с помощью" (требует Intent Filters)

## Рекомендация

Используйте приложение как есть. Файлы можно:
- Открывать через встроенный файловый менеджер приложения
- Делиться через кнопку "Поделиться файлом" после завершения сборки
