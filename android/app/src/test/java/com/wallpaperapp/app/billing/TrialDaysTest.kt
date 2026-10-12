package com.wallpaperapp.app.billing

import com.google.common.truth.Truth.assertThat
import com.revenuecat.purchases.models.Period
import org.junit.Test

class TrialDaysTest {
    @Test fun `trial periods convert to days`() {
        assertThat(Period(7, Period.Unit.DAY, "P7D").toDays()).isEqualTo(7)
        assertThat(Period(1, Period.Unit.WEEK, "P1W").toDays()).isEqualTo(7)
        assertThat(Period(1, Period.Unit.MONTH, "P1M").toDays()).isEqualTo(30)
    }
}
