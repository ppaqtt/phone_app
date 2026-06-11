package com.example.notes.ui.screens

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf

/**
 * 简单的快照式撤销/重做控制器。
 *
 * 用法:
 *   val undoRedo = remember { UndoRedoState() }
 *   undoRedo.record(currentSnapshot)  // 在变更后调用
 *   undoRedo.undo()?.let { apply(it) } // 按下撤销按钮时
 *
 * 栈深度上限 [maxDepth] 默认 50, 避免内存爆掉。
 */
@Stable
class UndoRedoState<T>(private val maxDepth: Int = 50) {
    private val undoStack = mutableStateListOf<T>()
    private val redoStack = mutableStateListOf<T>()

    /** 当前是否能撤销 / 重做 (只读) */
    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    /**
     * 记录一个新快照。**如果新快照和当前栈顶相同, 跳过** (避免连续输入被分成多步)。
     * 记录成功会清空 redo 栈 (线性历史, 不能跨分支撤销)。
     */
    fun record(snapshot: T) {
        if (undoStack.isNotEmpty() && undoStack.last() == snapshot) return
        undoStack.add(snapshot)
        if (undoStack.size > maxDepth) undoStack.removeAt(0)
        redoStack.clear()
    }

    /**
     * 弹出上一个快照, 同时把当前快照推入 redo 栈。
     * 如果栈为空返回 null。
     */
    fun undo(current: T): T? {
        if (undoStack.isEmpty()) return null
        redoStack.add(current)
        return undoStack.removeAt(undoStack.lastIndex)
    }

    /**
     * 重做: 取出 redo 栈顶, 推入 undo 栈, 返回新值。
     */
    fun redo(current: T): T? {
        if (redoStack.isEmpty()) return null
        undoStack.add(current)
        return redoStack.removeAt(redoStack.lastIndex)
    }

    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }
}

/** 笔记的快照 — 包含所有可被撤销/重做的字段 */
data class NoteSnapshot(
    val title: String,
    val content: String,
    val color: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified,
    val isPinned: Boolean = false,
    val categoryId: Long? = null,
    val tags: List<String> = emptyList(),
    val reminderTime: Long? = null
)
