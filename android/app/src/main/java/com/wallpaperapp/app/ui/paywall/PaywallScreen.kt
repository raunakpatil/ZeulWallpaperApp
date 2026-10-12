package com.wallpaperapp.app.ui.paywall

import com.wallpaperapp.app.ui.theme.ZeulRadius
import com.wallpaperapp.app.ui.theme.softRaised
import com.wallpaperapp.app.ui.theme.softInset
import com.wallpaperapp.app.ui.theme.softClickable
import com.wallpaperapp.app.ui.theme.rememberSoftPress
import com.wallpaperapp.app.ui.theme.pressScale
import com.wallpaperapp.app.ui.theme.ZeulMotion
import com.wallpaperapp.app.ui.theme.ZeulIcons
import com.wallpaperapp.app.ui.theme.ZeulDisplay
import com.wallpaperapp.app.ui.theme.SoftTextButton
import com.wallpaperapp.app.ui.theme.SoftIconButton
import com.wallpaperapp.app.ui.theme.LocalSoft
import com.wallpaperapp.app.ui.theme.AccentButton
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.animation.animateColorAsState
import androidx.activity.compose.LocalActivity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import com.wallpaperapp.app.ui.common.UiText
import androidx.compose.ui.res.pluralStringResource
import com.wallpaperapp.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wallpaperapp.app.billing.Plan
import com.wallpaperapp.app.billing.PlanType

private val PERKS = listOf(
    R.string.perk_no_ads,
    R.string.perk_instant,
    R.string.perk_originals,
    R.string.perk_exclusive,
    R.string.perk_auto_changer,
    R.string.perk_early_access,
)

/** Paywall: annual highlighted, trial stated clearly, restore + manage available. */
@Composable
fun PaywallScreen(onClose: () -> Unit, viewModel: PaywallViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val activity = LocalActivity.current
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.done) { if (state.done) onClose() }
    val messageText = state.message?.asString()
    LaunchedEffect(messageText) {
        messageText?.let {
            snackbar.showSnackbar(it)
            viewModel.messageShown()
        }
    }

    PaywallContent(
        state = state,
        onClose = onClose,
        onSelect = viewModel::select,
        onPurchase = { activity?.let(viewModel::purchase) },
        onRestore = viewModel::restore,
        onManage = { context.startActivity(viewModel.manageSubscriptionIntent()) },
        onRetry = viewModel::loadPlans,
        snackbar = snackbar,
    )
}

/** Everything the paywall draws, from plain state: what the UI tests drive. */
@Composable
fun PaywallContent(
    state: PaywallUiState,
    onClose: () -> Unit,
    onSelect: (PlanType) -> Unit,
    onPurchase: () -> Unit,
    onRestore: () -> Unit,
    onManage: () -> Unit,
    onRetry: () -> Unit = {},
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    val soft = LocalSoft.current
    Box(Modifier.fillMaxSize().background(soft.base)) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                SoftIconButton(ZeulIcons.Close, stringResource(R.string.close), onClose, size = 46.dp, tint = soft.text)
            }
            // The hamster with a gem badge: Premium, Zeul style.
            Box(Modifier.size(150.dp)) {
                Box(Modifier.size(136.dp).align(Alignment.Center).softRaised(CircleShape, depth = 9.dp), contentAlignment = Alignment.Center) {
                    Image(
                        painterResource(R.drawable.boot_splash_icon),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(128.dp).clip(CircleShape).graphicsLayer { scaleX = 1.35f; scaleY = 1.35f },
                    )
                }
                Box(Modifier.size(46.dp).align(Alignment.BottomEnd).softRaised(CircleShape, depth = 4.dp, fill = soft.accent), contentAlignment = Alignment.Center) {
                    Icon(ZeulIcons.Gem, null, tint = soft.onAccent, modifier = Modifier.size(22.dp))
                }
            }
            Text(
                state.headline ?: stringResource(R.string.paywall_headline),
                fontFamily = ZeulDisplay,
                fontSize = 30.sp,
                lineHeight = 36.sp,
                fontWeight = if (soft.isDark) FontWeight.Normal else FontWeight.Light,
                color = soft.wordmark,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )

            if (state.isPremium) {
                Text(stringResource(R.string.paywall_already_premium), style = MaterialTheme.typography.bodyLarge, color = soft.textMuted, textAlign = TextAlign.Center)
                AccentButton(stringResource(R.string.manage_subscription), onManage, Modifier.fillMaxWidth())
                return@Column
            }

            Column(
                Modifier.fillMaxWidth().softRaised(RoundedCornerShape(ZeulRadius.Panel), depth = 5.dp).padding(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PERKS.forEach { perk ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(26.dp).clip(CircleShape).background(soft.accent.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                            Icon(ZeulIcons.Check, contentDescription = null, tint = soft.accent, modifier = Modifier.size(16.dp))
                        }
                        Text(stringResource(perk), style = MaterialTheme.typography.bodyLarge, color = soft.text, modifier = Modifier.padding(start = 12.dp))
                    }
                }
            }

            when {
                state.loading -> CircularProgressIndicator(color = soft.accent)
                state.plans.isEmpty() -> SoftTextButton(stringResource(R.string.paywall_retry_plans), onRetry)
                else -> state.plans.sortedBy { it.type != state.highlighted }.forEach { plan ->
                    PlanCard(
                        plan = plan,
                        selected = plan.type == state.selected,
                        highlighted = plan.type == state.highlighted,
                        savings = annualSavings(state.plans),
                        onClick = { onSelect(plan.type) },
                    )
                }
            }

            val selectedPlan = state.plans.firstOrNull { it.type == state.selected }
            AccentButton(
                text = if (selectedPlan?.trialDays != null) pluralStringResource(R.plurals.start_trial, selectedPlan.trialDays, selectedPlan.trialDays) else stringResource(R.string.continue_),
                onClick = onPurchase,
                enabled = selectedPlan != null && !state.busy,
                loading = state.busy,
                height = 58.dp,
                modifier = Modifier.fillMaxWidth(),
            )
            selectedPlan?.let {
                Text(trialTerms(it).asString(), style = MaterialTheme.typography.bodySmall, color = soft.textMuted, textAlign = TextAlign.Center)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SoftTextButton(stringResource(R.string.restore_purchases), onRestore, enabled = !state.busy, color = soft.textMuted)
                SoftTextButton(stringResource(R.string.manage_subscription), onManage, color = soft.textMuted)
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }
}

/** Soft plan card: the selected one is pressed in with an accent edge; the annual "save" badge is an accent pill. */
@Composable
private fun PlanCard(plan: Plan, selected: Boolean, highlighted: Boolean, savings: Int?, onClick: () -> Unit) {
    val soft = LocalSoft.current
    val press = rememberSoftPress()
    val shape = RoundedCornerShape(ZeulRadius.Panel)
    val edge by animateColorAsState(if (selected) soft.accent else Color.Transparent, ZeulMotion.snappy(), label = "planEdge")
    Row(
        Modifier
            .fillMaxWidth()
            .pressScale(press, 0.02f)
            .then(if (selected) Modifier.softInset(shape, depth = 3.dp) else Modifier.softRaised(shape, depth = 5.dp, pressed = press.progress))
            .border(2.dp, edge, shape)
            .softClickable(press, onClickLabel = null, role = Role.RadioButton, haptic = HapticFeedbackType.SegmentTick, onClick = onClick)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (plan.type == PlanType.ANNUAL) stringResource(R.string.plan_annual) else stringResource(R.string.plan_monthly), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = soft.text)
                if (highlighted && plan.type == PlanType.ANNUAL && savings != null) {
                    Text(
                        stringResource(R.string.save_percent, savings),
                        color = soft.onAccent,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 10.dp).clip(RoundedCornerShape(ZeulRadius.Small)).background(soft.accent).padding(horizontal = 10.dp, vertical = 3.dp),
                    )
                }
            }
            Text(
                if (plan.type == PlanType.ANNUAL) stringResource(R.string.billed_yearly, plan.pricePerMonth) else stringResource(R.string.billed_monthly),
                style = MaterialTheme.typography.bodySmall,
                color = soft.textMuted,
            )
            plan.trialDays?.let {
                Text(pluralStringResource(R.plurals.free_trial_days, it, it), style = MaterialTheme.typography.labelMedium, color = soft.accent)
            }
        }
        Text(plan.price + if (plan.type == PlanType.ANNUAL) "/yr" else "/mo", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = soft.text)
    }
}

/** Save-N% badge for annual vs 12× monthly, from the store's price strings. Null if they can't be compared. */
internal fun annualSavings(plans: List<Plan>): Int? {
    val monthly = plans.firstOrNull { it.type == PlanType.MONTHLY }?.price?.let(::parsePrice) ?: return null
    val annual = plans.firstOrNull { it.type == PlanType.ANNUAL }?.price?.let(::parsePrice) ?: return null
    if (monthly <= 0.0) return null
    val percent = ((1 - annual / (monthly * 12)) * 100).toInt()
    return percent.takeIf { it in 1..99 }
}

/** Number from a localized price like "₹1,299.00" or "$4.99". */
internal fun parsePrice(price: String): Double? =
    price.filter { it.isDigit() || it == '.' }.toDoubleOrNull()

/** Plain-words subscription terms under the button (Play policy: price, period, trial, how to cancel). */
internal fun trialTerms(plan: Plan): UiText {
    val annual = plan.type == PlanType.ANNUAL
    return if (plan.trialDays != null) {
        UiText.Plural(if (annual) R.plurals.trial_terms_year else R.plurals.trial_terms_month, plan.trialDays, listOf(plan.trialDays, plan.price))
    } else {
        UiText.Res(if (annual) R.string.renew_terms_year else R.string.renew_terms_month, listOf(plan.price))
    }
}
