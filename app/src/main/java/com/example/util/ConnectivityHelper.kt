package com.example.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.wifi.WifiManager

object ConnectivityHelper {

    const val CLAUDE_URL = "https://claude.ai"

    fun isWifiEnabled(context: Context): Boolean {
        return runCatching {
            val wifiManager =
                context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            wifiManager?.isWifiEnabled == true
        }.getOrDefault(false)
    }

    fun isWifiConnected(context: Context): Boolean {
        return runCatching {
            val cm =
                context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                    ?: return false
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        }.getOrDefault(false)
    }

    fun hasValidatedInternet(context: Context): Boolean {
        return runCatching {
            val cm =
                context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                    ?: return false
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            val hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            val isValidated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            hasInternet && isValidated
        }.getOrDefault(false)
    }

    fun resolveDefaultBrowserLabel(context: Context): String? {
        return runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(CLAUDE_URL)).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
            }
            val pm = context.packageManager
            val resolveInfo = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
                ?: return null
            val pkg = resolveInfo.activityInfo?.packageName ?: return null
            if (pkg == context.packageName || pkg == "android") return null
            val appLabel = resolveInfo.loadLabel(pm)?.toString()?.takeIf { it.isNotBlank() }
            if (appLabel != null) "$appLabel ($pkg)" else pkg
        }.getOrNull()
    }

    fun buildOpenClaudeIntent(context: Context): Intent {
        return Intent(Intent.ACTION_VIEW, Uri.parse(CLAUDE_URL)).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val resolved = context.packageManager.resolveActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(CLAUDE_URL)).apply {
                    addCategory(Intent.CATEGORY_BROWSABLE)
                },
                PackageManager.MATCH_DEFAULT_ONLY,
            )
            val pkg = resolved?.activityInfo?.packageName
            if (!pkg.isNullOrBlank() && pkg != context.packageName && pkg != "android") {
                setPackage(pkg)
            }
        }
    }
}
