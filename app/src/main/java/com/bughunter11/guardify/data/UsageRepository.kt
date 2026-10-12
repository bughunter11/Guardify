package com.bughunter11.guardify.data

import android.app.AppOpsManager
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.net.ConnectivityManager
import android.os.Process
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

object UsageRepository {

    fun hasAccess(ctx: Context): Boolean = runCatching {
        val ops = ctx.getSystemService(AppOpsManager::class.java)
        val mode = ops.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            ctx.packageName,
        )
        mode == AppOpsManager.MODE_ALLOWED
    }.getOrDefault(false)

    /** WiFi aur mobile ki query ek saath (parallel) chalti hai, background thread par. */
    @Suppress("DEPRECATION") // NetworkStatsManager abhi bhi TYPE_WIFI / TYPE_MOBILE hi maangta hai
    suspend fun query(ctx: Context, days: Int): Usage = withContext(Dispatchers.IO) {
        val nsm = ctx.getSystemService(NetworkStatsManager::class.java)
        val end = System.currentTimeMillis()
        val start = end - days * 24L * 60 * 60 * 1000
        coroutineScope {
            val wifi = async { sum(nsm, ConnectivityManager.TYPE_WIFI, start, end) }
            val mobile = async { sum(nsm, ConnectivityManager.TYPE_MOBILE, start, end) }
            Usage(wifi = wifi.await(), mobile = mobile.await())
        }
    }

    /** Billing cycle ke start se ab tak ka mobile data, uid ke hisaab se (limits ke liye). */
    suspend fun mobileSince(ctx: Context, start: Long): Map<Int, Long> =
        withContext(Dispatchers.IO) { mobileSinceBlocking(ctx, start) }

    @Suppress("DEPRECATION")
    fun mobileSinceBlocking(ctx: Context, start: Long): Map<Int, Long> {
        val nsm = ctx.getSystemService(NetworkStatsManager::class.java)
        return sum(nsm, ConnectivityManager.TYPE_MOBILE, start, System.currentTimeMillis())
    }

    private fun sum(nsm: NetworkStatsManager, type: Int, start: Long, end: Long): Map<Int, Long> {
        val map = HashMap<Int, Long>()
        val stats = try {
            nsm.querySummary(type, null, start, end)
        } catch (e: Exception) {
            return map
        }
        try {
            val bucket = NetworkStats.Bucket()
            while (stats.hasNextBucket()) {
                stats.getNextBucket(bucket)
                map[bucket.uid] = (map[bucket.uid] ?: 0L) + bucket.rxBytes + bucket.txBytes
            }
        } catch (e: Exception) {
            // adhura data bhi chalega, crash nahi
        } finally {
            runCatching { stats.close() }
        }
        return map
    }
}
