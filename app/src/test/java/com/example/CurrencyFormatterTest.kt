package com.example

import com.example.util.CurrencyFormatter
import org.junit.Assert.assertEquals
import org.junit.Test

class CurrencyFormatterTest {

    @Test
    fun `rupiah formatting uses Indonesian dot grouping`() {
        assertEquals("Rp0", CurrencyFormatter.formatRupiah(0L))
        assertEquals("Rp1.000.000", CurrencyFormatter.formatRupiah(1_000_000L))
        assertEquals("-Rp250.000", CurrencyFormatter.formatRupiah(-250_000L))
        assertEquals("Rp1,5Jt", CurrencyFormatter.formatCompactRupiah(1_500_000L))
    }
}
