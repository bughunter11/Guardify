package com.bughunter11.guardify.data

import android.content.pm.ApplicationInfo

data class AppInfo(
    val pkg: String,
    val label: String,
    val uid: Int,
    val isSystem: Boolean,
    val category: Int = -1,
)

/** App list ki sorting. Ordinal Prefs me save hota hai, isliye order mat badalna. */
enum class SortMode(val label: String) {
    TOTAL("Most data"),
    WIFI("Most WiFi"),
    MOBILE("Most mobile"),
    NAME("A to Z"),
}

class Usage(
    private val wifi: Map<Int, Long>,
    private val mobile: Map<Int, Long>,
) {
    fun wifi(uid: Int): Long = wifi[uid] ?: 0L
    fun mobile(uid: Int): Long = mobile[uid] ?: 0L
    fun total(uid: Int): Long = wifi(uid) + mobile(uid)

    companion object {
        val EMPTY = Usage(emptyMap(), emptyMap())
    }
}

/**
 * Android har app ko uski developer ki batayi category deta hai (Social, Games...).
 * Jo app ne declare nahi ki wo kisi category me nahi aati. ALL = koi filter nahi.
 */
object AppCategories {
    const val ALL = -2

    val all: List<Pair<Int, String>> = listOf(
        ApplicationInfo.CATEGORY_SOCIAL to "Social",
        ApplicationInfo.CATEGORY_GAME to "Games",
        ApplicationInfo.CATEGORY_VIDEO to "Video",
        ApplicationInfo.CATEGORY_AUDIO to "Music and audio",
        ApplicationInfo.CATEGORY_IMAGE to "Photos",
        ApplicationInfo.CATEGORY_NEWS to "News",
        ApplicationInfo.CATEGORY_MAPS to "Maps",
        ApplicationInfo.CATEGORY_PRODUCTIVITY to "Productivity",
    )
}
