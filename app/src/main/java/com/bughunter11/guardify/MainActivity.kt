package com.bughunter11.guardify

import android.content.ComponentName
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.service.quicksettings.TileService
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import com.bughunter11.guardify.data.Prefs
import com.bughunter11.guardify.data.QuickActions
import com.bughunter11.guardify.ui.GuardifyTheme
import com.bughunter11.guardify.ui.MainScreen
import com.bughunter11.guardify.ui.MainViewModel

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    // Tile / widget se aaya kaam, VPN permission milte hi apne aap hoga
    private var pendingQuick: String? = null

    private val vpnPermission =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == RESULT_OK) startFirewall() else pendingQuick = null
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val systemDark = isSystemInDarkTheme()
            val dark = when (vm.themeMode) {
                1 -> false
                2 -> true
                else -> systemDark
            }

            // Status aur nav bar transparent rehte hain (app ka rang peeche se dikhta hai),
            // sirf icons ka rang app ke theme ke hisaab se badalta hai.
            DisposableEffect(dark) {
                this@MainActivity.enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT,
                    ) { dark },
                    navigationBarStyle = SystemBarStyle.auto(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT,
                    ) { dark },
                )
                onDispose { }
            }

            GuardifyTheme(darkTheme = dark, accent = vm.accent, amoled = vm.amoled) {
                MainScreen(
                    vm = vm,
                    onToggleFirewall = ::toggleFirewall,
                    onGrantUsage = {
                        runCatching { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
                    },
                )
            }
        }

        // Data-guzzler alarm app khulte hi taza (firewall band ho tab bhi alert chalte rahein)
        if (Prefs(this).guzzler()) DataWatchReceiver.schedule(applicationContext)
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /** Tile long-press = Quick controls sheet. Tile / widget ka adhura kaam = permission lo, phir karo. */
    private fun handleIntent(i: Intent?) {
        i ?: return
        if (i.action == TileService.ACTION_QS_TILE_PREFERENCES) {
            // Roaming tile par long-press = Roaming sheet, baaki tiles = Quick controls
            val tile = tileComponent(i)
            vm.openSheet = if (tile?.className == RoamTileService::class.java.name) {
                RoamAlerts.SHEET_ROAMING
            } else {
                SHEET_QUICK
            }
            return
        }
        // Roaming notification tap: seedha Roaming sheet
        val sheet = i.getIntExtra(RoamAlerts.EXTRA_SHEET, 0)
        if (sheet != 0) {
            i.removeExtra(RoamAlerts.EXTRA_SHEET) // rotate / dobara khulne par phir se na khule
            vm.openSheet = sheet
            return
        }
        val quick = i.getStringExtra(QuickActions.EXTRA) ?: return
        i.removeExtra(QuickActions.EXTRA) // rotate / dobara khulne par phir se na chale
        pendingQuick = quick
        toggleFirewall(true)
    }

    /** Long-press hui tile ka component (system ye extra bhejta hai). Na mile to null. */
    @Suppress("DEPRECATION")
    private fun tileComponent(i: Intent): ComponentName? = runCatching {
        if (Build.VERSION.SDK_INT >= 33) {
            i.getParcelableExtra(TILE_COMPONENT_EXTRA, ComponentName::class.java)
        } else {
            i.getParcelableExtra<ComponentName>(TILE_COMPONENT_EXTRA)
        }
    }.getOrNull()

    // Screen dikh rahi ho tabhi usage live refresh hota hai, battery bachti hai
    override fun onResume() {
        super.onResume()
        vm.startLive()
    }

    override fun onPause() {
        vm.stopLive()
        super.onPause()
    }

    private fun toggleFirewall(on: Boolean) {
        if (on) {
            val intent = VpnService.prepare(this)
            if (intent != null) {
                runCatching { vpnPermission.launch(intent) }
            } else {
                startFirewall()
            }
        } else {
            // Firewall band = "sab mobile band" bhi band, warna agli baar on karte hi achanak lag jata
            Prefs(this).setKillMobile(false)
            Prefs(this).setRoamOn(false) // roaming ka manual switch bhi, warna agli baar achanak lagta
            vm.syncQuick()
            stopService(Intent(this, FirewallVpnService::class.java))
        }
    }

    private fun startFirewall() {
        runCatching { startService(Intent(this, FirewallVpnService::class.java)) }
        val quick = pendingQuick ?: return
        pendingQuick = null
        val app = applicationContext
        // Focus ke liye apps ki list load hoti hai, isliye alag thread par
        Thread {
            QuickActions.perform(app, quick)
            runOnUiThread {
                vm.syncQuick()
                vm.syncFocus()
            }
        }.start()
    }

    private companion object {
        const val SHEET_QUICK = 4

        // Intent.EXTRA_COMPONENT_NAME ki value. QS_TILE_PREFERENCES ke saath system tile ka naam bhejta hai.
        const val TILE_COMPONENT_EXTRA = "android.intent.extra.COMPONENT_NAME"
    }
}
