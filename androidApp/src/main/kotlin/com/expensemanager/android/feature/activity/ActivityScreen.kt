@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.expensemanager.android.feature.activity

import android.app.DatePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Work
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.expensemanager.android.core.designsystem.FinanceThemeColors
import com.expensemanager.android.core.designsystem.financeColors
import com.expensemanager.android.core.model.FinanceCategory
import com.expensemanager.android.core.model.FinanceTransaction
import com.expensemanager.android.core.model.TransactionType
import com.expensemanager.android.core.model.formatMoney
import com.expensemanager.android.core.model.maskedMoney
import com.expensemanager.android.core.model.parseAmountToMinorUnits
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.delay

private data class ActivityFilters(
    val type: TransactionType? = null,
    val category: FinanceCategory? = null,
    val accountId: String? = null,
    val fromDate: LocalDate? = null,
    val toDate: LocalDate? = null,
    val minimumMinor: Long? = null,
    val maximumMinor: Long? = null,
) {
    val activeCount: Int
        get() = listOf(type, category, accountId, fromDate, toDate, minimumMinor, maximumMinor).count { it != null }
}

private sealed interface ActivityItem {
    val key: String

    data class DateHeader(val date: LocalDate) : ActivityItem {
        override val key: String = "date-$date"
    }

    data class TransactionRow(val transaction: FinanceTransaction) : ActivityItem {
        override val key: String = "transaction-${transaction.id}"
    }
}

@Composable
fun DayActivityScreen(
    date: LocalDate,
    transactions: List<FinanceTransaction>,
    amountsVisible: Boolean,
    onToggleAmountsVisibility: () -> Unit,
    onBack: () -> Unit,
    onEditTransaction: (FinanceTransaction) -> Unit,
    onDeleteTransaction: (FinanceTransaction) -> Unit,
) {
    val colors = financeColors()
    val dayExpenses = remember(transactions, date) {
        transactions
            .filter { it.type == TransactionType.EXPENSE && it.localDate() == date }
            .sortedByDescending { it.timestampMillis }
    }
    val totalSpent = dayExpenses.sumOf { it.amountMinor }
    var selectedTransaction by remember { mutableStateOf<FinanceTransaction?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(colors.appBackground),
        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "day-header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to home")
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault())),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "Expense records for this day",
                        color = colors.secondaryText,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                IconButton(
                    onClick = onToggleAmountsVisibility,
                    modifier = Modifier.semantics {
                        contentDescription = if (amountsVisible) "Hide amounts" else "Show amounts"
                    },
                ) {
                    Icon(
                        if (amountsVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = null,
                    )
                }
            }
        }
        item(key = "day-summary") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surface),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("Total spent", color = colors.secondaryText, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        if (amountsVisible) formatMoney(totalSpent) else maskedMoney(),
                        color = colors.expense,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "${dayExpenses.size} expense${if (dayExpenses.size == 1) "" else "s"}",
                        color = colors.secondaryText,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }

        if (dayExpenses.isEmpty()) {
            item(key = "empty-day") {
                ActivityEmptyState(
                    "No expenses on this day",
                    "Expenses added for this date will appear here.",
                    colors,
                )
            }
        } else {
            items(dayExpenses, key = { it.id }) { transaction ->
                ActivityTransactionRow(
                    transaction = transaction,
                    colors = colors,
                    amountsVisible = amountsVisible,
                    onClick = { selectedTransaction = transaction },
                )
            }
        }
    }

    selectedTransaction?.let { transaction ->
        TransactionDetailsSheet(
            transaction = transaction,
            amountsVisible = amountsVisible,
            onDismiss = { selectedTransaction = null },
            onEdit = {
                selectedTransaction = null
                onEditTransaction(transaction)
            },
            onDelete = {
                selectedTransaction = null
                onDeleteTransaction(transaction)
            },
        )
    }
}

@Composable
fun ActivityScreen(
    transactions: List<FinanceTransaction>,
    amountsVisible: Boolean,
    onToggleAmountsVisibility: () -> Unit,
    onOpenCategory: (FinanceCategory) -> Unit,
    onEditTransaction: (FinanceTransaction) -> Unit,
    onDeleteTransaction: (FinanceTransaction) -> Unit,
) {
    val colors = financeColors()
    val context = LocalContext.current
    var searchText by rememberSaveable { mutableStateOf("") }
    var debouncedQuery by rememberSaveable { mutableStateOf("") }
    var selectedTypeName by rememberSaveable { mutableStateOf<String?>(null) }
    var reportMonthText by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    var appliedFilters by remember { mutableStateOf(ActivityFilters()) }
    var showFilters by rememberSaveable { mutableStateOf(false) }
    var showMonthPicker by rememberSaveable { mutableStateOf(false) }
    var selectedTransaction by remember { mutableStateOf<FinanceTransaction?>(null) }
    val reportMonth = runCatching { YearMonth.parse(reportMonthText) }.getOrDefault(YearMonth.now())
    val selectedType = selectedTypeName?.let { runCatching { TransactionType.valueOf(it) }.getOrNull() }

    LaunchedEffect(searchText) {
        delay(250)
        debouncedQuery = searchText.trim()
    }

    if (showMonthPicker) {
        LaunchedEffect(Unit) {
            DatePickerDialog(
                context,
                { _, year, month, _ ->
                    reportMonthText = YearMonth.of(year, month + 1).toString()
                    showMonthPicker = false
                },
                reportMonth.year,
                reportMonth.monthValue - 1,
                1,
            ).apply { setOnDismissListener { showMonthPicker = false } }.show()
        }
    }

    val visibleTransactions = remember(transactions, debouncedQuery, selectedType, appliedFilters) {
        transactions.asSequence()
            .filter { selectedType == null || it.type == selectedType }
            .filter { matchesQuery(it, debouncedQuery) }
            .filter { matchesFilters(it, appliedFilters) }
            .sortedByDescending { it.timestampMillis }
            .toList()
    }
    val reportTransactions = remember(transactions, reportMonth) {
        transactions.filter { YearMonth.from(it.localDate()) == reportMonth }
    }
    val monthExpenses = reportTransactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amountMinor }
    val monthIncome = reportTransactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amountMinor }
    val activityItems = remember(visibleTransactions) { buildActivityItems(visibleTransactions) }
    val categoryTotals = remember(transactions) {
        transactions.asSequence()
            .filter { it.type == TransactionType.EXPENSE }
            .groupBy { it.category }
            .map { (category, categoryTransactions) ->
                CategoryActivityTotal(
                    category = category,
                    amountMinor = categoryTransactions.sumOf { it.amountMinor },
                    transactionCount = categoryTransactions.size,
                )
            }
            .sortedByDescending { it.amountMinor }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(colors.appBackground).padding(horizontal = 16.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item(key = "activity-overview") {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Activity", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Track every rupee you spend",
                            color = colors.secondaryText,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Row {
                        IconButton(
                            onClick = onToggleAmountsVisibility,
                            modifier = Modifier.semantics {
                                contentDescription = if (amountsVisible) "Hide amounts" else "Show amounts"
                            },
                        ) {
                            Icon(
                                if (amountsVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = null,
                            )
                        }
                        IconButton(
                            onClick = { showMonthPicker = true },
                            modifier = Modifier.semantics { contentDescription = "Choose reporting month" },
                        ) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = null)
                        }
                    }
                }

                OutlinedTextField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                    placeholder = { Text("Search transactions...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (searchText.isNotEmpty()) {
                                IconButton(onClick = { searchText = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear search")
                                }
                            }
                            IconButton(
                                onClick = { showFilters = true },
                                modifier = Modifier.semantics { contentDescription = "Open transaction filters" },
                            ) {
                                Icon(Icons.Default.FilterList, contentDescription = null)
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                )

                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ActivityChip("All", selectedType == null) { selectedTypeName = null }
                    ActivityChip("Expenses", selectedType == TransactionType.EXPENSE) {
                        selectedTypeName = TransactionType.EXPENSE.name
                    }
                    ActivityChip("Income", selectedType == TransactionType.INCOME) {
                        selectedTypeName = TransactionType.INCOME.name
                    }
                    ActivityChip("Transfers", selectedType == TransactionType.TRANSFER) {
                        selectedTypeName = TransactionType.TRANSFER.name
                    }
                    if (appliedFilters.activeCount > 0) {
                        Text(
                            "${appliedFilters.activeCount} filter${if (appliedFilters.activeCount == 1) "" else "s"}",
                            color = colors.primary,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.align(Alignment.CenterVertically),
                        )
                    }
                }

                SummaryCard(reportMonth, monthExpenses, monthIncome, colors, amountsVisible)
                if (categoryTotals.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Text("Expense categories", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Tap a category to see every transaction",
                        color = colors.secondaryText,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    LazyRow(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(categoryTotals, key = { it.category.key }) { total ->
                            CategoryActivityCard(
                                total = total,
                                colors = colors,
                                amountsVisible = amountsVisible,
                                onClick = { onOpenCategory(total.category) },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text("Transactions", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
            }
        }

        when {
            transactions.isEmpty() -> item(key = "empty-transactions") {
                ActivityEmptyState("No transactions yet", "Add an expense, income, or transfer to see it here.", colors)
            }

            visibleTransactions.isEmpty() -> item(key = "empty-filtered-transactions") {
                ActivityEmptyState("No matching transactions", "Try clearing your search or resetting the filters.", colors)
            }

            else -> items(activityItems, key = { it.key }) { item ->
                when (item) {
                    is ActivityItem.DateHeader -> DateHeader(item.date, colors)
                    is ActivityItem.TransactionRow -> ActivityTransactionRow(
                        transaction = item.transaction,
                        colors = colors,
                        amountsVisible = amountsVisible,
                        onClick = { selectedTransaction = item.transaction },
                    )
                }
            }
        }
    }

    if (showFilters) {
        ActivityFilterSheet(
            transactions = transactions,
            initialFilters = appliedFilters,
            onDismiss = { showFilters = false },
            onApply = {
                appliedFilters = it
                showFilters = false
            },
        )
    }

    selectedTransaction?.let { transaction ->
        TransactionDetailsSheet(
            transaction = transaction,
            amountsVisible = amountsVisible,
            onDismiss = { selectedTransaction = null },
            onEdit = {
                selectedTransaction = null
                onEditTransaction(transaction)
            },
            onDelete = {
                selectedTransaction = null
                onDeleteTransaction(transaction)
            },
        )
    }
}

private data class CategoryActivityTotal(
    val category: FinanceCategory,
    val amountMinor: Long,
    val transactionCount: Int,
)

@Composable
private fun CategoryActivityCard(
    total: CategoryActivityTotal,
    colors: FinanceThemeColors,
    amountsVisible: Boolean,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.width(160.dp).semantics {
            contentDescription = "Open ${total.category.label} activity"
        },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = BorderStroke(1.dp, colors.border),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Box(
                modifier = Modifier.size(36.dp).clip(RoundedCornerShape(11.dp))
                    .background(colors.categoryTints[total.category.ordinal]),
                contentAlignment = Alignment.Center,
            ) {
                Icon(categoryIcon(total.category), contentDescription = null, modifier = Modifier.size(19.dp))
            }
            Text(total.category.label, fontWeight = FontWeight.SemiBold)
            Text(
                if (amountsVisible) formatMoney(total.amountMinor) else maskedMoney(),
                color = colors.primaryText,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            Text(
                "${total.transactionCount} transaction${if (total.transactionCount == 1) "" else "s"}",
                color = colors.secondaryText,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun ActivityChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}

@Composable
private fun SummaryCard(
    month: YearMonth,
    expenses: Long,
    income: Long,
    colors: FinanceThemeColors,
    amountsVisible: Boolean,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = BorderStroke(1.dp, colors.border),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())),
                color = colors.secondaryText,
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SummaryValue("Expenses", expenses, colors.expense, Modifier.weight(1f), amountsVisible)
                SummaryValue("Income", income, colors.income, Modifier.weight(1f), amountsVisible)
            }
        }
    }
}

@Composable
private fun SummaryValue(label: String, amount: Long, tint: Color, modifier: Modifier, amountsVisible: Boolean) {
    Column(modifier) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
        Text(
            if (amountsVisible) formatMoney(amount) else maskedMoney(),
            color = tint,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun DateHeader(date: LocalDate, colors: FinanceThemeColors) {
    Text(
        dateLabel(date),
        color = colors.secondaryText,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun ActivityTransactionRow(
    transaction: FinanceTransaction,
    colors: FinanceThemeColors,
    amountsVisible: Boolean,
    onClick: () -> Unit,
) {
    val amountColor = when (transaction.type) {
        TransactionType.EXPENSE -> colors.expense
        TransactionType.INCOME -> colors.income
        TransactionType.TRANSFER -> colors.primary
    }
    val amount = when (transaction.type) {
        TransactionType.EXPENSE -> formatMoney(-transaction.amountMinor, transaction.currencyCode, withSign = true)
        TransactionType.INCOME -> formatMoney(transaction.amountMinor, transaction.currencyCode, withSign = true)
        TransactionType.TRANSFER -> "↔ ${formatMoney(transaction.amountMinor, transaction.currencyCode)}"
    }
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(13.dp)).background(categoryTint(transaction.category, colors)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(categoryIcon(transaction.category), contentDescription = transaction.category.label, tint = colors.primaryText, modifier = Modifier.size(21.dp))
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                transaction.description.ifBlank { transaction.category.label },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${transaction.category.label} · ${transaction.account.name} · ${formatTime(transaction)}",
                color = colors.secondaryText,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            if (amountsVisible) amount else maskedMoney(),
            color = amountColor,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun ActivityEmptyState(message: String, detail: String, colors: FinanceThemeColors) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = colors.primary, modifier = Modifier.size(30.dp))
            Text(message, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(detail, color = colors.secondaryText, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ActivityFilterSheet(
    transactions: List<FinanceTransaction>,
    initialFilters: ActivityFilters,
    onDismiss: () -> Unit,
    onApply: (ActivityFilters) -> Unit,
) {
    val colors = financeColors()
    val context = LocalContext.current
    var type by remember(initialFilters) { mutableStateOf(initialFilters.type) }
    var category by remember(initialFilters) { mutableStateOf(initialFilters.category) }
    var accountId by remember(initialFilters) { mutableStateOf(initialFilters.accountId) }
    var fromDate by remember(initialFilters) { mutableStateOf(initialFilters.fromDate) }
    var toDate by remember(initialFilters) { mutableStateOf(initialFilters.toDate) }
    var minimumText by remember(initialFilters) { mutableStateOf(initialFilters.minimumMinor?.let { (it / 100.0).toString() }.orEmpty()) }
    var maximumText by remember(initialFilters) { mutableStateOf(initialFilters.maximumMinor?.let { (it / 100.0).toString() }.orEmpty()) }
    val categories = transactions.map { it.category }.distinct().sortedBy { it.label }
    val accounts = transactions.map { it.account }.distinctBy { it.id }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Filter transactions", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text("Transaction type", style = MaterialTheme.typography.titleLarge)
            FilterOptions(
                labels = listOf("All") + TransactionType.entries.map { it.label() },
                selectedIndex = type?.ordinal?.plus(1) ?: 0,
                onSelected = { type = if (it == 0) null else TransactionType.entries[it - 1] },
            )
            if (categories.isNotEmpty()) {
                Text("Category", style = MaterialTheme.typography.titleLarge)
                FilterOptions(
                    labels = listOf("All") + categories.map { it.label },
                    selectedIndex = category?.let { categories.indexOf(it) + 1 } ?: 0,
                    onSelected = { category = if (it == 0) null else categories[it - 1] },
                )
            }
            if (accounts.isNotEmpty()) {
                Text("Account", style = MaterialTheme.typography.titleLarge)
                FilterOptions(
                    labels = listOf("All") + accounts.map { it.name },
                    selectedIndex = accountId?.let { id -> accounts.indexOfFirst { it.id == id } + 1 } ?: 0,
                    onSelected = { accountId = if (it == 0) null else accounts[it - 1].id },
                )
            }
            Text("Date range", style = MaterialTheme.typography.titleLarge)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DateFilterButton("From", fromDate, Modifier.weight(1f)) {
                    DatePickerDialog(context, { _, year, month, day -> fromDate = LocalDate.of(year, month + 1, day) }, LocalDate.now().year, LocalDate.now().monthValue - 1, LocalDate.now().dayOfMonth).show()
                }
                DateFilterButton("To", toDate, Modifier.weight(1f)) {
                    DatePickerDialog(context, { _, year, month, day -> toDate = LocalDate.of(year, month + 1, day) }, LocalDate.now().year, LocalDate.now().monthValue - 1, LocalDate.now().dayOfMonth).show()
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = minimumText,
                    onValueChange = { minimumText = it },
                    modifier = Modifier.weight(1f),
                    label = { Text("Min amount") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = maximumText,
                    onValueChange = { maximumText = it },
                    modifier = Modifier.weight(1f),
                    label = { Text("Max amount") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton(
                    onClick = { onApply(ActivityFilters()) },
                    modifier = Modifier.weight(1f),
                ) { Text("Reset filters") }
                Button(
                    onClick = {
                        onApply(
                            ActivityFilters(
                                type = type,
                                category = category,
                                accountId = accountId,
                                fromDate = fromDate,
                                toDate = toDate,
                                minimumMinor = minimumText.takeIf { it.isNotBlank() }?.let(::parseAmountToMinorUnits),
                                maximumMinor = maximumText.takeIf { it.isNotBlank() }?.let(::parseAmountToMinorUnits),
                            ),
                        )
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("Apply filters") }
            }
            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun FilterOptions(labels: List<String>, selectedIndex: Int, onSelected: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEachIndexed { index, label ->
            FilterChip(selected = selectedIndex == index, onClick = { onSelected(index) }, label = { Text(label) })
        }
    }
}

@Composable
private fun DateFilterButton(label: String, date: LocalDate?, modifier: Modifier, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = modifier.height(52.dp), contentPadding = PaddingValues(horizontal = 10.dp)) {
        Text(if (date == null) label else date.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())))
    }
}

@Composable
private fun TransactionDetailsSheet(
    transaction: FinanceTransaction,
    amountsVisible: Boolean,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = financeColors()
    var confirmDelete by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Transaction details", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(
                if (amountsVisible) detailAmount(transaction) else maskedMoney(),
                color = detailAmountColor(transaction, colors),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
            )
            DetailLine("Type", transaction.type.label(), colors)
            DetailLine("Category", transaction.category.label, colors)
            DetailLine("Account", transaction.account.name, colors)
            transaction.destinationAccount?.let { DetailLine("To account", it.name, colors) }
            DetailLine("Date", formatDateTime(transaction), colors)
            if (transaction.description.isNotBlank()) DetailLine("Note", transaction.description, colors)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Edit, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Edit")
                }
                Button(onClick = { confirmDelete = true }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Delete")
                }
            }
            Spacer(Modifier.height(18.dp))
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete transaction?") },
            text = { Text("This will remove the transaction and update the related totals." ) },
            confirmButton = { TextButton(onClick = onDelete) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun DetailLine(label: String, value: String, colors: FinanceThemeColors) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = colors.secondaryText, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

private fun buildActivityItems(transactions: List<FinanceTransaction>): List<ActivityItem> = buildList {
    var previousDate: LocalDate? = null
    transactions.forEach { transaction ->
        val date = transaction.localDate()
        if (date != previousDate) {
            add(ActivityItem.DateHeader(date))
            previousDate = date
        }
        add(ActivityItem.TransactionRow(transaction))
    }
}

private fun matchesQuery(transaction: FinanceTransaction, query: String): Boolean {
    if (query.isBlank()) return true
    val searchable = listOf(
        transaction.description,
        transaction.category.label,
        transaction.account.name,
        transaction.destinationAccount?.name.orEmpty(),
    )
    return searchable.any { it.contains(query, ignoreCase = true) }
}

private fun matchesFilters(transaction: FinanceTransaction, filters: ActivityFilters): Boolean {
    val date = transaction.localDate()
    return (filters.type == null || transaction.type == filters.type) &&
        (filters.category == null || transaction.category == filters.category) &&
        (filters.accountId == null || transaction.account.id == filters.accountId || transaction.destinationAccount?.id == filters.accountId) &&
        (filters.fromDate == null || date >= filters.fromDate) &&
        (filters.toDate == null || date <= filters.toDate) &&
        (filters.minimumMinor == null || transaction.amountMinor >= filters.minimumMinor) &&
        (filters.maximumMinor == null || transaction.amountMinor <= filters.maximumMinor)
}

private fun dateLabel(date: LocalDate): String {
    val today = LocalDate.now()
    return when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault()))
    }
}

private fun FinanceTransaction.localDate(): LocalDate = Instant.ofEpochMilli(timestampMillis).atZone(ZoneId.systemDefault()).toLocalDate()

private fun formatTime(transaction: FinanceTransaction): String =
    DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()).format(Instant.ofEpochMilli(transaction.timestampMillis).atZone(ZoneId.systemDefault()))

private fun formatDateTime(transaction: FinanceTransaction): String =
    DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale.getDefault()).format(Instant.ofEpochMilli(transaction.timestampMillis).atZone(ZoneId.systemDefault()))

private fun detailAmount(transaction: FinanceTransaction): String = when (transaction.type) {
    TransactionType.EXPENSE -> formatMoney(-transaction.amountMinor, transaction.currencyCode, withSign = true)
    TransactionType.INCOME -> formatMoney(transaction.amountMinor, transaction.currencyCode, withSign = true)
    TransactionType.TRANSFER -> "↔ ${formatMoney(transaction.amountMinor, transaction.currencyCode)}"
}

private fun detailAmountColor(transaction: FinanceTransaction, colors: FinanceThemeColors): Color = when (transaction.type) {
    TransactionType.EXPENSE -> colors.expense
    TransactionType.INCOME -> colors.income
    TransactionType.TRANSFER -> colors.primary
}

private fun categoryTint(category: FinanceCategory, colors: FinanceThemeColors): Color =
    colors.categoryTints[category.ordinal.coerceIn(0, colors.categoryTints.lastIndex)]

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
    FinanceCategory.OTHER -> Icons.Default.AccountBalanceWallet
}

private fun TransactionType.label(): String = when (this) {
    TransactionType.EXPENSE -> "Expense"
    TransactionType.INCOME -> "Income"
    TransactionType.TRANSFER -> "Transfer"
}
