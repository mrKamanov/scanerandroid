package com.tscan.scanertestov.feature.blankeditor

/**
 * Описание: пакетная подготовка бланков по журналу (вариант и ФИО на листе).
 */
import androidx.compose.ui.geometry.Offset
import com.tscan.scanertestov.feature.journals.JournalStudent
import java.util.Locale

internal enum class BlankJournalVariantExportMode {
    None,
    Single,
    Cycle,
}

internal fun blankEditorNeedsJournalBatchDialog(
    variantMarkerEnabled: Boolean,
    variantPrintedMode: Boolean,
    studentNameMarkerEnabled: Boolean,
    studentNamePrintedMode: Boolean,
): Boolean {
    return (variantMarkerEnabled && variantPrintedMode) ||
        (studentNameMarkerEnabled && studentNamePrintedMode)
}

internal fun journalDistinctClasses(students: List<JournalStudent>): List<String> =
    students.map { it.className.trim() }
        .distinct()
        .filter { it.isNotBlank() }
        .sortedWith(compareBy { it.lowercase(Locale("ru")) })

internal fun studentsInClass(students: List<JournalStudent>, className: String): List<JournalStudent> =
    students.filter { it.className.trim() == className.trim() }
        .sortedWith(
            compareBy(
                { it.surname.lowercase(Locale("ru")) },
                { it.name.lowercase(Locale("ru")) },
            ),
        )

internal fun buildJournalExportSnapshot(
    layoutConfig: BlankEditorLayoutConfig,
    renderParams: BlankBubbleRenderParams,
    hiddenBubbleIds: Set<String>,
    blankPanLogical: Offset,
    blankScale: Float,
    textBlocks: List<BlankTextBlock>,
    variantMarkerEnabled: Boolean,
    variantPrintedMode: Boolean,
    variantNumberText: String,
    studentNameMarkerEnabled: Boolean,
    studentNamePrintedMode: Boolean,
    studentSurnameFallback: String,
    studentNameFallback: String,
    student: JournalStudent?,
    studentIndex: Int,
    variantMode: BlankJournalVariantExportMode,
    singleVariantText: String,
    cycleVariantMax: Int,
    /** Шапка бланка v2 (без данных ученика) или null — старый формат. */
    v2Base: BlankV2Options? = null,
): BlankSheetExportSnapshot {
    val (effVariantPrinted, effVariantNum) = when {
        !variantMarkerEnabled -> false to ""
        !variantPrintedMode -> false to ""
        student == null -> true to variantNumberText.ifBlank { "1" }
        variantMode == BlankJournalVariantExportMode.None -> false to ""
        variantMode == BlankJournalVariantExportMode.Single -> {
            val n = singleVariantText.toIntOrNull()?.coerceIn(1, 4) ?: 1
            true to n.toString()
        }
        variantMode == BlankJournalVariantExportMode.Cycle -> {
            val k = cycleVariantMax.coerceIn(2, 4)
            true to (((studentIndex % k) + 1).toString())
        }
        else -> false to ""
    }

    val (sur, nam, namePrintedEff) = when {
        !studentNameMarkerEnabled -> Triple(studentSurnameFallback, studentNameFallback, studentNamePrintedMode)
        student == null -> Triple(studentSurnameFallback, studentNameFallback, studentNamePrintedMode)
        !studentNamePrintedMode -> Triple(studentSurnameFallback, studentNameFallback, studentNamePrintedMode)
        else -> {
            val right = student.name.trim()
            Triple(student.surname.trim(), right.trim(), true)
        }
    }

    return BlankSheetExportSnapshot(
        layoutConfig = layoutConfig,
        renderParams = renderParams,
        hiddenBubbleIds = hiddenBubbleIds,
        blankPanLogical = blankPanLogical,
        blankScale = blankScale,
        textBlocks = textBlocks,
        showVariantMarker = variantMarkerEnabled,
        showStudentNameMarker = studentNameMarkerEnabled,
        studentNamePrintedMode = namePrintedEff,
        studentSurnameText = sur,
        studentNameText = nam,
        variantPrintedMode = effVariantPrinted,
        variantPrintedNumber = effVariantNum.ifBlank { "1" },
        v2 = v2Base?.copy(
            printedVariant = if (effVariantPrinted) effVariantNum.toIntOrNull()?.coerceIn(1, 4) else null,
            // Номер ученика по порядку в классе (1–63) — квадратами кода.
            studentCode = if (student != null) (studentIndex + 1).takeIf { it in 1..63 } else null,
            printedSurname = if (studentNameMarkerEnabled && namePrintedEff) sur else null,
            printedName = if (studentNameMarkerEnabled && namePrintedEff) nam else null,
        ),
    )
}

internal fun journalZipEntryBaseName(index: Int, student: JournalStudent): String =
    "${index + 1}-${student.surname}-${student.name}"
