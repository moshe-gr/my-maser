package com.example.mymaser.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class IncomeRateTest {

    @Test
    fun `maser users can count one income as chomesh`() {
        assertEquals(listOf(10f, 20f), incomeRateOptions(10f))
    }

    @Test
    fun `chomesh users can count one income as maser`() {
        assertEquals(listOf(10f, 20f), incomeRateOptions(20f))
    }

    @Test
    fun `a custom rate is offered next to chomesh`() {
        assertEquals(listOf(15f, 20f), incomeRateOptions(15f))
        assertEquals(listOf(20f, 25f), incomeRateOptions(25f))
    }
}
