package com.beatwave.music.ui.screens.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.beatwave.music.BuildConfig
import com.beatwave.music.R
import com.beatwave.music.SuggestionRow
import com.beatwave.music.BugReportRow
import com.beatwave.music.api.GitHubIssueService
import com.beatwave.music.api.SupabaseService
import com.beatwave.music.constants.SubmittedSuggestionIdsKey
import com.beatwave.music.constants.SubmittedBugReportIdsKey
import com.beatwave.music.ui.component.IconButton
import com.beatwave.music.ui.utils.appTopBarWindowInsets
import com.beatwave.music.utils.dataStore
import com.beatwave.music.utils.get
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.launch

sealed class HistoryItem {
    abstract val id: Long
    abstract val createdAt: String
    abstract val description: String
    abstract val status: String
    abstract val typeLabel: String
    abstract val adminComment: String?

    data class Suggestion(val row: SuggestionRow) : HistoryItem() {
        override val id: Long = row.id ?: 0L
        override val createdAt: String = row.created_at ?: ""
        override val description: String = row.content
        override val status: String = row.status
        override val typeLabel: String = "Recommendation"
        override val adminComment: String? = row.admin_comment
    }

    data class Bug(val row: BugReportRow) : HistoryItem() {
        override val id: Long = row.id ?: 0L
        override val createdAt: String = row.created_at ?: ""
        override val description: String = row.description
        override val status: String = row.status
        override val typeLabel: String = "Issue / Bug"
        override val adminComment: String? = row.admin_comment
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuggestionScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    initialTab: Int = 0
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    // 0: Recommendations, 1: Issue Noting, 2: History
    var selectedTab by rememberSaveable { mutableIntStateOf(initialTab.coerceIn(0, 2)) }

    // Form State - Recommendations
    var recName by rememberSaveable { mutableStateOf("") }
    var recInstaId by rememberSaveable { mutableStateOf("") }
    var recContent by rememberSaveable { mutableStateOf("") }
    var isSubmittingRec by remember { mutableStateOf(false) }

    // Form State - Issue Noting
    var issueName by rememberSaveable { mutableStateOf("") }
    var issueInstaId by rememberSaveable { mutableStateOf("") }
    var issueTitle by rememberSaveable { mutableStateOf("") }
    var issueDescription by rememberSaveable { mutableStateOf("") }
    var syncWithGitHub by rememberSaveable { mutableStateOf(true) }
    var isSubmittingIssue by remember { mutableStateOf(false) }

    // Post-Submit GitHub Prompt Dialog
    var showGitHubConfirmDialog by remember { mutableStateOf(false) }
    var lastSubmittedIssueTitle by remember { mutableStateOf("") }
    var lastSubmittedIssueDesc by remember { mutableStateOf("") }

    // History State
    var pastSuggestions by remember { mutableStateOf<List<SuggestionRow>>(emptyList()) }
    var pastBugs by remember { mutableStateOf<List<BugReportRow>>(emptyList()) }
    var isFetchingHistory by remember { mutableStateOf(false) }

    val deviceInfo = remember {
        "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} (Android ${android.os.Build.VERSION.RELEASE}, API ${android.os.Build.VERSION.SDK_INT})"
    }

    // Fetch history when history tab is selected
    LaunchedEffect(selectedTab) {
        if (selectedTab == 2) {
            isFetchingHistory = true
            try {
                val suggestionIdsStr = context.dataStore.get(SubmittedSuggestionIdsKey, "")
                val bugIdsStr = context.dataStore.get(SubmittedBugReportIdsKey, "")

                val suggestionIds = suggestionIdsStr.split(",")
                    .mapNotNull { it.trim().toLongOrNull() }
                val bugIds = bugIdsStr.split(",")
                    .mapNotNull { it.trim().toLongOrNull() }

                val suggestionsResult = SupabaseService.fetchSuggestionsByIds(suggestionIds)
                val bugsResult = SupabaseService.fetchBugReportsByIds(bugIds)

                if (suggestionsResult.isSuccess) {
                    pastSuggestions = suggestionsResult.getOrDefault(emptyList())
                }
                if (bugsResult.isSuccess) {
                    pastBugs = bugsResult.getOrDefault(emptyList())
                }
            } catch (_: Exception) {}
            isFetchingHistory = false
        }
    }

    if (showGitHubConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showGitHubConfirmDialog = false },
            icon = {
                Icon(
                    painter = painterResource(R.drawable.github),
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = { Text("Issue Saved! Pop up on GitHub?") },
            text = {
                Text("Your issue has been saved to the database. Would you like to confirm and open it on the GitHub Issues panel of BSFrameworks5253/Plusewave as well?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showGitHubConfirmDialog = false
                        GitHubIssueService.openPrefilledIssueInBrowser(
                            context = context,
                            title = lastSubmittedIssueTitle.ifBlank { "Bug Report" },
                            description = lastSubmittedIssueDesc,
                            reporterName = issueName,
                            instaId = issueInstaId.ifBlank { null },
                            deviceInfo = deviceInfo,
                            isBug = true
                        )
                    }
                ) {
                    Text("Open on GitHub Issues")
                }
            },
            dismissButton = {
                TextButton(onClick = { showGitHubConfirmDialog = false }) {
                    Text("Done")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = appTopBarWindowInsets(),
                title = {
                    Text(
                        when (selectedTab) {
                            0 -> "Recommendations"
                            1 -> "Issue Noting"
                            else -> "My Submitted History"
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
                    }
                },
                actions = {
                    IconButton(
                        onClick = { GitHubIssueService.openIssuesPanel(context) },
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.github),
                            contentDescription = "View GitHub Issues",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            // Top Tabs Row: Recommendations, Issue Noting, History
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Recommendations") },
                    icon = {
                        Icon(painterResource(R.drawable.sparks), contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Issue Noting") },
                    icon = {
                        Icon(painterResource(R.drawable.bug_report), contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("History") },
                    icon = {
                        Icon(painterResource(R.drawable.history), contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                )
            }

            when (selectedTab) {
                0 -> {
                    // TAB 0: RECOMMENDATIONS PAGE
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Header Banner
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.sparks),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(28.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Shape Pulse Wave",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        text = "Suggest new features, UI tweaks, streaming improvements, or audio ideas.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = recName,
                            onValueChange = { recName = it },
                            label = { Text("Your Name (Required)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )

                        OutlinedTextField(
                            value = recInstaId,
                            onValueChange = { recInstaId = it },
                            label = { Text("Instagram ID / Handle (Optional)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )

                        OutlinedTextField(
                            value = recContent,
                            onValueChange = { recContent = it },
                            label = { Text("Describe your recommendation or idea...") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 5,
                            shape = RoundedCornerShape(12.dp)
                        )

                        Button(
                            onClick = {
                                if (recName.isBlank()) {
                                    Toast.makeText(context, "Please enter your name", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                if (recContent.isBlank()) {
                                    Toast.makeText(context, "Please enter your suggestion", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                isSubmittingRec = true
                                coroutineScope.launch {
                                    val result = SupabaseService.submitSuggestion(
                                        userName = recName,
                                        instaId = recInstaId.ifBlank { null },
                                        content = recContent
                                    )
                                    isSubmittingRec = false
                                    if (result.isSuccess) {
                                        val generatedId = result.getOrNull()
                                        if (generatedId != null) {
                                            val currentIds = context.dataStore.get(SubmittedSuggestionIdsKey, "")
                                            val newIds = if (currentIds.isBlank()) "$generatedId" else "$currentIds,$generatedId"
                                            context.dataStore.edit { it[SubmittedSuggestionIdsKey] = newIds }
                                        }
                                        Toast.makeText(context, "Recommendation submitted! Thank you.", Toast.LENGTH_LONG).show()
                                        recContent = ""
                                        selectedTab = 2 // Switch to History
                                    } else {
                                        Toast.makeText(context, "Failed: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isSubmittingRec,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (isSubmittingRec) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("Submit Recommendation")
                            }
                        }

                        // Direct GitHub Community link
                        OutlinedButton(
                            onClick = { GitHubIssueService.openIssuesPanel(context) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.github),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Browse Ideas on GitHub Issues Panel")
                        }
                    }
                }

                1 -> {
                    // TAB 1: ISSUE NOTING PAGE (LINKED WITH GITHUB ISSUES PANEL)
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // GitHub Issues Panel Card
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.github),
                                        contentDescription = null,
                                        modifier = Modifier.size(26.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "GitHub Issues Panel Linked",
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Repo: BSFrameworks5253/Plusewave",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }

                                Text(
                                    text = "All issues noted here can be tracked directly on our official GitHub Issues panel for real-time developer updates.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Button(
                                    onClick = { GitHubIssueService.openIssuesPanel(context) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                        contentColor = MaterialTheme.colorScheme.primary
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.github),
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text("Open GitHub Issues Panel ↗")
                                }
                            }
                        }

                        // Auto-detected Device Info
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.info),
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = deviceInfo,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        OutlinedTextField(
                            value = issueName,
                            onValueChange = { issueName = it },
                            label = { Text("Your Name (Required)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )

                        OutlinedTextField(
                            value = issueInstaId,
                            onValueChange = { issueInstaId = it },
                            label = { Text("Instagram ID / Contact (Optional)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )

                        OutlinedTextField(
                            value = issueTitle,
                            onValueChange = { issueTitle = it },
                            label = { Text("Issue Title / Summary") },
                            placeholder = { Text("e.g. Song stuttering during playback") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )

                        OutlinedTextField(
                            value = issueDescription,
                            onValueChange = { issueDescription = it },
                            label = { Text("Describe the bug or issue in detail...") },
                            placeholder = { Text("What happened, steps to reproduce, or error messages...") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 5,
                            shape = RoundedCornerShape(12.dp)
                        )

                        // Checkbox to link/pop up on GitHub issues
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Checkbox(
                                checked = syncWithGitHub,
                                onCheckedChange = { syncWithGitHub = it }
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Pop up & post to GitHub Issues panel",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Button(
                            onClick = {
                                if (issueName.isBlank()) {
                                    Toast.makeText(context, "Please write your name first", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                if (issueDescription.isBlank()) {
                                    Toast.makeText(context, "Please write a description first", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                isSubmittingIssue = true
                                coroutineScope.launch {
                                    val fullDesc = if (issueTitle.isNotBlank()) {
                                        "${issueTitle.trim()}\n\n$issueDescription"
                                    } else {
                                        issueDescription
                                    }

                                    val result = SupabaseService.submitBugReport(
                                        userName = issueName,
                                        instaId = issueInstaId.ifBlank { null },
                                        description = fullDesc,
                                        deviceInfo = deviceInfo
                                    )

                                    var githubIssueCreated = false
                                    // Try direct GitHub API submission if token is available
                                    if (syncWithGitHub && GitHubIssueService.isApiConfigured) {
                                        val gitHubResult = GitHubIssueService.createGitHubIssue(
                                            title = issueTitle.ifBlank { issueDescription.take(50) },
                                            description = issueDescription,
                                            reporterName = issueName,
                                            instaId = issueInstaId.ifBlank { null },
                                            deviceInfo = deviceInfo,
                                            isBug = true
                                        )
                                        if (gitHubResult.isSuccess) {
                                            githubIssueCreated = true
                                            Toast.makeText(
                                                context,
                                                "Posted to GitHub Issues #${gitHubResult.getOrNull()?.number}!",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    }

                                    isSubmittingIssue = false
                                    if (result.isSuccess) {
                                        val generatedId = result.getOrNull()
                                        if (generatedId != null) {
                                            val currentIds = context.dataStore.get(SubmittedBugReportIdsKey, "")
                                            val newIds = if (currentIds.isBlank()) "$generatedId" else "$currentIds,$generatedId"
                                            context.dataStore.edit { it[SubmittedBugReportIdsKey] = newIds }
                                        }

                                        lastSubmittedIssueTitle = issueTitle.ifBlank { issueDescription.take(60) }
                                        lastSubmittedIssueDesc = issueDescription

                                        if (syncWithGitHub && !githubIssueCreated) {
                                            // Show confirmation dialog to open prefilled GitHub issue
                                            showGitHubConfirmDialog = true
                                        } else {
                                            Toast.makeText(context, "Issue recorded successfully!", Toast.LENGTH_LONG).show()
                                            selectedTab = 2 // Switch to History
                                        }

                                        issueTitle = ""
                                        issueDescription = ""
                                    } else {
                                        Toast.makeText(context, "Failed: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isSubmittingIssue,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (isSubmittingIssue) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("Log Issue & Link with GitHub")
                            }
                        }
                    }
                }

                else -> {
                    // TAB 2: MY SUBMITTED HISTORY
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = if (isFetchingHistory) Arrangement.Center else Arrangement.spacedBy(12.dp)
                    ) {
                        if (isFetchingHistory) {
                            CircularProgressIndicator()
                        } else {
                            val historyItems = (pastSuggestions.map { HistoryItem.Suggestion(it) } +
                                    pastBugs.map { HistoryItem.Bug(it) })
                                .sortedByDescending { it.createdAt }

                            if (historyItems.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 64.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "You haven't submitted any recommendations or issues yet.",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                historyItems.forEach { item ->
                                    HistoryCard(item = item)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryCard(item: HistoryItem) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Type Header Badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (item is HistoryItem.Bug) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = item.typeLabel,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (item is HistoryItem.Bug) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                // Status Badge
                val statusColor = when (item.status.lowercase()) {
                    "done", "fixed" -> Color(0xFF34C759)
                    "reviewed", "investigating" -> Color(0xFF007AFF)
                    "rejected", "closed" -> Color(0xFFFF3B30)
                    else -> Color(0xFFFF9500)
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = statusColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = item.status.uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Text(
                text = item.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )

            // Date & GitHub button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val displayDate = if (item.createdAt.length >= 10) item.createdAt.substring(0, 10) else item.createdAt
                Text(
                    text = "Submitted: $displayDate",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (item is HistoryItem.Bug) {
                    TextButton(
                        onClick = {
                            GitHubIssueService.openPrefilledIssueInBrowser(
                                context = context,
                                title = item.description.lines().firstOrNull()?.take(60) ?: "Bug Report",
                                description = item.description,
                                reporterName = null,
                                isBug = true
                            )
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.github),
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("View on GitHub", fontSize = 12.sp)
                    }
                }
            }

            item.adminComment?.takeIf { it.isNotBlank() }?.let { reply ->
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "REPLY FROM THE TEAM",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = reply,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}
