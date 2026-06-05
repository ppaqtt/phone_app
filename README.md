# 便签 (Notes) — Android

一个使用 **Kotlin + Jetpack Compose + Material 3 + Room** 构建的极简现代风笔记应用,支持笔记 CRUD、分类、标签、置顶、颜色标记、搜索等常用功能。

## 功能特性

| 模块 | 说明 |
| --- | --- |
| 笔记列表 | 卡片式布局,支持置顶、颜色、分类色点和更新时间 |
| 笔记编辑 | 标题 / 内容 / 标签 / 分类 / 颜色 / 置顶,新建与编辑共用同一页面 |
| 分类管理 | 增删分类,自定义分类颜色 |
| 全文搜索 | 搜索标题、内容、标签 (大小写不敏感) |
| 分类筛选 | 在主页通过 Chip 切换不同分类 |
| 主题 | Material 3,深色 / 浅色模式自动跟随系统,Android 12+ 支持动态取色 |
| 持久化 | Room 本地数据库,Flow 驱动 UI,无网络请求 |

## 技术栈

- **语言**: Kotlin 2.0.21
- **UI**: Jetpack Compose (Compose BOM 2024.10.01) + Material 3
- **架构**: 单 Activity + Compose Navigation + MVVM (StateFlow)
- **数据库**: Room 2.6.1 (KSP 注解处理器)
- **依赖管理**: Version catalog (`gradle/libs.versions.toml`)
- **AGP**: 8.7.2 / Gradle 8.10.2
- **JVM**: Java 17 字节码
- **minSdk**: 24 (Android 7.0) / **targetSdk**: 34

## 工程结构

```
.
├── build.gradle.kts              # 根 build,只做 plugin 声明
├── settings.gradle.kts           # 模块与仓库
├── gradle/libs.versions.toml     # 版本目录
├── gradle/wrapper/
│   └── gradle-wrapper.properties
└── app/
    ├── build.gradle.kts          # 应用模块 build
    ├── proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/com/example/notes/
        │   ├── NotesApplication.kt
        │   ├── MainActivity.kt
        │   ├── data/                 # Room: Entity / DAO / Database
        │   ├── repository/           # NotesRepository
        │   ├── ui/
        │   │   ├── theme/            # Color / Type / Theme (M3)
        │   │   ├── components/       # NoteCard
        │   │   ├── screens/          # 列表 / 编辑 / 分类 / 搜索
        │   │   └── viewmodel/        # NotesViewModel + Factory
        │   ├── nav/                  # NotesNavGraph
        │   └── util/                 # TimeFormat
        └── res/
            ├── values/               # strings / colors / themes
            ├── values-night/         # 深色主题 bridge
            ├── drawable/             # 启动图前景
            ├── mipmap-anydpi-v26/    # 自适应启动图
            ├── mipmap-anydpi/        # 兼容旧版本启动图
            └── xml/                  # 备份规则
```

## 本地构建

1. 安装 **Android Studio Koala (2024.1.1)** 或更高版本。
2. 在项目根目录创建或修改 `local.properties`,写入你的 Android SDK 路径:
   ```
   sdk.dir=/Users/you/Library/Android/sdk
   ```
3. 在 Android Studio 中 **Open** 项目根目录,等待 Gradle Sync。
4. 连接 Android 设备或启动模拟器,点击 ▶ 运行。

或使用命令行:
```bash
./gradlew :app:assembleDebug
# 产物: app/build/outputs/apk/debug/app-debug.apk
```

> 注:本仓库不包含 `gradle/wrapper/gradle-wrapper.jar` 和 `gradlew` 脚本。
> 第一次在 Android Studio 中打开时,IDE 会自动生成。也可以手动执行
> `gradle wrapper --gradle-version 8.10.2` 补全。

## 下一步可扩展

- [ ] Markdown 编辑 + 预览
- [ ] 拖拽排序 / 自定义置顶顺序
- [ ] 笔记提醒 (WorkManager + Notification)
- [ ] 云同步 (Firebase / 自托管)
- [ ] 富文本 (图片、附件、代码块)
- [ ] 回收站 / 撤销删除
- [ ] 笔记导出 (PDF / Markdown)
- [ ] 应用图标替换为正式设计稿

## 许可

仅作为学习脚手架,可自由使用与修改。
