# NovelReader

一个简洁的安卓小说阅读器，使用 Kotlin + Jetpack Compose 编写。支持从手机本地文件夹导入小说，提供书架、书籍详情、epub 分章阅读（含插图）与阅读进度记忆。

## 功能特性

- **书架**：通过系统文件选择器添加小说文件夹，读取权限持久化，应用重启后仍可访问；支持重命名书籍。
- **书籍详情页**：展示封面、章节目录、上次读到位置，提供「开始阅读 / 继续阅读」按钮。
- **epub 阅读**：
  - 分章阅读，支持上一章 / 下一章与目录跳转；
  - 正文插图显示；
  - 阅读进度自动保存，下次从上次位置继续。
- **状态保持**：旋转屏幕不会跳回主页，当前页面与章节自动恢复。
- **排版**：正文段落首行缩进两个字符。
- **格式识别**：自动识别文件夹内的 `.txt` / `.epub` 文件（TXT 阅读功能开发中）。

## 技术栈

| 项目 | 版本 |
| --- | --- |
| Kotlin | 2.0.21 |
| Jetpack Compose | BOM 2024.10.01（Material 3） |
| Android Gradle Plugin | 8.7.3 |
| Gradle | 8.9 |
| compileSdk / targetSdk | 35 |
| minSdk | 24（Android 7.0） |

## 构建

> 需要 JDK 17 或 21。JDK 25 与 Kotlin 2.0.21 不兼容，会导致编译失败。

**方式一：Android Studio**

直接用 Android Studio 打开项目，等待 Gradle 同步完成后点击 Run。

**方式二：命令行**

```bash
# Windows
gradlew.bat assembleDebug

# macOS / Linux
./gradlew assembleDebug
```

生成的 APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。

## 使用说明

1. 安装并打开应用，点击右下角「添加书籍」。
2. 选择一个存放小说的文件夹，授予读取权限。
3. 在书架点击书籍进入详情页，点「开始阅读」即可阅读。

## 项目结构

```
app/src/main/kotlin/com/reader/app/novel/
├── model/   数据模型（Book、BookFile、EpubBook、ReadState、EpubDetail）
├── data/    数据层（BookRepository、ReadingProgressRepository、EpubParser、NovelFiles）
└── ui/      界面层（书架、详情页、阅读器、导航）
```
