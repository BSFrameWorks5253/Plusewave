/**
 * Pulse Wave Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.beatwave.music.constants

import com.beatwave.music.BuildConfig
import java.net.URLEncoder

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
 * Builds standard UPI payment URI with optional amount.
 * Schema: upi://pay?pa=<upi_id>&pn=<payee_name>&cu=INR[&am=<amount>]
 */
fun getDonationUpiUriWithAmount(amountInr: Int? = null): String {
    val upiId = DonationUpiId
    val payee = try {
        URLEncoder.encode(DonationPayeeName, "UTF-8")
    } catch (_: Throwable) {
        "PulseWave"
    }
    val base = "upi://pay?pa=$upiId&pn=$payee&cu=INR"
    return if (amountInr != null && amountInr > 0) {
        "$base&am=$amountInr"
    } else {
        base
    }
}

/**
 * Base UPI payment URI for Pulse Wave donations.
 */
val DonationUpiUri: String
    get() = getDonationUpiUriWithAmount()
