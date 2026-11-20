package com.tscan.scanertestov

import com.tscan.scanertestov.feature.journals.decodeCsvText
import com.tscan.scanertestov.feature.journals.parseStudentsFromBytesInternal
import org.junit.Assert.assertEquals
import org.junit.Test

class JournalCsvImportTest {
    private val csv = "Класс;Фамилия;Имя;Отчество\n7А;Иванов;Пётр;Сергеевич\n7А;Смирнова;Анна;\n"

    @Test
    fun utf8() {
        val list = parseStudentsFromBytesInternal(csv.toByteArray(Charsets.UTF_8))
        assertEquals(2, list.size)
        assertEquals("Иванов", list[0].surname)
    }

    @Test
    fun utf8WithBom() {
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + csv.toByteArray(Charsets.UTF_8)
        assertEquals(csv, decodeCsvText(bytes))
        assertEquals(2, parseStudentsFromBytesInternal(bytes).size)
    }

    @Test
    fun windows1251FromRussianExcel() {
        val list = parseStudentsFromBytesInternal(csv.toByteArray(charset("windows-1251")))
        assertEquals(2, list.size)
        assertEquals("Смирнова", list[1].surname)
        assertEquals("Анна", list[1].name)
    }
}
