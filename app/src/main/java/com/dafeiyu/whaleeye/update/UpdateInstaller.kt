package com.dafeiyu.whaleeye.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

object UpdateInstaller {

    fun canInstall(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= 26) context.packageManager.canRequestPackageInstalls() else true
    }

    fun openInstallSettings(context: Context) {
        try {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:" + context.packageName)
            )
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
        }
    }

    fun install(context: Context, apk: File): Boolean {
        return try {
            val uri = FileProvider.getUriForFile(
                context,
                context.packageName + ".fileprovider",
                apk
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }
}