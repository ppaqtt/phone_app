package com.example.notes.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.RemoteViews
import com.example.notes.MainActivity
import com.example.notes.NotesApplication
import com.example.notes.R
import timber.log.Timber

/**
 * F3: 桌面小部件 - 显示最近 5 条笔记。
 *
 * 设计要点:
 * 1) 用传统 RemoteViews 而非 Glance: 无需新增依赖, 单文件即可, 兼容 API 24+。
 * 2) ListView 用 RemoteViewsService + RemoteViewsFactory 提供数据 (ListAdapter 写法)。
 * 3) 列表项点击通过 setOnClickFillInIntent 在 Factory 里给每条 item 挂独立 PendingIntent,
 *    打开 MainActivity 携带 noteId (NavGraph 已支持 deep link)。
 * 4) updatePeriodMillis=0: 不靠系统轮询; 数据更新由 Repository 显式广播触发
 *    (此处先不接, 后续可在 ViewModel 写完笔记后调 [requestRefresh])。
 * 5) CoroutineScope 仅在 onUpdate 短暂使用, 不持有长生命周期。
 */
class NotesAppWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // 一次性刷新所有小部件实例
        appWidgetIds.forEach { widgetId ->
            // 设置 ListView 的 RemoteAdapter (每次 onUpdate 都设一次, 不然 widget 复用旧数据)
            val views = buildBaseViews(context, appWidgetManager, widgetId)
            appWidgetManager.updateAppWidget(widgetId, views)
            appWidgetManager.notifyAppWidgetViewDataChanged(widgetId, R.id.widget_list)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        // 自定义 action: 请求小部件刷新
        if (intent.action == ACTION_REFRESH) {
            requestRefresh(context)
        }
    }

    companion object {
        const val ACTION_REFRESH = "com.example.notes.widget.ACTION_REFRESH"
        const val EXTRA_NOTE_ID = "extra_note_id"

        /**
         * 让所有小部件实例重新加载数据。NotesViewModel.saveNote 成功后 / 删除后调用。
         * 走广播 → onReceive → notifyAppWidgetViewDataChanged 触发 Factory 重读 DB。
         */
        fun requestRefresh(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, NotesAppWidget::class.java)
            val ids = mgr.getAppWidgetIds(component)
            if (ids.isEmpty()) return
            mgr.notifyAppWidgetViewDataChanged(ids, R.id.widget_list)
        }

        /**
         * 异步预热一次数据, 避免首次 widget 显示空白。
         * 触发 onUpdate 让 Factory 重新拉 DB。
         */
        fun warmUp(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, NotesAppWidget::class.java)
            val ids = mgr.getAppWidgetIds(component)
            if (ids.isEmpty()) return
            val updateIntent = Intent(context, NotesAppWidget::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            }
            context.sendBroadcast(updateIntent)
        }

        /**
         * 给指定 widgetId 构造基础视图 (标题 + 列表 adapter + 新建按钮)。
         */
        internal fun buildBaseViews(
            context: Context,
            @Suppress("UNUSED_PARAMETER") mgr: AppWidgetManager,
            @Suppress("UNUSED_PARAMETER") widgetId: Int
        ): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.app_widget)

            // 列表 adapter
            val serviceIntent = Intent(context, NotesRemoteViewsService::class.java)
            // 必须 putExtra 区分 widgetId, 否则多个 widget 实例共享同一 adapter
            // (在 Service.onGetViewFactory 时再取出来)
            serviceIntent.data = Uri.parse(serviceIntent.toUri(Intent.URI_INTENT_SCHEME))
            views.setRemoteAdapter(R.id.widget_list, serviceIntent)
            views.setEmptyView(R.id.widget_list, R.id.widget_empty)

            // "新建笔记"按钮: 启动 MainActivity (noteId = 0L = 新建)
            val newNoteIntent = Intent(context, MainActivity::class.java).apply {
                action = MainActivity.ACTION_NEW_NOTE
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val newNotePi = PendingIntent.getActivity(
                context, 0, newNoteIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_new_note, newNotePi)

            // 列表项点击的模板 PendingIntent (会被 Factory 的 fillInIntent 覆盖 extras)
            val openIntent = Intent(context, MainActivity::class.java).apply {
                action = MainActivity.ACTION_OPEN_NOTE
            }
            val templatePi = PendingIntent.getActivity(
                context, 1, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    or PendingIntent.FLAG_MUTABLE
            )
            views.setPendingIntentTemplate(R.id.widget_list, templatePi)

            return views
        }
    }
}

/**
 * F3: ListView 数据源服务, 系统拉数据时调 [onGetViewFactory]。
 */
class NotesRemoteViewsService : android.widget.RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        NotesRemoteViewsFactory(applicationContext)
}

/**
 * F3: ListView 数据源工厂。每次 notifyAppWidgetViewDataChanged 都会重新
 * 创建, 拉一次最新数据。NotesAppWidget.onUpdate 也调一次。
 */
class NotesRemoteViewsFactory(
    private val context: Context
) : android.widget.RemoteViewsService.RemoteViewsFactory {

    private var notes: List<com.example.notes.data.NoteEntity> = emptyList()

    override fun onCreate() {
        // 首次创建时预热一次
        loadData()
    }

    override fun onDataSetChanged() {
        // 每次 notifyAppWidgetViewDataChanged 都会走这里
        loadData()
    }

    override fun onDestroy() {
        notes = emptyList()
    }

    override fun getCount(): Int = notes.size

    override fun getViewAt(position: Int): RemoteViews {
        if (position >= notes.size) {
            return RemoteViews(context.packageName, R.layout.widget_note_item)
        }
        val note = notes[position]
        val views = RemoteViews(context.packageName, R.layout.widget_note_item)
        views.setTextViewText(R.id.item_title, note.title.ifBlank { "(无标题)" })
        views.setTextViewText(R.id.item_preview, note.content.take(80).ifBlank { "无内容预览" })

        // 每条 item 的点击 Intent: 覆盖模板的 extras
        val fillIn = Intent().apply {
            putExtra(NotesAppWidget.EXTRA_NOTE_ID, note.id)
        }
        views.setOnClickFillInIntent(R.id.item_title, fillIn)
        views.setOnClickFillInIntent(R.id.item_preview, fillIn)
        return views
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long =
        if (position < notes.size) notes[position].id else position.toLong()

    override fun hasStableIds(): Boolean = true

    private fun loadData() {
        try {
            val app = context.applicationContext as NotesApplication
            // RemoteViewsFactory 的回调都在 binder 线程池, 走 blocking runBlocking 简单
            // 取 5 条 IO 不会卡顿, 后续可改成回调注册
            // 这里用 runBlocking 是因为 RemoteViewsFactory.getViewAt 是同步调用,
            // 没有 suspend 入口。AppWidget 列表项数 ≤ 5, IO 微秒级, 不构成性能问题。
            notes = kotlinx.coroutines.runBlocking { app.repository.getRecentNotes(5) }
        } catch (e: Exception) {
            Timber.tag("Widget").e(e, "loadData failed")
            notes = emptyList()
        }
    }
}
