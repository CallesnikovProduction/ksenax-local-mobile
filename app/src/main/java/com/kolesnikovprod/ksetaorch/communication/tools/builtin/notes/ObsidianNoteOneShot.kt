package com.kolesnikovprod.ksetaorch.communication.tools.builtin.notes

import com.kolesnikovprod.ksetaorch.communication.work.oneshot.KsenaxOneShotDeclaration

/**
 * Маленькие FG declarations для Obsidian-compatible заметок.
 *
 * Контент генерирует G4 planner, а FunctionGemma только компилирует один
 * безопасный function-call под Android/file executor.
 *
 * @since 0.2
 * @author Stephan Kolesnikov
 */
interface ObsidianNoteOneShot : KsenaxOneShotDeclaration {

    object Write : ObsidianNoteOneShot {
        override val codeName: String = "obsidian_note_write"
        override val description: String =
            "Selects the action for creating or appending an Obsidian-compatible Markdown note. Arguments are provided by the planner."
        override val parameters: String? = null
    }

    object AppendAnalysis : ObsidianNoteOneShot {
        override val codeName: String = "obsidian_note_append_analysis"
        override val description: String =
            "Selects the action for appending generated analysis to an Obsidian-compatible Markdown note. Arguments are provided by the planner."
        override val parameters: String? = null
    }
}
