package com.bughunter11.guardify.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager

object AppRepository {
    fun load(ctx: Context): List<AppInfo> {
        val pm = ctx.packageManager
        // flags = 0: GET_META_DATA ki zaroorat nahi, usse load dheema hota hai
        return pm.getInstalledApplications(0)
            .asSequence()
            .filter { it.packageName != ctx.packageName }
            .map {
                val isSystem = (it.flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
                    (it.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0
                val label = runCatching { it.loadLabel(pm).toString() }.getOrNull()
                    ?.takeIf { l -> l.isNotBlank() } ?: it.packageName
                AppInfo(it.packageName, label, it.uid, isSystem, it.category)
            }
            .toList()
    }
}
