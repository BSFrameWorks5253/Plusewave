package com.beatwave.music.api

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.beatwave.music.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder

@Serializable
data class GitHubIssueResult(
    val id: Long? = null,
    val number: Long? = null,
    val html_url: String? = null,
    val title: String? = null
)

object GitHubIssueService {
    const val GITHUB_REPO_OWNER = "BSFrameworks5253"
    const val GITHUB_REPO_NAME = "Plusewave"
    const val ISSUES_PANEL_URL = "https://github.com/$GITHUB_REPO_OWNER/$GITHUB_REPO_NAME/issues"
    const val NEW_ISSUE_URL = "https://github.com/$GITHUB_REPO_OWNER/$GITHUB_REPO_NAME/issues/new"

    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }
    private val mediaType = "application/json; charset=utf-8".toMediaType()

    /**
     * Checks if a GitHub Issue API token is configured.
     */
    val isApiConfigured: Boolean
        get() = BuildConfig.GITHUB_ISSUE_TOKEN.isNotBlank()

    /**
     * Constructs a prefilled GitHub issue URL with Markdown body, tags, and reporter info.
     */
    fun buildPrefilledIssueUrl(
        title: String,
        description: String,
        reporterName: String?,
        instaId: String? = null,
        deviceInfo: String? = null,
        isBug: Boolean = true
    ): String {
        val typeLabel = if (isBug) "bug" else "enhancement"
        val bodyContent = buildString {
            appendLine("### Reporter")
            appendLine(reporterName?.takeIf { it.isNotBlank() } ?: "Pulse Wave App User")
            if (!instaId.isNullOrBlank()) {
                appendLine()
                appendLine("### Contact (Instagram / Handle)")
                appendLine(instaId)
            }
            appendLine()
            appendLine("### Description")
            appendLine(description)
            if (isBug) {
                appendLine()
                appendLine("### Environment & Device Info")
                if (!deviceInfo.isNullOrBlank()) {
                    appendLine("- Device: $deviceInfo")
                }
                appendLine("- App Version: Pulse Wave v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            }
        }

        val encodedTitle = URLEncoder.encode(title.take(120), "UTF-8")
        val encodedBody = URLEncoder.encode(bodyContent, "UTF-8")
        return "$NEW_ISSUE_URL?title=$encodedTitle&body=$encodedBody&labels=$typeLabel"
    }

    /**
     * Submits an issue directly to GitHub via the GitHub REST API if a token is present.
     */
    suspend fun createGitHubIssue(
        title: String,
        description: String,
        reporterName: String?,
        instaId: String? = null,
        deviceInfo: String? = null,
        isBug: Boolean = true
    ): Result<GitHubIssueResult> = withContext(Dispatchers.IO) {
        val token = BuildConfig.GITHUB_ISSUE_TOKEN
        if (token.isBlank()) {
            return@withContext Result.failure(IllegalStateException("No GITHUB_ISSUE_TOKEN configured"))
        }

        try {
            val typeLabel = if (isBug) "bug" else "enhancement"
            val bodyContent = buildString {
                appendLine("### Reporter")
                appendLine(reporterName?.takeIf { it.isNotBlank() } ?: "Pulse Wave App User")
                if (!instaId.isNullOrBlank()) {
                    appendLine()
                    appendLine("### Contact (Instagram / Handle)")
                    appendLine(instaId)
                }
                appendLine()
                appendLine("### Description")
                appendLine(description)
                if (isBug) {
                    appendLine()
                    appendLine("### Environment & Device Info")
                    if (!deviceInfo.isNullOrBlank()) {
                        appendLine("- Device: $deviceInfo")
                    }
                    appendLine("- App Version: Pulse Wave v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                }
                appendLine()
                appendLine("*Submitted via Pulse Wave in-app feedback portal.*")
            }

            val payload = buildJsonObject {
                put("title", title.take(120))
                put("body", bodyContent)
                putJsonArray("labels") {
                    add(kotlinx.serialization.json.JsonPrimitive(typeLabel))
                    add(kotlinx.serialization.json.JsonPrimitive("in-app-report"))
                }
            }

            val request = Request.Builder()
                .url("https://api.github.com/repos/$GITHUB_REPO_OWNER/$GITHUB_REPO_NAME/issues")
                .post(payload.toString().toRequestBody(mediaType))
                .header("Accept", "application/vnd.github+json")
                .header("Authorization", "Bearer $token")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .build()

            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    val result = json.decodeFromString<GitHubIssueResult>(responseBody)
                    Result.success(result)
                } else {
                    Result.failure(Exception("GitHub API error (${response.code}): $responseBody"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Opens the repository's GitHub Issues panel in a browser or GitHub app.
     */
    fun openIssuesPanel(context: Context) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(ISSUES_PANEL_URL)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {}
    }

    /**
     * Opens a new prefilled issue composer directly on GitHub.
     */
    fun openPrefilledIssueInBrowser(
        context: Context,
        title: String,
        description: String,
        reporterName: String?,
        instaId: String? = null,
        deviceInfo: String? = null,
        isBug: Boolean = true
    ) {
        try {
            val url = buildPrefilledIssueUrl(
                title = title,
                description = description,
                reporterName = reporterName,
                instaId = instaId,
                deviceInfo = deviceInfo,
                isBug = isBug
            )
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {}
    }
}
