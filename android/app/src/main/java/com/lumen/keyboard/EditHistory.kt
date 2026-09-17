package com.lumen.keyboard

data class LumenEdit(val inserted: String, val removed: String = "")

class EditHistory(private val capacity: Int = 5) {
    private val undo = ArrayDeque<LumenEdit>()
    private val redo = ArrayDeque<LumenEdit>()

    fun record(edit: LumenEdit) {
        undo.addLast(edit)
        while (undo.size > capacity) undo.removeFirst()
        redo.clear()
    }

    fun takeUndo(): LumenEdit? = undo.removeLastOrNull()?.also {
        redo.addLast(it)
        while (redo.size > capacity) redo.removeFirst()
    }

    fun takeRedo(): LumenEdit? = redo.removeLastOrNull()?.also {
        undo.addLast(it)
        while (undo.size > capacity) undo.removeFirst()
    }
}

