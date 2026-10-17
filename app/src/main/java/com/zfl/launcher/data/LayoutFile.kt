package com.zfl.launcher.data

import java.io.File

/**
 * The layout file itself: [LayoutData] as JSON ([toJson]), grid and cells together. A save
 * writes a temporary file and renames it over the old one, so after a crash at any point
 * the file holds either the whole old layout or the whole new one, never a mix.
 */
class LayoutFile(private val file: File) {
    private val tmp = File(file.parentFile, "${file.name}.tmp")

    val exists: Boolean get() = file.exists()

    /**
     * The saved layout, or null when there is none or it can't be read. A temporary file
     * left by an interrupted save is ignored: the rename never happened, so it isn't the layout.
     *
     * @param legacyGrid grid of a file saved before the grid was stored in it.
     */
    fun read(legacyGrid: GridSpec): LayoutData? = file.takeIf { it.exists() }?.readText()?.let { layoutFromJson(it, legacyGrid) }

    fun write(layout: LayoutData) {
        tmp.writeText(layout.toJson())
        check(tmp.renameTo(file)) { "Couldn't replace ${file.name}" }
    }
}
