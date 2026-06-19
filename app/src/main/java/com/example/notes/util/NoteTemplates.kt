package com.example.notes.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 进阶功能: 笔记模板。
 *
 * 新建笔记时, 用户可从预设模板中选一个快速填充内容。
 * 模板使用占位符 {{date}} / {{time}} / {{weekday}} / {{year}} 等,
 * 插入时实时替换为当前时间。
 */
object NoteTemplates {

    /** 模板类型枚举 (0=无, 1=日记, 2=会议, 3=读书, 4=周报) */
    val all: List<NoteTemplate> = listOf(
        NoteTemplate(
            type = 1,
            name = "日记",
            title = "{{date}} 日记",
            content = """## {{date}} {{weekday}}

### 今日心情
- 

### 今日要事
1. 
2. 
3. 

### 今日收获
- 

### 明日计划
- 

### 反思
- 
"""
        ),
        NoteTemplate(
            type = 2,
            name = "会议记录",
            title = "会议: {{date}}",
            content = """## 会议信息
- **时间**: {{date}} {{time}} ({{weekday}})
- **地点**: 
- **主持**: 
- **出席**: 

## 议题
1. 
2. 
3. 

## 讨论要点
- 

## 决议
- 

## 待办事项
- [ ] 
- [ ] 

## 下次会议
- 
"""
        ),
        NoteTemplate(
            type = 3,
            name = "读书笔记",
            title = "读书笔记: 《》",
            content = """## 基本信息
- **书名**: 《》
- **作者**: 
- **读完时间**: {{date}}

## 一句话总结
- 

## 核心观点
1. 
2. 
3. 

## 启发与思考
- 

## 行动清单
- [ ] 

## 引用摘抄
> 
"""
        ),
        NoteTemplate(
            type = 4,
            name = "周报",
            title = "{{year}}年第{{weekOfYear}}周 周报",
            content = """## 本周概览
- **周期**: {{date}} - 
- **状态**: 

## 本周完成
1. 
2. 
3. 

## 进行中
- 

## 下周计划
1. 
2. 
3. 

## 数据指标
- 

## 反思与改进
- 
"""
        )
    )

    /** 按类型查找模板 */
    fun get(type: Int): NoteTemplate? = all.firstOrNull { it.type == type }

    /** 渲染模板 (替换占位符) */
    fun render(template: String, time: Long = System.currentTimeMillis()): String {
        val cal = Calendar.getInstance().apply { timeInMillis = time }
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val date = dateFormat.format(Date(time))
        val timeStr = timeFormat.format(Date(time))
        val weekday = when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> "周一"
            Calendar.TUESDAY -> "周二"
            Calendar.WEDNESDAY -> "周三"
            Calendar.THURSDAY -> "周四"
            Calendar.FRIDAY -> "周五"
            Calendar.SATURDAY -> "周六"
            Calendar.SUNDAY -> "周日"
            else -> ""
        }
        val year = cal.get(Calendar.YEAR)
        val weekOfYear = cal.get(Calendar.WEEK_OF_YEAR)
        return template
            .replace("{{date}}", date)
            .replace("{{time}}", timeStr)
            .replace("{{weekday}}", weekday)
            .replace("{{year}}", year.toString())
            .replace("{{weekOfYear}}", weekOfYear.toString())
    }
}

/** 笔记模板数据 */
data class NoteTemplate(
    val type: Int,
    val name: String,
    val title: String,
    val content: String
)
