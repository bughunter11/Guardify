package com.bughunter11.guardify.data

/**
 * Focus mode: chune hue apps WiFi aur mobile dono par kuch der ke liye block.
 * Timer khatam hote hi sirf wahi apps wapas khulte hain jo focus ne block kiye the,
 * to tumhare apne purane rules aur beech me kiye badlav safe rehte hain.
 * Ye Prefs par chalta hai, isliye app band ho tab bhi service isse khatam kar sakti hai.
 */
object FocusMode {

    fun start(prefs: Prefs, pkgs: Collection<String>, untilMs: Long) {
        end(prefs) // pehle se chal raha ho to usse sahi se khatam karo
        val wifi = prefs.wifi()
        val mobile = prefs.mobile()
        val addedWifi = pkgs.filter { it !in wifi }.toSet()
        val addedMobile = pkgs.filter { it !in mobile }.toSet()
        prefs.setRules(wifi + pkgs, mobile + pkgs)
        prefs.setFocus(untilMs, addedWifi, addedMobile)
    }

    fun end(prefs: Prefs) {
        if (prefs.focusUntil() == 0L) return
        prefs.setRules(
            prefs.wifi() - prefs.focusAddedWifi(),
            prefs.mobile() - prefs.focusAddedMobile(),
        )
        prefs.clearFocus()
    }

    /** Time pura ho gaya ho to focus khatam karo. Rules badle to true. */
    fun endIfExpired(prefs: Prefs, now: Long = System.currentTimeMillis()): Boolean {
        val until = prefs.focusUntil()
        if (until == 0L || now < until) return false
        end(prefs)
        return true
    }
}
