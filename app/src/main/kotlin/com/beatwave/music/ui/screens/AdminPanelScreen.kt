package com.beatwave.music.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.beatwave.music.AppUpdateRow
import com.beatwave.music.R
import com.beatwave.music.SuggestionRow
import com.beatwave.music.BugReportRow
import com.beatwave.music.api.SupabaseService
import com.beatwave.music.api.GitHubIssueService
import com.beatwave.music.LocalDatabase
import com.beatwave.music.constants.ShowWrappedCardKey
import com.beatwave.music.constants.WrappedSeenKey
import com.beatwave.music.constants.WrappedIntervalDaysKey
import com.beatwave.music.ui.screens.wrapped.provideWrappedManager
import com.beatwave.music.LocalPlayerAwareWindowInsets
import com.beatwave.music.ui.utils.appTopBarWindowInsets
import com.beatwave.music.utils.dataStore
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminPanelScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Recommendations", "Issue Noting", "Publish Update", "Publish News", "Test Wrapped")

    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = appTopBarWindowInsets(),
                title = { Text("Admin Panel") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
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
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                edgePadding = 16.dp
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }

            val playerBottomPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues().calculateBottomPadding()
            Box(modifier = Modifier.fillMaxSize()) {
                when (selectedTab) {
                    0 -> SuggestionsTab(playerBottomPadding)
                    1 -> BugReportsTab(playerBottomPadding)
                    2 -> PublishUpdateTab(navController, playerBottomPadding)
                    3 -> PublishNewsTab(navController, playerBottomPadding)
                    else -> TestWrappedTab(navController, playerBottomPadding)
                }
            }
        }
    }
}

@Composable
fun PublishUpdateTab(navController: NavController, bottomPadding: androidx.compose.ui.unit.Dp = 0.dp) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var version by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var apkUrl by remember { mutableStateOf("") }
    var isForceUpdate by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 16.dp + bottomPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Publish a new app update that will be shown to users when they open the app.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value = version,
            onValueChange = { version = it },
            label = { Text("Update Version (e.g. v5.1.2)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Update Title (e.g. Hotfix Released)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Changelog / Description") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3
        )

        OutlinedTextField(
            value = apkUrl,
            onValueChange = { apkUrl = it },
            label = { Text("APK URL (Optional)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Force Update", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Users cannot dismiss the update dialog",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = isForceUpdate,
                onCheckedChange = { isForceUpdate = it }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (version.isBlank() || title.isBlank() || description.isBlank()) {
                    Toast.makeText(context, "Please fill in Version, Title, and Description", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                isLoading = true
                coroutineScope.launch {
                    val update = AppUpdateRow(
                        version = version,
                        title = title,
                        description = description,
                        update_type = if (isForceUpdate) "force" else "optional",
                        apk_url = apkUrl.ifBlank { null }
                    )
                    val result = SupabaseService.publishUpdate(update)
                    isLoading = false
                    if (result.isSuccess) {
                        Toast.makeText(context, "Update published successfully!", Toast.LENGTH_LONG).show()
                        navController.navigateUp()
                    } else {
                        Toast.makeText(context, "Failed: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading,
            shape = RoundedCornerShape(12.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                Text("Publish Update")
            }
        }
    }
}

@Composable
fun SuggestionsTab(bottomPadding: androidx.compose.ui.unit.Dp = 0.dp) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var suggestions by remember { mutableStateOf<List<SuggestionRow>>(emptyList()) }
    var isFetching by remember { mutableStateOf(true) }
    var actionInProgressId by remember { mutableStateOf<Long?>(null) }

    fun refreshSuggestions() {
        isFetching = true
        coroutineScope.launch {
            val result = SupabaseService.fetchSuggestions()
            isFetching = false
            if (result.isSuccess) {
                suggestions = result.getOrDefault(emptyList())
            } else {
                Toast.makeText(context, "Failed to load suggestions: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshSuggestions()
    }

    var searchQuery by remember { mutableStateOf("") }
    var selectedStatus by remember { mutableStateOf("All") }
    val statusOptions = listOf("All", "Pending", "Approved", "Acknowledged", "Rejected")

    val filteredSuggestions = remember(suggestions, searchQuery, selectedStatus) {
        suggestions.filter { item ->
            val matchesStatus = selectedStatus == "All" || item.status.equals(selectedStatus, ignoreCase = true)
            val matchesQuery = searchQuery.isBlank() ||
                item.content.contains(searchQuery, ignoreCase = true) ||
                item.user_name.contains(searchQuery, ignoreCase = true) ||
                item.insta_id.orEmpty().contains(searchQuery, ignoreCase = true)
            matchesStatus && matchesQuery
        }
    }

    var showClearAllDialog by remember { mutableStateOf(false) }

    if (showClearAllDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            title = { Text("Purge All Suggestions?") },
            text = { Text("This will permanently delete all user suggestions from the database. This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearAllDialog = false
                        isFetching = true
                        coroutineScope.launch(Dispatchers.IO) {
                            val res = SupabaseService.clearAllSuggestions()
                            withContext(Dispatchers.Main) {
                                isFetching = false
                                if (res.isSuccess) {
                                    Toast.makeText(context, "All suggestions purged", Toast.LENGTH_SHORT).show()
                                    refreshSuggestions()
                                } else {
                                    Toast.makeText(context, "Failed to purge suggestions: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Purge All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (isFetching) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else if (suggestions.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No suggestions submitted yet.", style = MaterialTheme.typography.bodyMedium)
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search suggestions, author, insta...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(painterResource(R.drawable.close), contentDescription = "Clear")
                            }
                        }
                    }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(statusOptions) { status ->
                            val count = if (status == "All") suggestions.size else suggestions.count { it.status.equals(status, ignoreCase = true) }
                            FilterChip(
                                selected = selectedStatus == status,
                                onClick = { selectedStatus = status },
                                label = { Text("$status ($count)") }
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { showClearAllDialog = true },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    ) {
                        Icon(painterResource(R.drawable.delete), contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Purge")
                    }
                }
            }

            if (filteredSuggestions.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No suggestions match current filter.", style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 16.dp + bottomPadding),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredSuggestions, key = { it.id ?: 0L }) { item ->
                SuggestionItemCard(
                    item = item,
                    actionInProgress = actionInProgressId == item.id,
                    onUpdateStatus = { newStatus ->
                        item.id?.let { id ->
                            actionInProgressId = id
                            coroutineScope.launch {
                                val res = SupabaseService.updateSuggestionStatus(id, newStatus)
                                actionInProgressId = null
                                if (res.isSuccess) {
                                    refreshSuggestions()
                                } else {
                                    Toast.makeText(context, "Error updating status: ${res.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                    onSaveComment = { comment ->
                        item.id?.let { id ->
                            actionInProgressId = id
                            coroutineScope.launch {
                                val res = SupabaseService.updateSuggestionComment(id, comment)
                                actionInProgressId = null
                                if (res.isSuccess) {
                                    refreshSuggestions()
                                } else {
                                    Toast.makeText(context, "Error saving comment: ${res.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                    onDelete = {
                        item.id?.let { id ->
                            actionInProgressId = id
                            coroutineScope.launch {
                                val res = SupabaseService.deleteSuggestion(id)
                                actionInProgressId = null
                                if (res.isSuccess) {
                                    refreshSuggestions()
                                } else {
                                    Toast.makeText(context, "Error deleting suggestion: ${res.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                )
            }
        }
    }
}
}
}

@Composable
fun SuggestionItemCard(
    item: SuggestionRow,
    actionInProgress: Boolean,
    onUpdateStatus: (String) -> Unit,
    onSaveComment: (String) -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusBadge(status = item.status)
                
                IconButton(
                    onClick = onDelete,
                    enabled = !actionInProgress
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Suggestion",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            Text(
                text = "By: ${item.user_name}" + if (!item.insta_id.isNullOrBlank()) " (${item.insta_id})" else "",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )

            Text(
                text = item.content,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            item.created_at?.let { date ->
                // Basic cleanup of ISO timestamp for user display
                val cleanDate = date.substringBefore("T")
                Text(
                    text = "Submitted: $cleanDate",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (actionInProgress) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = { onUpdateStatus("approved") },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF4CAF50))
                    ) {
                        Text("Approve")
                    }

                    TextButton(
                        onClick = { onUpdateStatus("acknowledged") },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF2196F3))
                    ) {
                        Text("Acknowledge")
                    }

                    TextButton(
                        onClick = { onUpdateStatus("rejected") },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFFF9800))
                    ) {
                        Text("Reject")
                    }
                }
            }

            AdminCommentEditor(
                savedComment = item.admin_comment,
                enabled = !actionInProgress,
                onSave = onSaveComment
            )
        }
    }
}

/**
 * Inline editor for the admin's public reply on a suggestion or bug report.
 *
 * Collapsed to a single line until tapped so a long list of reports stays
 * scannable; [savedComment] is the value currently stored server-side and is
 * what the submitter sees in their own list.
 */
@Composable
fun AdminCommentEditor(
    savedComment: String?,
    enabled: Boolean,
    onSave: (String) -> Unit,
) {
    var isEditing by remember(savedComment) { mutableStateOf(false) }
    var draft by remember(savedComment) { mutableStateOf(savedComment.orEmpty()) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

        if (!isEditing) {
            if (!savedComment.isNullOrBlank()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "ADMIN REPLY",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = savedComment,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            TextButton(onClick = { isEditing = true }, enabled = enabled) {
                Text(if (savedComment.isNullOrBlank()) "Add comment" else "Edit comment")
            }
        } else {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Reply to the user") },
                placeholder = { Text("e.g. Added in v6.1.0 — thanks!") },
                minLines = 2,
                enabled = enabled
            )

            val quickReplies = listOf(
                "Fixed in v6.3.0!",
                "Investigating now, thank you!",
                "Planned for upcoming update!",
                "Could you share reproduction steps?"
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                items(quickReplies) { reply ->
                    SuggestionChip(
                        onClick = { draft = reply },
                        label = { Text(reply, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = {
                        onSave(draft.trim())
                        isEditing = false
                    },
                    enabled = enabled
                ) {
                    Text("Save")
                }
                TextButton(
                    onClick = {
                        draft = savedComment.orEmpty()
                        isEditing = false
                    },
                    enabled = enabled
                ) {
                    Text("Cancel")
                }
            }
        }
    }
}

@Composable
fun StatusBadge(status: String) {
    val (bgColor, textColor) = when (status.lowercase()) {
        "approved" -> Color(0xFFE8F5E9) to Color(0xFF2E7D32)
        "acknowledged" -> Color(0xFFE3F2FD) to Color(0xFF1565C0)
        "rejected" -> Color(0xFFFFF3E0) to Color(0xFFE65100)
        else -> Color(0xFFECEFF1) to Color(0xFF37474F)
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = status.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = textColor
        )
    }
}

@Composable
fun PublishNewsTab(navController: NavController, bottomPadding: androidx.compose.ui.unit.Dp = 0.dp) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var imageUrl by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 16.dp + bottomPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Publish news or blog posts that will appear as a popup window to users on startup.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Blog/News Title") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        OutlinedTextField(
            value = content,
            onValueChange = { content = it },
            label = { Text("Content / Body") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 6
        )

        OutlinedTextField(
            value = imageUrl,
            onValueChange = { imageUrl = it },
            label = { Text("Image URL (Optional)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (title.isBlank() || content.isBlank()) {
                    Toast.makeText(context, "Please fill in Title and Content", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                isLoading = true
                coroutineScope.launch {
                    val news = com.beatwave.music.NewsUpdateRow(
                        title = title,
                        content = content,
                        image_url = imageUrl.ifBlank { null }
                    )
                    val result = SupabaseService.publishNews(news)
                    isLoading = false
                    if (result.isSuccess) {
                        Toast.makeText(context, "News published successfully!", Toast.LENGTH_LONG).show()
                        navController.navigateUp()
                    } else {
                        Toast.makeText(context, "Failed: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading,
            shape = RoundedCornerShape(12.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                Text("Publish Post")
            }
        }
    }
}

@Composable
fun BugReportsTab(bottomPadding: androidx.compose.ui.unit.Dp = 0.dp) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var bugReports by remember { mutableStateOf<List<BugReportRow>>(emptyList()) }
    var isFetching by remember { mutableStateOf(true) }
    var actionInProgressId by remember { mutableStateOf<Long?>(null) }

    fun refreshBugReports() {
        isFetching = true
        coroutineScope.launch {
            val result = SupabaseService.fetchBugReports()
            isFetching = false
            if (result.isSuccess) {
                bugReports = result.getOrDefault(emptyList())
            } else {
                Toast.makeText(context, "Failed to load bug reports: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshBugReports()
    }

    var searchQuery by remember { mutableStateOf("") }
    var selectedStatus by remember { mutableStateOf("All") }
    val statusOptions = listOf("All", "Pending", "Investigating", "Fixed", "Closed")

    val filteredBugReports = remember(bugReports, searchQuery, selectedStatus) {
        bugReports.filter { item ->
            val matchesStatus = selectedStatus == "All" || item.status.equals(selectedStatus, ignoreCase = true)
            val matchesQuery = searchQuery.isBlank() ||
                item.description.contains(searchQuery, ignoreCase = true) ||
                item.user_name.contains(searchQuery, ignoreCase = true) ||
                item.device_info.orEmpty().contains(searchQuery, ignoreCase = true) ||
                item.insta_id.orEmpty().contains(searchQuery, ignoreCase = true)
            matchesStatus && matchesQuery
        }
    }

    var showClearAllDialog by remember { mutableStateOf(false) }

    if (showClearAllDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            title = { Text("Purge Bug Reports & Logs?") },
            text = { Text("This will permanently delete all bug reports from the server and purge all local crash logs, dumps, and debug trace files. This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearAllDialog = false
                        isFetching = true
                        coroutineScope.launch(Dispatchers.IO) {
                            val res = SupabaseService.clearAllBugReports()
                            runCatching {
                                java.io.File(context.filesDir, "latest_crash.txt").delete()
                                context.cacheDir.listFiles { _, name ->
                                    name.startsWith("pulsewave_crash_") ||
                                    name.startsWith("beatwave_crash_") ||
                                    name.startsWith("crash_") ||
                                    name.endsWith(".log") ||
                                    name.endsWith(".trace")
                                }?.forEach { it.delete() }
                                context.filesDir.listFiles { _, name ->
                                    name.startsWith("pulsewave_crash_") ||
                                    name.startsWith("beatwave_crash_") ||
                                    name.startsWith("crash_") ||
                                    name.endsWith(".log") ||
                                    name.endsWith(".trace")
                                }?.forEach { it.delete() }
                            }
                            withContext(Dispatchers.Main) {
                                isFetching = false
                                if (res.isSuccess) {
                                    Toast.makeText(context, "Bug reports & crash logs purged", Toast.LENGTH_SHORT).show()
                                    refreshBugReports()
                                } else {
                                    Toast.makeText(context, "Purged local logs. Server: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Purge All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (isFetching) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search description, device, user...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(painterResource(R.drawable.close), contentDescription = "Clear")
                            }
                        }
                    }
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(statusOptions) { status ->
                        val count = if (status == "All") bugReports.size else bugReports.count { it.status.equals(status, ignoreCase = true) }
                        FilterChip(
                            selected = selectedStatus == status,
                            onClick = { selectedStatus = status },
                            label = { Text("$status ($count)") }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { GitHubIssueService.openIssuesPanel(context) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.github),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("GitHub Issues ↗")
                    }

                    Button(
                        onClick = { showClearAllDialog = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Purge Reports & Logs")
                    }
                }
            }

            if (filteredBugReports.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (bugReports.isEmpty()) "No bug reports submitted yet." else "No bug reports match current filter.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 16.dp + bottomPadding),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredBugReports, key = { it.id ?: 0L }) { item ->
                        BugReportItemCard(
                            item = item,
                            actionInProgress = actionInProgressId == item.id,
                            onUpdateStatus = { newStatus ->
                                item.id?.let { id ->
                                    actionInProgressId = id
                                    coroutineScope.launch {
                                        val res = SupabaseService.updateBugReportStatus(id, newStatus)
                                        actionInProgressId = null
                                        if (res.isSuccess) {
                                            refreshBugReports()
                                        } else {
                                            Toast.makeText(context, "Error updating status: ${res.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            },
                            onSaveComment = { comment ->
                                item.id?.let { id ->
                                    actionInProgressId = id
                                    coroutineScope.launch {
                                        val res = SupabaseService.updateBugReportComment(id, comment)
                                        actionInProgressId = null
                                        if (res.isSuccess) {
                                            refreshBugReports()
                                        } else {
                                            Toast.makeText(context, "Error saving comment: ${res.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            },
                            onDelete = {
                                item.id?.let { id ->
                                    actionInProgressId = id
                                    coroutineScope.launch {
                                        val res = SupabaseService.deleteBugReport(id)
                                        actionInProgressId = null
                                        if (res.isSuccess) {
                                            refreshBugReports()
                                        } else {
                                            Toast.makeText(context, "Error deleting bug report: ${res.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BugReportItemCard(
    item: BugReportRow,
    actionInProgress: Boolean,
    onUpdateStatus: (String) -> Unit,
    onSaveComment: (String) -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val statusColor = when (item.status.lowercase()) {
                    "fixed" -> Color(0xFF34C759)
                    "investigating" -> Color(0xFF007AFF)
                    "closed" -> Color(0xFFFF3B30)
                    else -> Color(0xFFFF9500)
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(statusColor.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = item.status.uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = statusColor
                    )
                }

                IconButton(
                    onClick = onDelete,
                    enabled = !actionInProgress
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Bug Report",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            Text(
                text = "Reporter: ${item.user_name}" + if (!item.insta_id.isNullOrBlank()) " (${item.insta_id})" else "",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )

            Text(
                text = item.description,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            item.device_info?.let { info ->
                Text(
                    text = "Device: $info",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item.created_at?.let { date ->
                val cleanDate = date.substringBefore("T")
                Text(
                    text = "Reported: $cleanDate",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (actionInProgress) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = { onUpdateStatus("investigating") },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF2196F3))
                    ) {
                        Text("Investigate")
                    }

                    TextButton(
                        onClick = { onUpdateStatus("fixed") },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF4CAF50))
                    ) {
                        Text("Fix")
                    }

                    TextButton(
                        onClick = { onUpdateStatus("closed") },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFFF9800))
                    ) {
                        Text("Close")
                    }

                    val context = LocalContext.current
                    TextButton(
                        onClick = {
                            GitHubIssueService.openPrefilledIssueInBrowser(
                                context = context,
                                title = item.description.lines().firstOrNull()?.take(60) ?: "Bug Report",
                                description = item.description,
                                reporterName = item.user_name,
                                instaId = item.insta_id,
                                deviceInfo = item.device_info,
                                isBug = true
                            )
                        }
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.github),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("GitHub")
                    }
                }
            }

            AdminCommentEditor(
                savedComment = item.admin_comment,
                enabled = !actionInProgress,
                onSave = onSaveComment
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TestWrappedTab(navController: NavController, bottomPadding: androidx.compose.ui.unit.Dp = 0.dp) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val coroutineScope = rememberCoroutineScope()
    val wrappedManager = remember { provideWrappedManager(context) }

    val intervalOptions = listOf(
        3 to "3 Days",
        7 to "7 Days (Weekly)",
        14 to "14 Days",
        30 to "30 Days (Monthly)",
        60 to "60 Days",
        90 to "90 Days",
        365 to "365 Days (Yearly)",
        0 to "All Time"
    )

    var selectedDays by remember { mutableStateOf(30) }
    var isPreparing by remember { mutableStateOf(false) }

    val fromTimestamp = remember(selectedDays) {
        if (selectedDays > 0) {
            System.currentTimeMillis() - (selectedDays.toLong() * 24L * 60L * 60L * 1000L)
        } else {
            0L
        }
    }

    val mostPlayedSongs by database.mostPlayedSongsStats(fromTimestamp, limit = 5).collectAsState(initial = emptyList())
    val mostPlayedArtists by database.mostPlayedArtists(fromTimestamp, limit = 3).collectAsState(initial = emptyList())
    val totalPlayTimeMs by database.getTotalPlayTimeInRange(fromTimestamp, System.currentTimeMillis()).collectAsState(initial = 0L)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 16.dp + bottomPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "🎉 Admin Wrapped Testing Suite",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Generate and preview Spotify-style Wrapped stories from your actual listening history across custom time windows.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Text(
            text = "Select Time Range to Test:",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )

        // Interval chips
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            intervalOptions.forEach { (days, label) ->
                FilterChip(
                    selected = selectedDays == days,
                    onClick = { selectedDays = days },
                    label = { Text(label) }
                )
            }
        }

        // Live stats preview card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "📊 Admin Listening Stats (${intervalOptions.find { it.first == selectedDays }?.second}):",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                val totalMinutes = (totalPlayTimeMs ?: 0L) / 1000 / 60
                val totalHours = totalMinutes / 60
                val remMinutes = totalMinutes % 60
                val timeString = if (totalHours > 0) "${totalHours}h ${remMinutes}m" else "${totalMinutes} mins"

                Text(
                    text = "• Total Listening Time: $timeString",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "• Top Song: ${mostPlayedSongs.firstOrNull()?.title ?: "None logged yet"}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "• Top Artist: ${mostPlayedArtists.firstOrNull()?.title ?: "None logged yet"}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "• Unique Songs Played: ${mostPlayedSongs.size}+",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        // Action Buttons
        Button(
            onClick = {
                coroutineScope.launch {
                    isPreparing = true
                    try {
                        wrappedManager.prepare(forceRefresh = true, days = selectedDays)
                        navController.navigate("wrapped")
                    } catch (e: Exception) {
                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    } finally {
                        isPreparing = false
                    }
                }
            },
            enabled = !isPreparing,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (isPreparing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(8.dp))
                Text("Generating Wrapped...")
            } else {
                Text("🚀 Launch & Test Interactive Wrapped")
            }
        }

        OutlinedButton(
            onClick = {
                coroutineScope.launch {
                    context.dataStore.edit {
                        it[ShowWrappedCardKey] = true
                        it[WrappedSeenKey] = false
                        it[WrappedIntervalDaysKey] = if (selectedDays > 0) selectedDays else 30
                    }
                    Toast.makeText(context, "Wrapped card forced visible on Home Screen!", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("📌 Force Show Wrapped Card on Home Screen")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DjMashupStudioTab(bottomPadding: androidx.compose.ui.unit.Dp = 0.dp) {
    val context = LocalContext.current
    var isMashupEnabled by remember { mutableStateOf(true) }
    var mixGranularity by remember { mutableStateOf("Verse-by-Verse (4-8 Bars)") }
    var maxTracks by remember { mutableIntStateOf(4) }
    var stemMode by remember { mutableStateOf("Vocal Swap Over Synced Beat") }
    var keyMatchTolerance by remember { mutableStateOf("Harmonic Key (Camelot ±1)") }

    var isSimulating by remember { mutableStateOf(false) }
    var simulatedCurrentTrack by remember { mutableStateOf("Track 1 - Line 1: 'Pulse Wave Harmonic'") }
    var simulationBeatBpm by remember { mutableIntStateOf(128) }

    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 16.dp + bottomPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "🎛️ DJ Console — Live Twin-Deck & Transition Engine",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "Real-time AI mixing and synchronization engine that weaves 2 to 4 tracks line-by-line or verse-by-verse over an unbroken, synchronized beat grid and harmonic key.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                )
            }
        }

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Dynamic Mashup Engine (Live)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text("Multi-track real-time beat matching & phrase alignment", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = isMashupEnabled, onCheckedChange = { isMashupEnabled = it })
                }
            }
        }

        Text("Mashup Granularity & Shuffling", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        val granularities = listOf("Line-by-Line (Dynamic 10s)", "Verse-by-Verse (4-8 Bars 20s)", "Acapella Hook Swap (14s)")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            granularities.forEach { option ->
                FilterChip(
                    selected = mixGranularity == option,
                    onClick = { mixGranularity = option },
                    label = { Text(option) }
                )
            }
        }

        Text("Max Concurrently Synced Tracks ($maxTracks tracks)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Slider(
            value = maxTracks.toFloat(),
            onValueChange = { maxTracks = it.toInt() },
            valueRange = 2f..4f,
            steps = 1
        )

        Text("Audio Stem Isolation Mode", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        val stemModes = listOf("Vocal Swap Over Synced Beat", "Full Track (BPM Snapped)", "Dual Stem (Vocals + Bass)")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            stemModes.forEach { mode ->
                FilterChip(
                    selected = stemMode == mode,
                    onClick = { stemMode = mode },
                    label = { Text(mode) }
                )
            }
        }

        Text("Harmonic Key Compatibility", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        val keyModes = listOf("Harmonic Key (Camelot ±1)", "Exact Key Match", "Auto Pitch-Shift (±2 semitones)")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            keyModes.forEach { keyOpt ->
                FilterChip(
                    selected = keyMatchTolerance == keyOpt,
                    onClick = { keyMatchTolerance = keyOpt },
                    label = { Text(keyOpt) }
                )
            }
        }

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "⚡ Real-time Dynamic Mashup Simulator",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Simulating: $maxTracks Tracks @ $simulationBeatBpm BPM (Continuous Master Beat Grid)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (isSimulating) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(
                        text = "▶ Currently Playing: $simulatedCurrentTrack",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    Text(
                        text = "Beat Grid: Synchronized ($simulationBeatBpm BPM) • Dynamic Swap Buffer: 10ms",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Button(
                    onClick = {
                        if (!isSimulating) {
                            isSimulating = true
                            coroutineScope.launch {
                                simulatedCurrentTrack = "Track 1 - Line 1: 'Club beats in the midnight neon...'"
                                kotlinx.coroutines.delay(2000)
                                simulatedCurrentTrack = "Track 2 - Verse 1: 'Bassline drops, rhythm takes over...'"
                                kotlinx.coroutines.delay(2000)
                                simulatedCurrentTrack = "Track 3 - Hook: 'Singing with the crowd, harmonic snap...'"
                                kotlinx.coroutines.delay(2000)
                                simulatedCurrentTrack = "Seamless Beat Drop: Synchronized Outro ➔ Track 1 Hook"
                                kotlinx.coroutines.delay(1800)
                                isSimulating = false
                            }
                        }
                    },
                    enabled = !isSimulating,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(if (isSimulating) "Mixing Tracks in Real-Time..." else "▶ Run Live Mashup Simulation")
                }
            }
        }
    }
}




