package com.zfl.launcher.ui.home

import com.zfl.launcher.data.GridSpec
import com.zfl.launcher.ui.theme.Tokens

/** Fractions of the folder tile, shared by its miniatures and the expanded icons' origins. */
internal data class FolderPreviewCell(val left: Float, val top: Float, val size: Float)

internal fun folderPreviewCell(index: Int, rtl: Boolean = false): FolderPreviewCell {
    val columns = GridSpec.Folder.columns
    require(index in 0 until GridSpec.Folder.cellCount)
    val padding = Tokens.FolderPreviewPadding
    val gap = Tokens.FolderPreviewGap
    val size = (1f - padding * 2 - gap * (columns - 1)) / columns
    val column = if (rtl) columns - 1 - index % columns else index % columns
    return FolderPreviewCell(
        left = padding + (size + gap) * column,
        top = padding + (size + gap) * (index / columns),
        size = size,
    )
}

/** Names and page dots arrive after the miniature icons start spreading out. */
internal fun folderDetailAlpha(progress: Float): Float =
    ((progress - Tokens.FolderDetailRevealStart) / (1f - Tokens.FolderDetailRevealStart)).coerceIn(0f, 1f)
