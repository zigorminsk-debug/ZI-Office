# ZI Office

Мобильное приложение для чтения и работы с документами: PDF, Word, Excel,
PowerPoint, RTF, текстовые файлы. Android, minSdk 24.

## Возможности

- Чтение PDF (нативный рендер с масштабированием и переходом к странице),
  Word, Excel, PPTX, RTF, TXT
- Поиск по документу, закладки, тёмная тема
- Озвучка текста (TTS), режим чтения
- Распознавание текста в PDF (OCR, русский язык, Tesseract)
- Инструменты PDF: поворот, извлечение/удаление страниц, пустая страница,
  обратный порядок, нумерация страниц
- Сканер: фото страниц → PDF
- Объединение нескольких PDF в один
- Облака: Google Drive и Яндекс Диск (папка через SAF + встроенный браузер)
- Сохранение/отправка/печать, сохранение в облако

## Сборка

Требуется JDK 17 и Android SDK (platform 34, build-tools 34).

```bash
./gradlew assembleRelease
# APK: app/build/outputs/apk/release/app-release.apk
```

Или открыть проект в Android Studio и собрать.

## Подпись

Ключ выпуска: `signing/myoffice-release.p12` (копия в `app/`).
Пароль и alias — в `app/build.gradle` и в `signing/ПОДПИСЬ.txt`.

> ⚠️ Без этого ключа нельзя выпускать обновления того же приложения.
> Храните его в надёжном месте. Репозиторий приватный — не делайте его
> публичным, не удаляя сначала ключ из истории.

## Автообновление

Приложение само проверяет наличие новой версии и предлагает скачать APK.
Сборка и публикация релизов — GitHub Actions по тегу `v*`.

**Подробная инструкция (включая одноразовую настройку): [АВТООБНОВЛЕНИЕ.md](АВТООБНОВЛЕНИЕ.md)**

Коротко:
1. Публичный репозиторий `zigorminsk-debug/ZI-Office-Release` + secret `RELEASE_TOKEN` (разово).
2. Увеличить `versionName`/`versionCode` в `app/build.gradle`.
3. `git tag v2.XX && git push origin v2.XX` — всё, релиз опубликован.

## CI

`.github/workflows/`:

- `build.yml` — компиляция на пулы в `main` и ветки `arena/*`; при падении
  создаёт issue с хвостом лога сборки;
- `release.yml` — сборка release-APK по тегу `v*` (или вручную) и публикация
  GitHub Release в публичном репозитории релизов.

## Структура

```
app/src/main/java/com/docreader/app/
  MainActivity          главное окно (недавние файлы)
  ViewerActivity        просмотрщик (PDF нативно, остальное — WebView)
  CloudActivity         подключение облаков
  CloudBrowseActivity   файловый менеджер подключённой папки (SAF)
  CloudBrowserActivity  встроенный браузер облака
  ScanActivity          скан в PDF
  MergeActivity         объединение PDF
  UpdateManager         автообновление через GitHub
  ...
app/src/main/assets/
  viewer.html           рендер Word/Excel/PPT/текста в WebView
  pdftools.html         инструменты PDF (pdf-lib)
  js/                   pdf.js, mammoth, xlsx, jszip, pdf-lib
  tessdata/             модель OCR (rus.traineddata)
```

## Версия

`versionName` и `versionCode` — в `app/build.gradle`.
Номер версии в приложении берётся из `BuildConfig`.

---

## ZI Office StartFix (Windows)

В каталоге [`StartFix/`](StartFix/README.md) — отдельная утилита для Windows:
восстановление кнопки и меню «Пуск», поиска и панели задач в Windows 10/11
и Windows Server 2012–2025. Самодостаточные EXE-файлы (без .NET, Visual C++
и UCRT) лежат в `StartFix/dist/`. Подробности — в
[StartFix/README.md](StartFix/README.md).
