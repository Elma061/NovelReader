# NovelReader

一个简洁的安卓小说阅读器，使用 Kotlin + Jetpack Compose 编写。支持从手机本地文件夹导入 epub 小说，提供等距投影 3D 书架、书籍详情、分章阅读（含插图）与阅读进度记忆。

![书架截图](screenshots/bookshelf.jpg)

## 功能特性

- **等距投影 3D 书架**：每本书以立体书形式渲染（顶部书页盖 + 书脊 + 斜切封面），书脊竖向显示书名，居中的书自动「抽出 + 抬升」。
- **文件夹导入、逐本成书**：通过系统文件选择器选择文件夹，递归扫描其中所有 `.epub`，每个 epub 独立成为一本书；已添加的书籍不会重复添加（自动去重）。
- **书籍详情页**：展示封面、章节目录、上次读到位置，提供「开始阅读 / 继续阅读」按钮，右上角可删除该书。
- **书籍管理**：长按书架上的书可重命名；从书架点击封面到详情页有共享过渡动画。
- **epub 阅读**：
  - 分章阅读，支持上一章 / 下一章与目录跳转；
  - 正文插图显示；
  - 阅读进度自动保存，下次从上次位置继续。
- **状态保持**：旋转屏幕不会跳回主页，当前页面与章节自动恢复。
- **排版**：正文段落首行缩进两个字符。

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
2. 选择一个存放 epub 小说的文件夹，授予读取权限。
3. 在书架点击书籍进入详情页，点「开始阅读」即可阅读；详情页右上角可删除书籍。

## 项目结构

```
app/src/main/kotlin/com/reader/app/novel/
├── model/   数据模型（Book、BookFile、EpubBook、ReadState、EpubDetail）
├── data/    数据层（BookRepository、ReadingProgressRepository、EpubParser、NovelFiles）
└── ui/      界面层（书架、详情页、阅读器、导航）
```
