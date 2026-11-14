package com.example.notes.util

/**
 * 内置笔记模板。
 *
 * 模板是纯内容, 不依赖数据库结构; 首页"模板库"和编辑器"图文模版"面板
 * 共用这一份数据, 避免两处内容不一致。
 */
data class NoteTemplate(
    val id: String,
    val emoji: String,
    val name: String,
    val summary: String,
    val title: String,
    val content: String,
    val tags: String = ""
)

object TemplateLibrary {

    val all: List<NoteTemplate> = listOf(
        NoteTemplate(
            id = "daily",
            emoji = "📖",
            name = "日记",
            summary = "记录今天发生的事、情绪与收获",
            title = "日记 · " + today(),
            tags = "日记",
            content = """
                # ${today()} 日记

                ## 今日三件事
                1. 
                2. 
                3. 

                ## 心情与状态
                - 心情：
                - 精力：/10
                - 睡眠：小时

                ## 值得记住的瞬间
                

                ## 今日收获
                - 

                ## 明天最重要的一件事
                - [ ] 
            """.trimIndent()
        ),
        NoteTemplate(
            id = "meeting",
            emoji = "🗣️",
            name = "会议纪要",
            summary = "议题、结论、待办与负责人",
            title = "会议纪要 · " + today(),
            tags = "会议",
            content = """
                # 会议纪要

                - 时间：
                - 地点 / 形式：
                - 主持人：
                - 参会人：

                ## 议题
                1. 
                2. 

                ## 讨论要点
                - 

                ## 结论
                - 

                ## 待办（负责人 / 截止时间）
                - [ ]  @负责人  YYYY-MM-DD
                - [ ]  @负责人  YYYY-MM-DD

                ## 下次会议
                - 时间：
                - 议题：
            """.trimIndent()
        ),
        NoteTemplate(
            id = "reading",
            emoji = "📚",
            name = "读书笔记",
            summary = "书籍信息、核心观点与行动清单",
            title = "读书笔记 · 书名",
            tags = "读书",
            content = """
                # 读书笔记

                - 书名：
                - 作者：
                - 阅读区间：
                - 推荐指数：★★★★☆

                ## 一句话总结
                

                ## 核心观点
                1. 
                2. 
                3. 

                ## 金句摘录
                > 

                ## 我的思考
                

                ## 行动清单
                - [ ] 
            """.trimIndent()
        ),
        NoteTemplate(
            id = "weekly",
            emoji = "📅",
            name = "周报",
            summary = "本周成果、问题与下周计划",
            title = "周报 · " + thisWeek(),
            tags = "周报",
            content = """
                # 周报 · ${thisWeek()}

                ## 本周完成
                - 
                - 

                ## 数据 / 结果
                - 

                ## 遇到的问题
                - 问题：
                - 影响：
                - 处理方式：

                ## 风险与需要支持
                - 

                ## 下周计划
                1. 
                2. 
                3. 
            """.trimIndent()
        ),
        NoteTemplate(
            id = "cornell",
            emoji = "🧠",
            name = "康奈尔笔记",
            summary = "线索栏 + 笔记栏 + 总结栏",
            title = "康奈尔笔记 · 主题",
            tags = "学习",
            content = """
                # 康奈尔笔记 · 主题

                | 线索 / 关键词 | 笔记内容 |
                | --- | --- |
                | 问题 1 |  |
                | 问题 2 |  |
                | 问题 3 |  |

                ## 详细笔记
                - 

                ## 总结（用自己的话）
                

                ## 复习提示
                - 24 小时内回顾一次
                - 一周后再回顾一次
            """.trimIndent()
        ),
        NoteTemplate(
            id = "gtd",
            emoji = "✅",
            name = "GTD 周复盘",
            summary = "清空收件箱、回顾项目、确定下一步",
            title = "GTD 周复盘 · " + thisWeek(),
            tags = "GTD,复盘",
            content = """
                # GTD 周复盘 · ${thisWeek()}

                ## 1. 收集
                - 清空收件箱：
                - 未处理笔记：
                - 未回复消息：

                ## 2. 理清
                - 每件事的下一步行动：
                - 等待他人：
                - 日历事项：

                ## 3. 整理
                - 项目清单是否完整：
                -  someday / maybe：

                ## 4. 回顾
                - 本周做得好的：
                - 本周卡住的：
                - 需要停止的：

                ## 5. 行动
                - [ ] 下周最重要的 3 件事
                - [ ] 
                - [ ] 
            """.trimIndent()
        ),
        NoteTemplate(
            id = "morning",
            emoji = "🌅",
            name = "晨间日记",
            summary = "唤醒、意图与今日优先事项",
            title = "晨间日记 · " + today(),
            tags = "晨间日记",
            content = """
                # 晨间日记 · ${today()}

                ## 醒来后的第一感受
                

                ## 今天我想成为什么样的人
                - 

                ## 今日三件最重要的事
                1. 
                2. 
                3. 

                ## 今日小目标
                - 运动：
                - 阅读：
                - 专注时长：小时

                ## 感恩
                - 
            """.trimIndent()
        ),
        NoteTemplate(
            id = "ninegrid",
            emoji = "🔲",
            name = "九宫格复盘",
            summary = "从八个维度快速扫描现状",
            title = "九宫格复盘 · " + thisWeek(),
            tags = "复盘",
            content = """
                # 九宫格复盘 · ${thisWeek()}

                | 工作 / 学业 | 健康 | 家庭 |
                | --- | --- | --- |
                |  |  |  |
                | **财富** | **核心主题** | **社交** |
                |  | 本周最重要的事： |  |
                | **成长** | **休闲** | **环境** |
                |  |  |  |

                ## 评分（1-10）
                - 工作：  健康：  家庭：
                - 财富：  社交：  成长：
                - 休闲：  环境：

                ## 下周改进
                - 
            """.trimIndent()
        ),
        NoteTemplate(
            id = "bookcard",
            emoji = "🃏",
            name = "读书卡片",
            summary = "把书中的概念做成可复习闪卡",
            title = "读书卡片 · 书名",
            tags = "读书,闪卡",
            content = """
                # 读书卡片 · 书名

                用 `#card 正面 :: 背面` 写卡片，保存后会自动进入闪卡复习。
                也可以用 `==高亮==` 标记重点，同样会生成卡片。

                #card 这本书的核心论点是什么 :: 
                #card 作者给出的关键证据 :: 
                #card 我打算如何应用 :: 

                ## 摘录
                - ==在这里写下值得记住的句子==
            """.trimIndent()
        ),
        NoteTemplate(
            id = "interview",
            emoji = "💼",
            name = "面试记录",
            summary = "岗位信息、问题复盘与后续跟进",
            title = "面试记录 · 公司 / 岗位",
            tags = "面试",
            content = """
                # 面试记录

                - 公司：
                - 岗位：
                - 轮次：
                - 面试官：
                - 时间：

                ## 岗位信息
                - 职责：
                - 团队：
                - 薪资范围：

                ## 被问到的问题
                1. 
                2. 
                3. 

                ## 我的回答复盘
                - 答得好的：
                - 需要改进的：

                ## 反问环节
                - 

                ## 后续跟进
                - [ ] 发送感谢邮件
                - [ ] 补充作品集
                - [ ] 约定下一轮时间
            """.trimIndent()
        )
    )

    fun byId(id: String): NoteTemplate? = all.firstOrNull { it.id == id }

    private fun today(): String {
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        return fmt.format(java.util.Date())
    }

    private fun thisWeek(): String {
        val cal = java.util.Calendar.getInstance()
        val fmt = java.text.SimpleDateFormat("yyyy-'W'ww", java.util.Locale.getDefault())
        return fmt.format(cal.time)
    }
}
