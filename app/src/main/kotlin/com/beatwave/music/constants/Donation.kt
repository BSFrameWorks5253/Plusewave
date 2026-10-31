/**
 * Pulse Wave Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.beatwave.music.constants

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.beatwave.music.BuildConfig
import java.net.URLEncoder
import java.util.Locale

/**
 * Primary UPI ID for user donations & project sponsorship.
 * Sourced from BuildConfig (generated from .env / local.properties / env var) with
 * user default "burhanuddin08@fam".
 */
val DonationUpiId: String
    get() = try {
        BuildConfig.DONATION_UPI_ID.takeIf { it.isNotBlank() } ?: "burhanuddin08@fam"
    } catch (_: Throwable) {
        "burhanuddin08@fam"
    }

/**
 * Payee name registered with UPI for Pulse Wave.
 */
val DonationPayeeName: String
    get() = try {
        BuildConfig.DONATION_PAYEE_NAME.takeIf { it.isNotBlank() } ?: "PulseWave"
    } catch (_: Throwable) {
        "PulseWave"
    }

/**
 * Builds NPCI-compliant UPI payment URI with optional amount and note.
 * Schema: upi://pay?pa=<upi_id>&pn=<payee_name>&tn=<note>&tr=<ref_id>&cu=INR[&am=<amount_two_decimals>]
 */
fun getDonationUpiUriWithAmount(amountInr: Float? = null, note: String = "Support PulseWave"): String {
    val upiId = DonationUpiId
    val payee = try {
        URLEncoder.encode(DonationPayeeName, "UTF-8")
    } catch (_: Throwable) {
        "PulseWave"
    }
    val encodedNote = try {
        URLEncoder.encode(note, "UTF-8")
    } catch (_: Throwable) {
        "SupportPulseWave"
    }
    val refId = System.currentTimeMillis().toString()

    val builder = StringBuilder("upi://pay?")
    builder.append("pa=").append(upiId)
    builder.append("&pn=").append(payee)
    builder.append("&tn=").append(encodedNote)
    builder.append("&tr=").append(refId)
    builder.append("&cu=INR")
    if (amountInr != null && amountInr > 0f) {
        builder.append("&am=").append(String.format(Locale.US, "%.2f", amountInr))
    }
    return builder.toString()
}

/**
 * Convenience overload for Int amounts.
 */
fun getDonationUpiUriWithAmount(amountInr: Int?): String {
    return getDonationUpiUriWithAmount(amountInr?.toFloat())
}

/**
 * Base UPI payment URI for Pulse Wave donations.
 */
val DonationUpiUri: String
    get() = getDonationUpiUriWithAmount()

/**
 * Launches UPI payment cleanly in an external UPI app (FamPay, PhonePe, Google Pay, Paytm, BHIM, Cred, etc.)
 * using Intent.createChooser and FLAG_ACTIVITY_NEW_TASK so target apps do not crash / force-quit on launch.
 * Fallbacks safely to copying the UPI ID to clipboard if no UPI app is installed or launch fails.
 */
fun launchUpiPayment(
    context: Context,
    amountInr: Float? = null,
    note: String = "Support PulseWave"
) {
    val upiUriString = getDonationUpiUriWithAmount(amountInr, note)
    val uri = Uri.parse(upiUriString)
    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val chooser = Intent.createChooser(intent, "Pay via UPI App").apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(chooser)
    } catch (e: Exception) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = ClipData.newPlainText("UPI ID", DonationUpiId)
        clipboard?.setPrimaryClip(clip)
        Toast.makeText(
            context,
            "UPI ID ($DonationUpiId) copied to clipboard!",
            Toast.LENGTH_LONG
        ).show()
    }
}
