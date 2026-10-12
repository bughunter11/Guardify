package com.bughunter11.guardify.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

object Links {
    const val CHANNEL = "https://t.me/Zyron_Official_26"
    const val GROUP = "https://t.me/Zyron_Community_26"

    /** GitHub repo ka link yaha daalo. Khali rahe to Settings me "Source code" row dikhti hi nahi. */
    const val GITHUB = "https://github.com/bughunter11/Guardify"
}

/** Telegram app ho to wahan khulta hai, warna browser me. */
fun openLink(ctx: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { ctx.startActivity(intent) }.onFailure {
        Toast.makeText(ctx, "Couldn't open the link", Toast.LENGTH_SHORT).show()
    }
}

object Support {
    const val UPI_ID = "zyron0@ptyes"
    const val NAME = "Raj"
}

/** UPI app chooser kholta hai. Koi UPI app na ho to ID copy ho jati hai. */
fun openUpi(ctx: Context) {
    val uri = Uri.parse("upi://pay?pa=${Support.UPI_ID}&pn=${Uri.encode(Support.NAME)}&cu=INR")
    val intent = Intent.createChooser(Intent(Intent.ACTION_VIEW, uri), "Support with")
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { ctx.startActivity(intent) }.onFailure {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText("UPI", Support.UPI_ID))
        Toast.makeText(ctx, "UPI ID copied: ${Support.UPI_ID}", Toast.LENGTH_LONG).show()
    }
}
