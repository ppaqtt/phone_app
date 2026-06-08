# 清笺笔记编辑器与主界面改造 Spec

## Why
清笺笔记 App 的「编辑笔记」页面工具栏冗余、AI 入口已下线、撤销/重做失效、主界面缺少批量操作入口，亟需对底部工具栏、工具面板、元信息行、右滑手势、底层数据结构做一次系统改造，以匹配「极简高效」的产品定位。

## What Changes
- 底部 6 图标工具栏重组：分栏 / Aa / 列表 / 待办 / 图片 / 更多，**AI 入口彻底下线**
- 分栏面板拆为 4 个子页签：文字样式 / 符号 / 分割线 / 图文模版
- Aa 面板提供 B / I / U / S / 高亮 + 8 档字号 + 7 色字体颜色
- 列表面板提供左对齐 / 居中 / 右对齐 / 圆点 / 数字 / 字母 6 种排版入口
- 待办面板单按钮切换：点击插入 ☐，再次点击去除 ☐
- 图片面板：图片或视频 + 拍照（真实调起摄像头），去除文档扫码
- 更多面板：涂鸦 / 表格 / 音频 3 个新入口
- 涂鸦：弹白板 + 颜色 / 粗细 / 撤销 / 重做 / 清空，保存为 PNG 插入笔记
- 表格：弹出行 × 列输入框，生成等宽对齐 markdown 表格插入正文
- 音频：通过 `OpenDocument(audio/*)` 读取手机音频，插入到正文并展示可点击条目
- 撤销 / 重做：基于 `NoteSnapshot(title, content)` 栈，TopAppBar 右上角按钮接上
- 元信息行简化：去掉置顶和提醒小图标，只保留日期 / 字数 / 分类
- 主界面右滑笔记卡片：弹出「置顶 / 标签 / 删除 / 移动 / 重要度」5 个动作菜单
- 底层：NoteEntity 新增 `priority` 字段，Room version 升到 4，配套 DAO/Repository/ViewModel

## Impact
- Affected specs: 编辑器工具面板、主界面笔记列表、数据库 schema
- Affected code:
  - [NoteEditScreen.kt](file:///workspace/app/src/main/java/com/example/notes/ui/screens/NoteEditScreen.kt)
  - [NotesListScreen.kt](file:///workspace/app/src/main/java/com/example/notes/ui/screens/NotesListScreen.kt)
  - [UndoRedoState.kt](file:///workspace/app/src/main/java/com/example/notes/ui/screens/UndoRedoState.kt)
  - [DoodleDialog.kt](file:///workspace/app/src/main/java/com/example/notes/ui/screens/DoodleDialog.kt)
  - [TableInsertDialog.kt](file:///workspace/app/src/main/java/com/example/notes/ui/screens/TableInsertDialog.kt)
  - [NotesViewModel.kt](file:///workspace/app/src/main/java/com/example/notes/ui/viewmodel/NotesViewModel.kt)
  - [NotesRepository.kt](file:///workspace/app/src/main/java/com/example/notes/repository/NotesRepository.kt)
  - [Daos.kt](file:///workspace/app/src/main/java/com/example/notes/data/Daos.kt)
  - [Entities.kt](file:///workspace/app/src/main/java/com/example/notes/data/Entities.kt)
  - [AppDatabase.kt](file:///workspace/app/src/main/java/com/example/notes/data/AppDatabase.kt)
  - [AndroidManifest.xml](file:///workspace/app/src/main/AndroidManifest.xml)
  - [file_paths.xml](file:///workspace/app/src/main/res/xml/file_paths.xml)

## ADDED Requirements

### Requirement: REQ-01 AI 工具栏下线
编辑器底部工具栏不再包含任何 AI / 智能 / 智能写作 入口。

#### Scenario: 编辑笔记时不显示 AI 入口
- **WHEN** 用户打开任意笔记的编辑页
- **THEN** 底部工具栏 6 个图标依次为：分栏 / Aa / 列表 / 待办 / 图片 / 更多
- **AND** 不再出现第 7 个 AI / AutoAwesome / 智能相关图标

#### Scenario: 旧 APK 完全卸载后才能体现改动
- **WHEN** 用户已经安装过包含 AI 工具栏的旧版 APK
- **THEN** 必须先 `adb uninstall com.example.notes.debug` 或长按图标卸载
- **AND** 重新 `Run` / `installDebug` 安装新 APK 才能看到 6 图标版本

### Requirement: REQ-02 分栏面板 4 子页签
点击「分栏」按钮弹出面板，顶部 4 个页签可切换。

#### Scenario: 切换页签不重置面板
- **WHEN** 用户点击「文字样式 / 符号 / 分割线 / 图文模版」中的任一页签
- **THEN** 内容区切换为对应子项网格
- **AND** 已选中页签高亮为黄色

#### Scenario: 文字样式网格插入
- **WHEN** 用户点击文字样式网格中的某项
- **THEN** 该项对应的 markdown 片段追加到正文末尾
- **AND** 触发撤销 / 重做栈记录

### Requirement: REQ-03 Aa 文字格式面板
点击「Aa」按钮弹出 B/I/U/S + 高亮 + 字号 + 颜色三层工具。

#### Scenario: 点击 B/I/U/S 按钮
- **WHEN** 用户点击 B / I / U / S / 高亮中的某个按钮
- **THEN** 对应的 markdown 标记插入正文末尾

#### Scenario: 选中字号 16
- **WHEN** 用户点击字号行的「16」
- **THEN** 「16」字样高亮为黄色
- **AND** 插入 `[size=16]` 片段

#### Scenario: 选中字体颜色
- **WHEN** 用户点击 7 个色块中的任一色块
- **THEN** 该色块描边变粗并高亮
- **AND** 插入 `[color=#xxx]` 片段

### Requirement: REQ-04 列表面板 6 按钮
点击「列表」按钮弹出 6 个等距按钮：左对齐 / 居中 / 右对齐 / 圆点 / 数字 / 字母。

#### Scenario: 点击左对齐 / 居中 / 右对齐
- **WHEN** 用户点击对齐按钮
- **THEN** 插入 `[align=left|center|right]` markdown 片段

#### Scenario: 点击圆点 / 数字 / 字母编号
- **WHEN** 用户点击编号按钮
- **THEN** 分别插入 `• ` / `1. ` / `a. ` 片段

### Requirement: REQ-05 待办切换 ☐
点击「待办」按钮在正文末尾插入或去除 `☐ `。

#### Scenario: 首次点击
- **WHEN** 用户正文为空时点击「待办」
- **THEN** 正文变为 `☐ `

#### Scenario: 末尾已有 ☐
- **WHEN** 用户正文末尾已经是 `☐ `
- **THEN** 再次点击「待办」会去掉末尾的 `☐ `

#### Scenario: 中间行
- **WHEN** 用户正文中已有内容但末尾不是 `☐ `
- **THEN** 点击会在末尾换行后追加 `☐ `

### Requirement: REQ-06 拍照调起摄像头
点击「图片 → 拍照」必须真实打开系统相机。

#### Scenario: 授权
- **WHEN** 用户首次点击拍照
- **THEN** 系统弹窗申请 `CAMERA` 权限
- **AND** 拒绝时给 Toast 提示且不打开相机

#### Scenario: 已授权
- **WHEN** 用户已授权 CAMERA
- **THEN** 系统相机启动
- **AND** 拍照后图片以 FileProvider URI 写入 app 私有目录
- **AND** 写入成功后图片插入到笔记图片列表并显示在正文顶部

#### Scenario: 旧入口下线
- **WHEN** 用户打开图片面板
- **THEN** 只看到「图片或视频」「拍照」两个入口
- **AND** 不再出现「文档扫码」入口

### Requirement: REQ-07 更多面板 3 入口
点击「⊕」（更多）按钮弹出涂鸦 / 表格 / 音频 3 个入口。

#### Scenario: 入口可见
- **WHEN** 用户点击更多
- **THEN** 弹出 3 个等距按钮
- **AND** 不再显示 AI / 翻译 / 总结 等其他入口

### Requirement: REQ-08 涂鸦白板
点击「涂鸦」按钮弹出全屏白板 Dialog。

#### Scenario: 画画
- **WHEN** 用户手指在白板上拖动
- **THEN** 拖动轨迹实时绘制为当前颜色和粗细的线条
- **AND** 手指抬起时该条线被记录到笔画栈

#### Scenario: 切换颜色 / 粗细
- **WHEN** 用户点击 5 种颜色或 4 档粗细
- **THEN** 后续绘制使用新颜色或粗细
- **AND** 选中的颜色 / 粗细有视觉高亮

#### Scenario: 撤销 / 重做 / 清空
- **WHEN** 用户点击标题栏的撤销 / 重做 / 清空按钮
- **THEN** 撤销回到上一步、重做恢复、 清空回到空白白板

#### Scenario: 保存
- **WHEN** 用户点击「保存」
- **THEN** 当前白板被渲染为 PNG
- **AND** PNG 写入 app 私有 `images/` 目录
- **AND** PNG 的 FileProvider URI 插入到笔记 imageUris
- **AND** 缩略图出现在正文上方

### Requirement: REQ-09 表格插入
点击「表格」按钮弹出「行数 × 列数」输入框。

#### Scenario: 校验输入
- **WHEN** 用户输入行数不在 1..20 或列数不在 1..10
- **THEN** 确定按钮置灰
- **AND** 输入框报错

#### Scenario: 确认
- **WHEN** 用户输入 3 行 3 列后点击「确定」
- **THEN** 笔记正文末尾追加 3×3 等宽对齐 markdown 表格
- **AND** 触发撤销栈记录

### Requirement: REQ-10 音频读取
点击「音频」按钮调用系统文件选择器。

#### Scenario: 选取成功
- **WHEN** 用户在文件选择器中选中一个音频文件
- **THEN** app 取得 URI 持久读权限
- **AND** 笔记正文末尾追加 `🎵 [音频](file://...)` 标记
- **AND** 音频条目以可点击 Row 形式显示在正文上方

### Requirement: REQ-11 撤销 / 重做按钮生效
TopAppBar 右上角的撤销 / 重做按钮真实生效。

#### Scenario: 撤销
- **WHEN** 用户在标题或正文中输入过文字
- **THEN** 撤销按钮变为可点击
- **AND** 点击后标题 / 正文回到上一次快照

#### Scenario: 不可撤销
- **WHEN** 还没有任何输入
- **THEN** 撤销按钮置灰不可点击

#### Scenario: 重做
- **WHEN** 用户已经执行过撤销
- **THEN** 重做按钮变为可点击
- **AND** 点击后恢复被撤销的内容

### Requirement: REQ-12 元信息行简化
笔记元信息行不再有置顶和提醒小图标。

#### Scenario: 打开编辑页
- **WHEN** 用户打开任意笔记
- **THEN** 元信息行只显示「日期 | 字数 | 分类 ▾」
- **AND** 不再出现置顶和提醒两个小图标

### Requirement: REQ-13 主界面右滑弹出 5 动作
在主界面 (笔记列表) 右滑任意笔记卡片露出 5 个动作。

#### Scenario: 右滑卡片
- **WHEN** 用户在列表上从右向左滑动一条笔记卡片
- **THEN** 卡片右侧露出彩色动作背景
- **AND** 滑动超过 35% 阈值后弹出「置顶 / 标签 / 删除 / 移动 / 重要度」5 项菜单
- **AND** 卡片回弹到原位 (不真正删除)

#### Scenario: 菜单项
- **WHEN** 动作菜单出现
- **THEN** 5 个菜单项依次为：置顶 / 标签 / 删除 (红色) / 移动到分类 / 重要度
- **AND** 点击「关闭」或外部空白可关闭菜单

### Requirement: REQ-14 5 个动作功能完整
每个动作都要真正改变笔记状态并落库。

#### Scenario: 置顶
- **WHEN** 用户点击「置顶」
- **THEN** 笔记 `isPinned` 切换
- **AND** 数据库 `setPinned` 被调用
- **AND** 列表自动按 isPinned 排序

#### Scenario: 标签
- **WHEN** 用户点击「标签」
- **THEN** 弹出输入框, 初始值为当前 tags
- **AND** 多个标签用英文逗号分隔
- **AND** 确认后调用 `setTags`

#### Scenario: 删除
- **WHEN** 用户点击「删除」
- **THEN** 弹出确认框
- **AND** 确认后从数据库删除该笔记
- **AND** 列表中该条消失

#### Scenario: 移动到分类
- **WHEN** 用户点击「移动到分类」
- **THEN** 弹出分类列表 (含「未分类」)
- **AND** 当前分类标注「当前」
- **AND** 确认后调用 `moveToCategory`

#### Scenario: 重要度
- **WHEN** 用户点击「重要度」
- **THEN** 弹出 3 档选项：普通 (0) / 重要 (1) / 紧急 (2)
- **AND** 当前重要度标注「当前」
- **AND** 确认后调用 `setPriority`

## MODIFIED Requirements

### Requirement: REQ-MOD-01 Room schema 升级
NoteEntity 增加 `priority` 字段。

#### Scenario: 旧库迁移
- **WHEN** 用户从 v3 升级到 v4
- **THEN** 走 `fallbackToDestructiveMigration()` 直接清表重建
- **AND** 新建笔记时 priority 默认 0 (普通)

### Requirement: REQ-MOD-02 AndroidManifest 权限
增加 5 个运行时权限。

#### Scenario: 权限声明
- **WHEN** 编译 APK
- **THEN** AndroidManifest 中包含 CAMERA / RECORD_AUDIO / READ_MEDIA_IMAGES / READ_MEDIA_VIDEO / READ_MEDIA_AUDIO
- **AND** READ_EXTERNAL_STORAGE 只在 minSdk<=32 时声明

## REMOVED Requirements

### Requirement: REQ-REM-01 旧 AI 工具栏
**Reason**: AI 入口已下线, 整个 BottomTool.AI 分支及对应枚举值一并移除
**Migration**: 旧版 APK 升级后底部工具栏自动从 7 图标变为 6 图标
