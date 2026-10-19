package com.expensemanager.android.feature.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.expensemanager.android.core.model.FinanceCategory
import com.expensemanager.android.core.model.FinanceTransaction
import com.expensemanager.android.core.model.TransactionType
import com.expensemanager.android.core.model.calculateExpenses
import com.expensemanager.android.core.model.calculateIncome
import com.expensemanager.android.core.model.calculateTotalBalance
import com.expensemanager.android.core.model.formatMoney
import com.expensemanager.android.core.model.maskedMoney
import com.expensemanager.android.core.designsystem.FinanceThemeColors
import com.expensemanager.android.core.designsystem.financeColors
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun HomeScreen(
    transactions: List<FinanceTransaction>,
    onAddExpense: () -> Unit,
    onViewAll: () -> Unit,
    onOpenInsights: () -> Unit,
    onOpenSettings: () -> Unit,
    amountsVisible: Boolean,
    onToggleAmountsVisibility: () -> Unit,
) {
    val colors = financeColors()
    val today = LocalDate.now()
    val currentMonth = YearMonth.from(today)
    val monthTransactions = transactions.filter { YearMonth.from(it.localDate()) == currentMonth }
    val income = calculateIncome(monthTransactions)
    val expenses = calculateExpenses(monthTransactions)
    val balance = calculateTotalBalance(transactions)
    val recentTransactions = transactions.sortedByDescending { it.timestampMillis }.take(4)
    val growth = monthlyGrowthPercent(transactions, currentMonth)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.appBackground),
        contentPadding = PaddingValues(start = 16.dp, top = 2.dp, end = 16.dp, bottom = 92.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { DashboardHeader(colors, onOpenSettings) }
        item {
            BalanceCard(
                balance = balance,
                colors = colors,
                growthPercent = growth,
                amountsVisible = amountsVisible,
                onToggleVisibility = onToggleAmountsVisibility,
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                MonthlyMetricCard(
                    modifier = Modifier.weight(1f),
                    label = "Income",
                    amount = income,
                    icon = Icons.Default.ArrowUpward,
                    accent = colors.income,
                    colors = colors,
                    amountsVisible = amountsVisible,
                )
                MonthlyMetricCard(
                    modifier = Modifier.weight(1f),
                    label = "Expenses",
                    amount = expenses,
                    icon = Icons.Default.ArrowDownward,
                    accent = colors.expense,
                    colors = colors,
                    amountsVisible = amountsVisible,
                )
            }
        }
        item {
            MonthlyWeeklySpending(
                transactions = transactions,
                today = today,
                colors = colors,
                amountsVisible = amountsVisible,
                onOpenInsights = onOpenInsights,
            )
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Recent Transactions", color = colors.primaryText, style = MaterialTheme.typography.titleLarge)
                Text(
                    "See all",
                    color = colors.primary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onViewAll)
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                        .semantics { contentDescription = "See all transactions" },
                )
            }
        }
        if (recentTransactions.isEmpty()) {
            item { EmptyDashboard(colors, onAddExpense) }
        } else {
            items(recentTransactions, key = { it.id }) { transaction ->
                TransactionRow(transaction, colors, amountsVisible)
            }
        }
    }
}

@Composable
private fun DashboardHeader(colors: FinanceThemeColors, onOpenSettings: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(54.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            "RupeyaWise",
            color = colors.primary,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = {},
                modifier = Modifier.semantics { contentDescription = "Notifications" },
            ) {
                Icon(Icons.Default.NotificationsNone, contentDescription = null, tint = colors.primaryText)
            }
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier.semantics { contentDescription = "Open profile and settings" },
            ) {
                Box(
                    modifier = Modifier.size(34.dp).clip(CircleShape).background(colors.elevatedSurface),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Person, contentDescription = null, tint = colors.secondaryText, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun BalanceCard(
    balance: Long,
    colors: FinanceThemeColors,
    growthPercent: Int?,
    amountsVisible: Boolean,
    onToggleVisibility: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, colors.border),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(listOf(colors.balanceStart, colors.balanceEnd)))
                .padding(16.dp),
        ) {
            BalanceDecoration(
                colors = colors,
                modifier = Modifier.matchParentSize(),
            )
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Total Balance", color = colors.secondaryText, style = MaterialTheme.typography.bodyMedium)
                    IconButton(
                        onClick = onToggleVisibility,
                        modifier = Modifier.size(36.dp).semantics {
                            contentDescription = if (amountsVisible) "Hide balances" else "Show balances"
                        },
                    ) {
                        Icon(
                            imageVector = if (amountsVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = null,
                            tint = colors.secondaryText,
                            modifier = Modifier.size(17.dp),
                        )
                    }
                }
                Text(
                    text = if (amountsVisible) balanceAmountText(balance) else AnnotatedString(maskedAmount()),
                    color = colors.primaryText,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (growthPercent != null) {
                    val positive = growthPercent >= 0
                    Row(
                        modifier = Modifier.padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = if (positive) "↑ ${growthPercent}%" else "↓ ${-growthPercent}%",
                            color = if (positive) colors.primary else colors.expense,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text("vs last month", color = colors.secondaryText, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun BalanceDecoration(colors: FinanceThemeColors, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(
        modifier = modifier.semantics { contentDescription = "Decorative balance trend accent" },
    ) {
        val barWidth = size.width * 0.035f
        val barGap = size.width * 0.025f
        val chartWidth = barWidth * 3f + barGap * 2f
        val startX = size.width - chartWidth - size.width * 0.04f
        val base = size.height * 0.82f
        val accent = colors.primary.copy(alpha = 0.55f)
        listOf(0.35f, 0.54f, 0.77f).forEachIndexed { index, fraction ->
            drawRoundRect(
                color = accent,
                topLeft = androidx.compose.ui.geometry.Offset(startX + index * (barWidth + barGap), base - size.height * fraction),
                size = androidx.compose.ui.geometry.Size(barWidth, size.height * fraction),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth, barWidth),
            )
        }
        drawLine(
            color = colors.primary.copy(alpha = 0.4f),
            start = androidx.compose.ui.geometry.Offset(startX - size.width * 0.08f, size.height * 0.88f),
            end = androidx.compose.ui.geometry.Offset(size.width * 0.97f, size.height * 0.2f),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun MonthlyMetricCard(
    modifier: Modifier,
    label: String,
    amount: Long,
    icon: ImageVector,
    accent: Color,
    colors: FinanceThemeColors,
    amountsVisible: Boolean,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier.size(30.dp).clip(CircleShape).background(accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, tint = colors.appBackground, modifier = Modifier.size(17.dp))
                }
                Text(label, color = colors.secondaryText, style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                if (amountsVisible) formatMoney(amount) else maskedAmount(),
                color = colors.primaryText,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text("This month", color = colors.secondaryText, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun MonthlyWeeklySpending(
    transactions: List<FinanceTransaction>,
    today: LocalDate,
    colors: FinanceThemeColors,
    amountsVisible: Boolean,
    onOpenInsights: () -> Unit,
) {
    val pagerState = rememberPagerState(
        initialPage = WeeklyHistoryPageCount - 1,
        pageCount = { WeeklyHistoryPageCount },
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalPager(
            state = pagerState,
            pageSpacing = 12.dp,
            modifier = Modifier.fillMaxWidth(),
        ) { page ->
            val weeksAgo = WeeklyHistoryPageCount - 1L - page
            val weekAnchor = today.minusWeeks(weeksAgo)
            WeeklySpendingCard(
                title = weekTitle(weekAnchor, today),
                spending = weekSpending(transactions, weekAnchor),
                colors = colors,
                amountsVisible = amountsVisible,
                onOpenInsights = onOpenInsights,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(WeeklyHistoryPageCount) { page ->
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .size(if (page == pagerState.currentPage) 8.dp else 6.dp)
                        .clip(CircleShape)
                        .background(
                            if (page == pagerState.currentPage) colors.primary else colors.elevatedSurface,
                        ),
                )
            }
            Text(
                text = "  Swipe right for earlier weeks",
                color = colors.secondaryText,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun WeeklySpendingCard(
    title: String,
    spending: List<DailySpending>,
    colors: FinanceThemeColors,
    amountsVisible: Boolean,
    onOpenInsights: () -> Unit,
) {
    val total = spending.sumOf { it.amountMinor }
    val maxValue = spending.maxOfOrNull { it.amountMinor }?.coerceAtLeast(1L) ?: 1L
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(title, color = colors.primaryText, style = MaterialTheme.typography.titleLarge)
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onOpenInsights)
                    .padding(start = 8.dp, top = 6.dp, bottom = 6.dp)
                    .semantics { contentDescription = "Open weekly spending insights" },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    if (amountsVisible) formatMoney(total) else maskedAmount(),
                    color = colors.primaryText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = colors.secondaryText, modifier = Modifier.size(18.dp))
            }
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = colors.surface),
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(108.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    spending.forEach { day ->
                        val ratio = (day.amountMinor.toFloat() / maxValue.toFloat()).coerceIn(0f, 1f)
                        val barHeight = (6f + ratio * 62f).dp
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .clickable { }
                                .semantics {
                                    val amountDescription = if (amountsVisible) {
                                        formatMoney(day.amountMinor)
                                    } else {
                                        "Amount hidden"
                                    }
                                    contentDescription = "${day.date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())}: $amountDescription"
                                },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom,
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(18.dp)
                                    .height(barHeight)
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(if (day.amountMinor > 0) colors.primary else colors.elevatedSurface),
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    spending.forEach { day ->
                        Text(
                            text = day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(3),
                            modifier = Modifier.weight(1f),
                            color = colors.secondaryText,
                            style = MaterialTheme.typography.labelMedium,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TransactionRow(transaction: FinanceTransaction, colors: FinanceThemeColors, amountsVisible: Boolean) {
    val amountColor = when (transaction.type) {
        TransactionType.INCOME -> colors.income
        TransactionType.EXPENSE -> colors.expense
        TransactionType.TRANSFER -> colors.secondaryText
    }
    val signedAmount = when (transaction.type) {
        TransactionType.INCOME -> transaction.amountMinor
        TransactionType.EXPENSE -> -transaction.amountMinor
        TransactionType.TRANSFER -> transaction.amountMinor
    }
    val title = when {
        transaction.description.isNotBlank() -> transaction.description
        transaction.type == TransactionType.TRANSFER -> "${transaction.account.name} → ${transaction.destinationAccount?.name.orEmpty()}"
        else -> transaction.category.label
    }
    val dateText = transaction.localDateTime().format(DateTimeFormatter.ofPattern("dd MMM, h:mm a", Locale.getDefault()))
    val subtitle = if (transaction.type == TransactionType.TRANSFER) {
        "Transfer · ${transaction.account.name}"
    } else {
        "${transaction.category.label} · ${transaction.account.name}"
    }
    val amountDescription = if (amountsVisible) {
        formatMoney(signedAmount, withSign = true)
    } else {
        "Amount hidden"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surface.copy(alpha = 0.72f))
            .padding(horizontal = 10.dp, vertical = 9.dp)
            .semantics {
                contentDescription = "$title, $subtitle, $dateText, $amountDescription"
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(categoryBackground(transaction.category, colors)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(categoryIcon(transaction.category), contentDescription = null, tint = colors.primaryText, modifier = Modifier.size(19.dp))
        }
        Column(
            modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(title, color = colors.primaryText, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = colors.secondaryText, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(dateText, color = colors.secondaryText.copy(alpha = 0.76f), style = MaterialTheme.typography.labelMedium)
        }
        Text(
            if (amountsVisible) formatMoney(signedAmount, transaction.currencyCode, withSign = true) else maskedAmount(),
            color = amountColor,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun EmptyDashboard(colors: FinanceThemeColors, onAddExpense: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Icon(Icons.Default.Add, contentDescription = null, tint = colors.primary, modifier = Modifier.size(28.dp))
            Text("No transactions yet", color = colors.primaryText, style = MaterialTheme.typography.titleLarge)
            Text(
                "Add your first transaction to see your finances here.",
                color = colors.secondaryText,
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = onAddExpense, shape = RoundedCornerShape(12.dp)) { Text("Add transaction") }
        }
    }
}

private data class DailySpending(val date: LocalDate, val amountMinor: Long)

// Five calendar-week pages guarantee that the user can reach at least one full
// month of history even when the current week has only just started.
private const val WeeklyHistoryPageCount = 5

private fun weekTitle(weekAnchor: LocalDate, today: LocalDate): String {
    val weekStart = weekAnchor.minusDays((weekAnchor.dayOfWeek.value - 1).toLong())
    val currentWeekStart = today.minusDays((today.dayOfWeek.value - 1).toLong())
    return when (weekStart) {
        currentWeekStart -> "This Week"
        currentWeekStart.minusWeeks(1) -> "Last Week"
        else -> {
            val weekEnd = weekStart.plusDays(6)
            val formatter = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
            "${weekStart.format(formatter)} – ${weekEnd.format(formatter)}"
        }
    }
}

private fun weekSpending(transactions: List<FinanceTransaction>, today: LocalDate): List<DailySpending> {
    val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
    return (0L..6L).map { offset ->
        val date = monday.plusDays(offset)
        DailySpending(
            date = date,
            amountMinor = transactions.asSequence()
                .filter { it.type == TransactionType.EXPENSE && it.localDate() == date }
                .sumOf { it.amountMinor },
        )
    }
}

private fun monthlyGrowthPercent(transactions: List<FinanceTransaction>, currentMonth: YearMonth): Int? {
    val previousMonth = currentMonth.minusMonths(1)
    val currentNet = monthlyNet(transactions, currentMonth)
    val previousNet = monthlyNet(transactions, previousMonth)
    if (previousNet == 0L) return null
    return (((currentNet - previousNet).toDouble() / kotlin.math.abs(previousNet.toDouble())) * 100).roundToInt()
}

private fun monthlyNet(transactions: List<FinanceTransaction>, month: YearMonth): Long =
    transactions.asSequence()
        .filter { YearMonth.from(it.localDate()) == month }
        .sumOf {
            when (it.type) {
                TransactionType.INCOME -> it.amountMinor
                TransactionType.EXPENSE -> -it.amountMinor
                TransactionType.TRANSFER -> 0L
            }
        }

private fun maskedAmount(): String = maskedMoney()

private fun balanceAmountText(balance: Long): AnnotatedString = buildAnnotatedString {
    val formattedAmount = formatMoney(balance)
    if (balance < 0) {
        append("Rs ")
        withStyle(SpanStyle(fontSize = 26.sp)) { append("−") }
        append(formattedAmount.removePrefix("Rs "))
    } else {
        append(formattedAmount)
    }
}

private fun FinanceTransaction.localDateTime() =
    Instant.ofEpochMilli(timestampMillis).atZone(ZoneId.systemDefault()).toLocalDateTime()

private fun FinanceTransaction.localDate(): LocalDate = localDateTime().toLocalDate()

private fun categoryIcon(category: FinanceCategory): ImageVector = when (category) {
    FinanceCategory.FOOD -> Icons.Default.Restaurant
    FinanceCategory.TRANSPORT -> Icons.Default.DirectionsCar
    FinanceCategory.BILLS -> Icons.Default.ReceiptLong
    FinanceCategory.SHOPPING -> Icons.Default.ShoppingCart
    FinanceCategory.HEALTH -> Icons.Default.Favorite
    FinanceCategory.EDUCATION -> Icons.Default.School
    FinanceCategory.ENTERTAINMENT -> Icons.Default.MoreHoriz
    FinanceCategory.SALARY -> Icons.Default.Work
    FinanceCategory.FREELANCE -> Icons.Default.Payments
    FinanceCategory.TRANSFER -> Icons.Default.SwapHoriz
    FinanceCategory.OTHER -> Icons.Default.LocalGasStation
}

private fun categoryBackground(category: FinanceCategory, colors: FinanceThemeColors): Color =
    colors.categoryTints[category.ordinal.coerceIn(0, colors.categoryTints.lastIndex)]
