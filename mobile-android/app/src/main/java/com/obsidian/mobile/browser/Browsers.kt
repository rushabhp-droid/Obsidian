package com.obsidian.mobile.browser

import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings

/** The other browsers on the phone, and handing a checked link on to one of them. */
object Browsers {

    data class Browser(val packageName: String, val label: String)

    private val probe = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")).addCategory(Intent.CATEGORY_BROWSABLE)

    fun installed(context: Context): List<Browser> {
        val pm = context.packageManager
        return pm.queryIntentActivities(probe, PackageManager.MATCH_ALL)
            .map { it.activityInfo.packageName }
            .distinct()
            .filter { it != context.packageName }
            .map { Browser(it, runCatching { pm.getApplicationLabel(pm.getApplicationInfo(it, 0)).toString() }.getOrDefault(it)) }
            .sortedBy { it.label.lowercase() }
    }

    fun isDefault(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_BROWSER) == true
        } else {
            context.packageManager.resolveActivity(probe, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName ==
                context.packageName
        }

    /** The system screen that makes Obsidian the default browser (a role request on Android 10+). */
    fun defaultBrowserIntent(context: Context): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.getSystemService(RoleManager::class.java).createRequestRoleIntent(RoleManager.ROLE_BROWSER)
        } else {
            Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
        }

    /** Opens [url] in the user's chosen browser, or the first other browser. False if there is none. */
    fun open(context: Context, url: String, preferred: String?): Boolean {
        val browsers = installed(context)
        val target = browsers.firstOrNull { it.packageName == preferred } ?: browsers.firstOrNull() ?: return false
        return try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addCategory(Intent.CATEGORY_BROWSABLE)
                .setPackage(target.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }
}
