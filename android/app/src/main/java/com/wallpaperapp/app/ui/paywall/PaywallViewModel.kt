package com.wallpaperapp.app.ui.paywall

import android.app.Activity
import com.wallpaperapp.app.R
import com.wallpaperapp.app.ui.common.UiText
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wallpaperapp.app.billing.Plan
import com.wallpaperapp.app.billing.PlanType
import com.wallpaperapp.app.billing.PremiumManager
import com.wallpaperapp.app.billing.PurchaseResult
import com.wallpaperapp.app.data.repository.AppConfigRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

data class PaywallUiState(
    val loading: Boolean = true,
    val plans: List<Plan> = emptyList(),
    val selected: PlanType? = null,
    val highlighted: PlanType = PlanType.ANNUAL,
    /** Dashboard headline (app_config.paywall); null = the built-in one. */
    val headline: String? = null,
    val busy: Boolean = false,
    val isPremium: Boolean = false,
    val message: UiText? = null,
    /** Set after a successful purchase or restore, so the screen can close. */
    val done: Boolean = false,
)

@HiltViewModel
class PaywallViewModel @Inject constructor(
    private val premiumManager: PremiumManager,
    appConfig: AppConfigRepository,
) : ViewModel() {

    private val content = MutableStateFlow(PaywallUiState())

    val uiState: StateFlow<PaywallUiState> = combine(content, appConfig.config, premiumManager.isPremium) { state, config, premium ->
        val highlighted = if (config.paywall.highlight == "monthly") PlanType.MONTHLY else PlanType.ANNUAL
        state.copy(
            highlighted = highlighted,
            selected = state.selected ?: highlighted,
            headline = config.paywall.headline.ifBlank { null },
            isPremium = premium,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PaywallUiState())

    init {
        loadPlans()
    }

    fun loadPlans() {
        content.update { it.copy(loading = true, message = null) }
        viewModelScope.launch {
            val plans = safely { premiumManager.getPlans() }
            content.update {
                it.copy(loading = false, plans = plans.orEmpty(), message = if (plans == null) UiText.Res(R.string.plans_failed) else null)
            }
        }
    }

    fun select(plan: PlanType) = content.update { it.copy(selected = plan) }

    fun purchase(activity: Activity) {
        val plan = uiState.value.selected ?: return
        if (content.value.busy) return
        content.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            val result = safely { premiumManager.purchase(activity, plan) }
            content.update {
                when (result) {
                    null -> it.copy(busy = false, message = UiText.Res(R.string.purchase_failed_generic))
                    PurchaseResult.Success -> it.copy(busy = false, done = true)
                    PurchaseResult.Cancelled -> it.copy(busy = false)
                    is PurchaseResult.Error -> it.copy(busy = false, message = UiText.Res(R.string.purchase_failed, listOf(result.msg)))
                }
            }
        }
    }

    fun restore() {
        if (content.value.busy) return
        content.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            val restored = safely { premiumManager.restore() } == true
            content.update {
                if (restored) it.copy(busy = false, done = true)
                else it.copy(busy = false, message = UiText.Res(R.string.restore_none))
            }
        }
    }

    fun manageSubscriptionIntent(): Intent = premiumManager.manageSubscriptionIntent()

    fun messageShown() = content.update { it.copy(message = null) }

    private suspend fun <T> safely(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }
}
