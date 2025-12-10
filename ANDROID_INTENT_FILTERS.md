# Android Intent Filters Configuration

Для того чтобы приложение появлялось в списке "Открыть с помощью" для Excel файлов, необходимо настроить Intent Filters в Android манифесте.

## Конфигурация для Flet

При сборке APK через `flet build apk`, добавьте следующие параметры:

```bash
flet build apk \
  --android-intent-filters android_intent_filters.json
```

## Файл android_intent_filters.json

Создайте файл `android_intent_filters.json` в корне проекта со следующим содержимым:

```json
[
  {
    "action": "android.intent.action.VIEW",
    "category": [
      "android.intent.category.DEFAULT",
      "android.intent.category.BROWSABLE"
    ],
    "data": [
      {
        "mimeType": "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
      },
      {
        "mimeType": "application/vnd.ms-excel"
      }
    ]
  },
  {
    "action": "android.intent.action.SEND",
    "category": [
      "android.intent.category.DEFAULT"
    ],
    "data": [
      {
        "mimeType": "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
      },
      {
        "mimeType": "application/vnd.ms-excel"
      }
    ]
  }
]
```

## Обработка входящих файлов

Добавьте в начало функции `main()` в `flet_app.py`:

```python
def main(page: ft.Page):
    # Handle incoming files from intent
    if page.platform in [ft.PagePlatform.ANDROID, ft.PagePlatform.IOS]:
        # Get file path from intent if available
        # This will be implemented when Flet adds support for intent data
        pass
    
    app = AssemblyApp(page)
```

## Примечание

На данный момент (декабрь 2024) Flet может иметь ограниченную поддержку для получения данных из Intent. Проверьте актуальную документацию Flet для получения информации о работе с Intent Filters.
