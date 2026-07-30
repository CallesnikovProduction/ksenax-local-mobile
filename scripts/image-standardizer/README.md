# Image Standardizer

Личный Kotlin/JVM-скрипт для приведения PNG к точному размеру. Он не подключён
к Android-приложению и не входит в его Gradle-граф.

## Запуск

Из корня репозитория. Относительные пути считаются от него:

```powershell
.\gradlew.bat -q -p scripts\image-standardizer run --args='--input "C:\path\image.png" --width 1080 --height 2400'
```

По умолчанию рядом появится `image_1080x2400.png`. Для явного имени:

```powershell
.\gradlew.bat -q -p scripts\image-standardizer run --args='--input "C:\path\image.png" --output "C:\path\result.png" --width 1080 --height 2400'
```

Обрезать прозрачные поля перед приведением к размеру:

```powershell
.\gradlew.bat -q -p scripts\image-standardizer run --args='--input "C:\path\header.png" --width 1800 --height 450 --trim-transparent'
```

Сжать содержимое по вертикали, поднять его и оставить снизу прозрачное место:

```powershell
.\gradlew.bat -q -p scripts\image-standardizer run --args='--input "C:\path\image.png" --width 1080 --height 2400 --vertical-scale 0.75 --vertical-anchor bottom --bottom-inset 0.20'
```

Для непрозрачного фона добавьте `--fill edge`: освободившееся сверху место
будет заполнено цветами верхнего края. Для прозрачного переднего слоя оставьте
стандартный `--fill transparent`. Сочетание `--vertical-scale 0.75` и
`--bottom-inset 0.20` оставляет `5%` свободного места сверху и `20%` снизу.

Если изображение уже сжато и его нужно только поднять, оставьте
`--vertical-scale 1.0` и укажите `--bottom-inset`. Холст сдвинется вверх,
свободное место снизу заполнится выбранным `--fill`.

Режимы:

- `stretch` — точный размер без полей и обрезания содержимого; пропорции могут
  немного измениться;
- `fit` — сохранить пропорции и целиком поместить изображение, возможны поля;
- `cover` — сохранить пропорции и заполнить весь размер, края могут обрезаться.

Существующий файл заменяется только с `--overwrite`. Запись выполняется через
временный PNG, поэтому исходный файл не останется частично записанным при ошибке.
