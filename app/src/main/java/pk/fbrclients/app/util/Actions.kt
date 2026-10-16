package pk.fbrclients.app.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/** Opens other apps (dialer, WhatsApp, SMS, email, browser). */
object Actions {
    const val IRIS_URL = "https://iris.fbr.gov.pk"

    fun call(ctx: Context, mobile: String) =
        open(ctx, Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Formats.digitsOnly(mobile))), "phone")

    fun sms(ctx: Context, mobile: String) =
        open(ctx, Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Formats.digitsOnly(mobile))), "SMS")

    fun whatsApp(ctx: Context, mobile: String) {
        val n = Formats.whatsAppNumber(mobile) ?: return toast(ctx, "Mobile number isn't valid")
        open(ctx, Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$n")), "WhatsApp")
    }

    fun email(ctx: Context, address: String) =
        open(ctx, Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + Uri.encode(address.trim()))), "email")

    fun openIris(ctx: Context) =
        open(ctx, Intent(Intent.ACTION_VIEW, Uri.parse(IRIS_URL)), "browser")

    private fun open(ctx: Context, intent: Intent, what: String) {
        try {
            ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            toast(ctx, "No $what app found")
        }
    }

    private fun toast(ctx: Context, msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
}
