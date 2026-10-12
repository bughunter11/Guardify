package com.bughunter11.guardify.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.provider.Telephony
import android.telecom.TelecomManager

/**
 * Roaming mode: bahar jao to ek switch me sirf zaroori apps ko mobile data.
 *
 * - Sirf mobile data par lagta hai, WiFi ko kabhi nahi chhuta.
 * - Do tareeqe se on hota hai: manual switch, ya "auto" (network sach me roaming par ho tab).
 * - "Zaroori apps" tumhari chuni hui list hai. List khali ho to phone se live suggest hoti hai
 *   (default dialer, default SMS, maps aur kuch jaani-pehchani travel apps), isliye ek switch
 *   dabate hi bina kuch set kiye kaam karta hai.
 * - Naye install hui app bhi apne aap block ho jati hai, kyunki block-list "sab apps minus zaroori"
 *   se roz taza banti hai.
 */
object RoamingMode {

    /**
     * Sirf seed. Jo phone me installed hain wahi suggest hote hain. Asli suggestion default apps
     * aur Maps category se aati hai, ye list uske upar thoda sa extra deti hai.
     */
    private val KNOWN_TRAVEL = setOf(
        "com.whatsapp",
        "com.whatsapp.w4b",
        "org.telegram.messenger",
        "org.thoughtcrime.securesms",
        "com.google.android.apps.maps",
        "com.google.android.apps.translate",
        "com.google.android.apps.nbu.paisa.user",
        "com.phonepe.app",
        "net.one97.paytm",
        "com.ubercab",
        "com.olacabs.customer",
    )

    /** Capabilities se: cellular hai aur NOT_ROAMING nahi hai = abhi roaming par hain. */
    fun isRoaming(caps: NetworkCapabilities): Boolean =
        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) &&
            !caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
            !caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) &&
            !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING)

    /** Abhi ka active network roaming par hai? (service ke bahar, tile / alarm ke liye) */
    fun isNetworkRoaming(ctx: Context): Boolean {
        val cm = ctx.applicationContext.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) } ?: return false
        return isRoaming(caps)
    }

    /** Mode abhi lagu hona chahiye? manual switch on, ya auto on aur network roaming par. */
    fun isActive(prefs: Prefs, networkRoaming: Boolean): Boolean =
        prefs.roamOn() || (prefs.roamAuto() && networkRoaming)

    /**
     * Phone se live suggestion. Blocking hai (TelecomManager), background thread par chalao.
     * Sirf installed apps wapas aati hain.
     */
    fun suggest(ctx: Context, apps: List<AppInfo>): Set<String> {
        val installed = apps.mapTo(HashSet()) { it.pkg }
        val out = LinkedHashSet<String>()
        runCatching { ctx.getSystemService(TelecomManager::class.java)?.defaultDialerPackage }
            .getOrNull()?.let { out += it }
        runCatching { Telephony.Sms.getDefaultSmsPackage(ctx) }.getOrNull()?.let { out += it }
        apps.filterTo(ArrayList()) { !it.isSystem && it.category == ApplicationInfo.CATEGORY_MAPS }
            .forEach { out += it.pkg }
        KNOWN_TRAVEL.filterTo(out) { it in installed }
        return out.filterTo(LinkedHashSet()) { it in installed }
    }

    /** Jo apps online rahengi: tumhari chuni hui, warna live suggestion. */
    fun essentials(prefs: Prefs, suggested: Set<String>): Set<String> =
        prefs.roamEssential().ifEmpty { suggested }

    /**
     * Roaming me mobile par kaun si apps band hongi. Service, alerts aur UI teeno yahi dekhte hain.
     * System apps tab tak bahar rehti hain jab tak "Include system apps" on na ho.
     */
    fun blocked(apps: List<AppInfo>?, prefs: Prefs, suggested: Set<String>): Set<String> {
        if (apps == null) return emptySet()
        val keep = essentials(prefs, suggested)
        val system = prefs.roamSystem()
        return apps.filter { (system || !it.isSystem) && it.pkg !in keep }.mapTo(HashSet()) { it.pkg }
    }
}
