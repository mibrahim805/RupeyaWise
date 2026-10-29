package com.expensemanager.android.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.expensemanager.android.core.designsystem.FinanceThemeMode
import com.expensemanager.android.core.auth.AuthProfile
import com.expensemanager.android.core.auth.AuthUser
import com.expensemanager.android.core.presentation.FinanceEvent
import com.expensemanager.android.core.presentation.FinanceViewModel
import com.expensemanager.android.core.model.FinanceCategory
import com.expensemanager.android.feature.home.HomeScreen
import com.expensemanager.android.feature.insights.InsightsScreen
import com.expensemanager.android.feature.activity.ActivityScreen
import com.expensemanager.android.feature.activity.CategoryActivityScreen
import com.expensemanager.android.feature.activity.DayActivityScreen
import com.expensemanager.android.feature.budgets.BudgetsScreen
import com.expensemanager.android.feature.transactions.AddTransactionScreen
import com.expensemanager.android.feature.settings.SettingsScreen
import com.expensemanager.android.feature.settings.WhatsNewScreen

private data class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

private val topLevelDestinations = listOf(
    TopLevelDestination("home", "Home", Icons.Default.Home),
    TopLevelDestination("activity", "Activity", Icons.Default.ReceiptLong),
    TopLevelDestination("budgets", "Budgets", Icons.Default.PieChart),
    TopLevelDestination("insights", "Insights", Icons.Default.Analytics),
)

// Short, low-distance motion feels smoother on lower-end devices because the old
// 440 ms transitions kept two full screens rendering together for too long.
private const val ScreenEnterDurationMillis = 240
private const val ScreenExitDurationMillis = 160
private const val ChromeTransitionDurationMillis = 180

private fun <T> screenEnterTween() = tween<T>(
    durationMillis = ScreenEnterDurationMillis,
    easing = FastOutSlowInEasing,
)

private fun <T> screenExitTween() = tween<T>(
    durationMillis = ScreenExitDurationMillis,
    easing = FastOutLinearInEasing,
)

private fun <T> chromeTween() = tween<T>(
    durationMillis = ChromeTransitionDurationMillis,
    easing = FastOutSlowInEasing,
)

private fun AnimatedContentTransitionScope<*>.smoothEnterTransition(): EnterTransition =
    fadeIn(animationSpec = screenEnterTween()) +
        slideIntoContainer(
            towards = AnimatedContentTransitionScope.SlideDirection.Left,
            animationSpec = screenEnterTween(),
            initialOffset = { distance -> distance / 24 },
        )

private fun AnimatedContentTransitionScope<*>.smoothExitTransition(): ExitTransition =
    fadeOut(animationSpec = screenExitTween()) +
        slideOutOfContainer(
            towards = AnimatedContentTransitionScope.SlideDirection.Left,
            animationSpec = screenExitTween(),
            targetOffset = { distance -> distance / 24 },
        )

private fun AnimatedContentTransitionScope<*>.smoothPopEnterTransition(): EnterTransition =
    fadeIn(animationSpec = screenEnterTween()) +
        slideIntoContainer(
            towards = AnimatedContentTransitionScope.SlideDirection.Right,
            animationSpec = screenEnterTween(),
            initialOffset = { distance -> distance / 24 },
        )

private fun AnimatedContentTransitionScope<*>.smoothPopExitTransition(): ExitTransition =
    fadeOut(animationSpec = screenExitTween()) +
        slideOutOfContainer(
            towards = AnimatedContentTransitionScope.SlideDirection.Right,
            animationSpec = screenExitTween(),
            targetOffset = { distance -> distance / 24 },
        )

@Composable
@androidx.compose.material3.ExperimentalMaterial3Api
fun ExpenseManagerApp(
    viewModel: FinanceViewModel,
    themeMode: FinanceThemeMode,
    currentUser: AuthUser,
    profiles: List<AuthProfile>,
    isGuest: Boolean,
    amountProtectionEnabled: Boolean,
    onAuthenticateBalance: (() -> Unit) -> Unit,
    onAmountProtectionChange: (Boolean) -> Unit,
    onLogout: () -> Unit,
    onSwitchProfile: (Long) -> Unit,
    onAddAccount: () -> Unit,
    onThemeChange: (FinanceThemeMode) -> Unit,
) {
    val navController = rememberNavController()
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()
    val budgets by viewModel.budgets.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    var amountsVisible by remember(currentUser.id) { mutableStateOf(!amountProtectionEnabled) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbarHostState = remember { SnackbarHostState() }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val isFullscreen = currentRoute == "add" || currentRoute == "settings" || currentRoute == "whats-new" ||
        currentRoute?.startsWith("edit/") == true || currentRoute?.startsWith("category/") == true ||
        currentRoute?.startsWith("day/") == true
    val colors = MaterialTheme.colorScheme
    val toggleAmountsVisibility = {
        if (amountsVisible) {
            amountsVisible = false
        } else if (!amountProtectionEnabled) {
            amountsVisible = true
        } else {
            onAuthenticateBalance { amountsVisible = true }
        }
    }

    LaunchedEffect(currentUser.id, amountProtectionEnabled) {
        amountsVisible = !amountProtectionEnabled
    }

    DisposableEffect(lifecycleOwner, amountProtectionEnabled) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && amountProtectionEnabled) amountsVisible = false
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                FinanceEvent.TransactionSaved -> {
                    navController.popBackStack()
                    snackbarHostState.showSnackbar("Transaction saved")
                }
                FinanceEvent.TransactionUpdated -> {
                    navController.popBackStack()
                    snackbarHostState.showSnackbar("Transaction updated")
                }
                FinanceEvent.TransactionDeleted -> snackbarHostState.showSnackbar("Transaction deleted")
                FinanceEvent.BudgetSaved -> snackbarHostState.showSnackbar("Budget saved")
                FinanceEvent.BudgetDeleted -> snackbarHostState.showSnackbar("Budget deleted")
                is FinanceEvent.Error -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = colors.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            AnimatedVisibility(
                visible = !isFullscreen,
                enter = fadeIn(chromeTween()) + slideInVertically(chromeTween()) { height -> height / 4 },
                exit = fadeOut(chromeTween()) + slideOutVertically(chromeTween()) { height -> height / 4 },
            ) {
                NavigationBar(containerColor = colors.surface) {
                    topLevelDestinations.forEach { destination ->
                        val selected = currentRoute == destination.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo("home") { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            colors = androidx.compose.material3.NavigationBarItemDefaults.colors(
                                selectedIconColor = colors.primary,
                                selectedTextColor = colors.primary,
                                indicatorColor = colors.surfaceVariant,
                                unselectedIconColor = colors.onSurfaceVariant,
                                unselectedTextColor = colors.onSurfaceVariant,
                            ),
                            icon = { Icon(destination.icon, contentDescription = destination.label) },
                            label = { Text(destination.label) },
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = !isFullscreen,
                enter = fadeIn(chromeTween()) + scaleIn(chromeTween(), initialScale = 0.9f),
                exit = fadeOut(chromeTween()) + scaleOut(chromeTween(), targetScale = 0.9f),
            ) {
                FloatingActionButton(
                    onClick = { navController.navigate("add") },
                    containerColor = colors.primary,
                    contentColor = colors.onPrimary,
                    modifier = Modifier.semantics { contentDescription = "Add transaction" },
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                }
            }
        },
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = if (isFullscreen) {
                Modifier.windowInsetsPadding(WindowInsets.safeDrawing)
            } else {
                Modifier.padding(paddingValues)
            },
            enterTransition = { smoothEnterTransition() },
            exitTransition = { smoothExitTransition() },
            popEnterTransition = { smoothPopEnterTransition() },
            popExitTransition = { smoothPopExitTransition() },
        ) {
            composable("home") {
                HomeScreen(
                    transactions = transactions,
                    onAddExpense = { navController.navigate("add") },
                    onViewAll = { navController.navigate("activity") },
                    onOpenDay = { date -> navController.navigate("day/${date.toEpochDay()}") },
                    onOpenInsights = { navController.navigate("insights") },
                    onOpenSettings = { navController.navigate("settings") },
                    amountsVisible = amountsVisible,
                    onToggleAmountsVisibility = toggleAmountsVisibility,
                )
            }
            composable("add") {
                AddTransactionScreen(
                    isSaving = isSaving,
                    onBack = { navController.popBackStack() },
                    onSave = viewModel::saveTransaction,
                )
            }
            composable("edit/{transactionId}") { entry ->
                val transactionId = entry.arguments?.getString("transactionId")?.toLongOrNull()
                val transaction = transactions.firstOrNull { it.id == transactionId }
                if (transaction != null) {
                    AddTransactionScreen(
                        isSaving = isSaving,
                        existingTransaction = transaction,
                        onBack = { navController.popBackStack() },
                        onSave = { draft -> viewModel.updateTransaction(transaction.id, draft) },
                    )
                } else {
                    PlaceholderScreen("Transaction unavailable", "This transaction could not be found.")
                }
            }
            composable("settings") {
                SettingsScreen(
                    selectedTheme = themeMode,
                    currentUser = currentUser,
                    profiles = profiles,
                    isGuest = isGuest,
                    amountProtectionEnabled = amountProtectionEnabled,
                    onBack = { navController.popBackStack() },
                    onLogout = onLogout,
                    onSwitchProfile = onSwitchProfile,
                    onAddAccount = onAddAccount,
                    onOpenWhatsNew = { navController.navigate("whats-new") },
                    onThemeSelected = onThemeChange,
                    onAmountProtectionChange = onAmountProtectionChange,
                    onCreateBackup = viewModel::createEncryptedBackup,
                    onRestoreBackup = viewModel::restoreEncryptedBackup,
                )
            }
            composable("whats-new") {
                WhatsNewScreen(onBack = { navController.popBackStack() })
            }
            composable("activity") {
                ActivityScreen(
                    transactions = transactions,
                    amountsVisible = amountsVisible,
                    onToggleAmountsVisibility = toggleAmountsVisibility,
                    onOpenCategory = { category -> navController.navigate("category/${category.key}") },
                    onEditTransaction = { transaction -> navController.navigate("edit/${transaction.id}") },
                    onDeleteTransaction = { transaction -> viewModel.deleteTransaction(transaction.id) },
                )
            }
            composable("budgets") {
                BudgetsScreen(
                    transactions = transactions,
                    budgets = budgets,
                    amountsVisible = amountsVisible,
                    onToggleAmountsVisibility = toggleAmountsVisibility,
                    onSetBudget = viewModel::setBudget,
                    onDeleteBudget = viewModel::deleteBudget,
                )
            }
            composable("insights") {
                InsightsScreen(
                    transactions = transactions,
                    amountsVisible = amountsVisible,
                    onToggleAmountsVisibility = toggleAmountsVisibility,
                    onOpenCategory = { category -> navController.navigate("category/${category.key}") },
                )
            }
            composable("category/{categoryKey}") { entry ->
                val category = FinanceCategory.fromKey(entry.arguments?.getString("categoryKey"))
                CategoryActivityScreen(
                    category = category,
                    transactions = transactions,
                    amountsVisible = amountsVisible,
                    onToggleAmountsVisibility = toggleAmountsVisibility,
                    onBack = { navController.popBackStack() },
                    onEditTransaction = { transaction -> navController.navigate("edit/${transaction.id}") },
                    onDeleteTransaction = { transaction -> viewModel.deleteTransaction(transaction.id) },
                )
            }
            composable("day/{epochDay}") { entry ->
                val date = entry.arguments?.getString("epochDay")?.toLongOrNull()?.let(java.time.LocalDate::ofEpochDay)
                if (date != null) {
                    DayActivityScreen(
                        date = date,
                        transactions = transactions,
                        amountsVisible = amountsVisible,
                        onToggleAmountsVisibility = toggleAmountsVisibility,
                        onBack = { navController.popBackStack() },
                        onEditTransaction = { transaction -> navController.navigate("edit/${transaction.id}") },
                        onDeleteTransaction = { transaction -> viewModel.deleteTransaction(transaction.id) },
                    )
                } else {
                    PlaceholderScreen("Day unavailable", "This date could not be opened.")
                }
            }
        }
    }
}

@Composable
private fun PlaceholderScreen(title: String, message: String) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        androidx.compose.foundation.layout.Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
            Text(title, style = androidx.compose.material3.MaterialTheme.typography.headlineSmall)
            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
