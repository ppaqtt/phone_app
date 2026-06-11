package com.example.notes.util

/**
 * 应用更新日志的数据驱动定义。
 *
 * 之前的实现是把 20+ 个版本硬编码在 SettingsScreen.ChangelogCard 里,
 * 每发一个版本都要改两处 (CHANGELOG.md + Composable), 极易遗漏。
 * 现在统一在这个 data object 里维护, 渲染层只关心结构, 不再关心具体内容。
 */
// P112-FIX: Kotlin 1.8.22 不支持 `data object`, 改用 `object` (失去自动 toString/equals/hashCode, 业务无依赖)
object ChangelogData {

    /** 一条更新日志条目 */
    data class Entry(
        val version: String,
        val date: String,
        val items: List<String>
    )

    /**
     * 按版本号倒序排列的全部更新日志。
     * 新增版本时, 在列表头插入; 不要再回到 SettingsScreen 改硬编码。
     */
    val entries: List<Entry> = listOf(
        Entry(
            version = "v1.15.0",
            date = "2026-06-10",
            items = listOf(
                "新增：OCR 文字识别 — 集成 Google ML Kit 中文文本识别 (on-device, 无需网络)。" +
                    "笔记编辑页 IMAGE 工具面板加「识别文字」按钮, 选图后自动识别并插入到笔记正文; 大图自动缩放到 1920px",
                "升级：版本号 v1.14.0 → v1.15.0 (versionCode 24 → 25)"
            )
        ),
        Entry(
            version = "v1.14.0",
            date = "2026-06-10",
            items = listOf(
                "新增：语音转文字 — 封装 Android SpeechRecognizer (系统内置, 无需 API Key)。" +
                    "笔记编辑页底部工具栏加「语音」按钮, 点击请求录音权限后开始聆听; 识别完成自动插入到笔记正文",
                "升级：版本号 v1.13.0 → v1.14.0 (versionCode 23 → 24)"
            )
        ),
        Entry(
            version = "v1.13.0",
            date = "2026-06-10",
            items = listOf(
                "新增：代码块高亮 — 渲染 ```lang ... ``` 围栏代码块, 深色背景 + 语言标签 + 横向滚动; " +
                    "自带轻量关键字着色 (Kotlin / Java / Python / JS / Go / Rust / C/C++), 不引入第三方库",
                "升级：版本号 v1.12.0 → v1.13.0 (versionCode 22 → 23)"
            )
        ),
        Entry(
            version = "v1.12.0",
            date = "2026-06-10",
            items = listOf(
                "新增：统计仪表盘 — 4 个计数卡 (笔记 / 置顶 / 提醒 / 图片) + 字数卡 (中文字符 / 英文单词 / 平均每篇) " +
                    "+ 分类分布卡 (横向比例条) + 月度趋势卡 (最近 6 个月竖向柱状图)",
                "升级：版本号 v1.11.0 → v1.12.0 (versionCode 21 → 22)"
            )
        ),
        Entry(
            version = "v1.11.0",
            date = "2026-06-10",
            items = listOf(
                "新增：嵌套分类 — CategoryEntity 加 parentId 字段, 单层缩进 (0=顶级, 1=子级)。" +
                    "分类管理列表按父→子顺序渲染, 子分类缩进 20dp + ↳ 箭头图标; 新增分类时可选父分类, " +
                    "父分类候选自动排除自身和所有 descendants 防止循环引用; 删除父分类时自动把子分类提升为顶级",
                "升级：Room v7→v8 AutoMigration; 备份导出/导入同步维护 parentOldId 映射, 老备份默认顶级; " +
                    "版本号 v1.10.0 → v1.11.0 (versionCode 20 → 21)"
            )
        ),
        Entry(
            version = "v1.10.0",
            date = "2026-06-10",
            items = listOf(
                "新增：每日重复提醒 — NoteEntity 加 reminderRepeat 字段 (NONE/DAILY/WEEKLY/MONTHLY/YEARLY)。" +
                    "ReminderWorker 触发后若 repeat != NONE, 自动用 Calendar.add 排下次触发",
                "升级：Room v6→v7 AutoMigration; 版本号 v1.9.0 → v1.10.0 (versionCode 19 → 20)"
            )
        ),
        Entry(
            version = "v1.9.0",
            date = "2026-06-10",
            items = listOf(
                "新增：PDF / 长图导出 — 笔记编辑页顶部 MoreVert 下拉新增「导出为 PDF」和「导出为长图 (PNG)」两项, " +
                    "走 SAF CreateDocument。PDF 走 android.graphics.pdf.PdfDocument 渲染 (A4 自动分页), " +
                    "长图走 Bitmap + StaticLayout 拼接 (2x 像素密度)",
                "升级：版本号 v1.8.0 → v1.9.0 (versionCode 18 → 19)"
            )
        ),
        Entry(
            version = "v1.8.0",
            date = "2026-06-10",
            items = listOf(
                "新增：应用锁 — AppLockStore 持久化 PIN 的 SHA-256 哈希 (不存明文); " +
                    "AppLockGate 包裹 NavGraph, 启动 / 切回前台检测 5 分钟解锁宽限期; " +
                    "AppLockScreen PIN 数字键盘 + 圆点指示器, 失败 30s 冷却",
                "升级：版本号 v1.7.0 → v1.8.0 (versionCode 17 → 18)"
            )
        ),
        Entry(
            version = "v1.7.0",
            date = "2026-06-10",
            items = listOf(
                "新增：App 快捷方式 (长按桌面图标) — res/xml/shortcuts.xml 注册 3 个动态快捷方式 (新建笔记 / 搜索 / 回收站)。" +
                    "AndroidManifest MainActivity meta-data 指向 shortcuts.xml",
                "升级：版本号 v1.6.0 → v1.7.0 (versionCode 16 → 17)"
            )
        ),
        Entry(
            version = "v1.6.0",
            date = "2026-06-10",
            items = listOf(
                "新增：桌面小部件 (AppWidget) — 4x2 圆角卡片显示最近 5 条笔记 (按 updated_at 倒序), 标题 + 内容预览; " +
                    "列表项点击通过 setOnClickFillInIntent 打开笔记; 底部 + 按钮快速新建; " +
                    "saveNote 后调 NotesAppWidget.requestRefresh 触发刷新",
                "升级：版本号 v1.5.0 → v1.6.0 (versionCode 15 → 16)"
            )
        ),
        Entry(
            version = "v1.5.0",
            date = "2026-06-10",
            items = listOf(
                "新增：回收站 — NoteEntity 加 deletedAt 字段 (null=正常, 非 null=已删除); " +
                    "删除改走软删除, 列表 / 搜索 / 按分类观察自动加 deleted_at IS NULL 过滤; " +
                    "TrashScreen 显示 30 天内已删笔记, 每条 2 动作: 恢复 / 永久删除",
                "升级：Room v5→v6 AutoMigration; 版本号 v1.4.0 → v1.5.0 (versionCode 14 → 15)"
            )
        ),
        Entry(
            version = "v1.4.0",
            date = "2026-06-10",
            items = listOf(
                "新增：数据备份 / 恢复 — 全部笔记 / 分类 / 图片导出为 JSON, 走 SAF CreateDocument / OpenDocument; " +
                    "DTO 与 Entity 解耦, 兼容老备份; AUTO_INCREMENT 冲突通过「老 id → 新 id」映射表解决",
                "升级：版本号 v1.3.0 → v1.4.0 (versionCode 13 → 14)"
            )
        )
    )
}
