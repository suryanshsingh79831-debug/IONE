package com.jarvis.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.drawable.Drawable
import java.util.Locale

data class AppItem(
    val label: String,
    val pkg: String,
    val activity: String,
    val icon: Drawable,
    val cat: String
)

object Apps {
    val TABS = listOf("ALL", "FAVORITES", "GAMES", "SOCIAL", "MEDIA", "SHOP & PAY", "WORK", "UTILITIES", "SYSTEM")

    // Order matters: the first category whose keyword appears in "package + name" wins.
    private val KEYS: List<Pair<String, List<String>>> = listOf(
        "GAMES" to listOf(
            "supercell", "activision", "pubg", "freefire", "garena", "krafton", "roblox", "minecraft",
            "mojang", "epicgames", "riotgames", "gameloft", "candy", "ludo", "chess", "miniclip",
            "nianticlabs", "playrix", "rovio", "bgmi", "carrom", "subway", "temple run", "clash",
            "call of duty", "genshin", "mobile legends", "among us", "hill climb", "asphalt"
        ),
        "SOCIAL" to listOf(
            "whatsapp", "instagram", "facebook", "telegram", "snapchat", "twitter", "linkedin",
            "reddit", "discord", "tiktok", "pinterest", "sharechat", "signal", "threads",
            "messenger", "tinder", "bumble", "quora", "koo"
        ),
        "MEDIA" to listOf(
            "youtube", "spotify", "netflix", "hotstar", "jiocinema", "primevideo", "avod", "gaana",
            "wynk", "vlc", "mxtech", "soundcloud", "music", "video", "photos", "gallery", "camera",
            "audio", "player", "podcast", "radio", "movies", "tv"
        ),
        "SHOP & PAY" to listOf(
            "mshop", "amazon", "flipkart", "myntra", "meesho", "swiggy", "zomato", "blinkit", "zepto",
            "paytm", "phonepe", "paisa", "bhim", "gpay", "bank", "wallet", "olacabs", "uber", "rapido",
            "irctc", "makemytrip", "bigbasket", "nykaa", "ajio", "payments", "pay"
        ),
        "WORK" to listOf(
            "docs", "sheets", "slides", "office", "word", "excel", "powerpoint", "drive", "gmail",
            "mail", "outlook", "zoom", "meet", "teams", "slack", "notion", "calendar", "notes",
            "keep", "pdf", "adobe", "onedrive", "dropbox", "scanner"
        )
    )

    @Suppress("DEPRECATION")
    private fun categorize(ai: ApplicationInfo, label: String): String {
        val isGame = ai.category == ApplicationInfo.CATEGORY_GAME ||
            (ai.flags and ApplicationInfo.FLAG_IS_GAME) != 0
        if (isGame) return "GAMES"

        val s = (ai.packageName + " " + label).lowercase(Locale.ROOT)
        for ((cat, words) in KEYS) {
            if (words.any { s.contains(it) }) return cat
        }

        when (ai.category) {
            ApplicationInfo.CATEGORY_SOCIAL -> return "SOCIAL"
            ApplicationInfo.CATEGORY_AUDIO,
            ApplicationInfo.CATEGORY_VIDEO,
            ApplicationInfo.CATEGORY_IMAGE -> return "MEDIA"
            ApplicationInfo.CATEGORY_PRODUCTIVITY -> return "WORK"
        }

        val isSystem = (ai.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0
        return if (isSystem) "SYSTEM" else "UTILITIES"
    }

    fun load(ctx: Context): List<AppItem> {
        val pm = ctx.packageManager
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val out = ArrayList<AppItem>()
        for (ri in pm.queryIntentActivities(main, 0)) {
            val ai = ri.activityInfo
            if (ai.packageName == ctx.packageName) continue
            val label = ri.loadLabel(pm).toString()
            out.add(AppItem(label, ai.packageName, ai.name, ri.loadIcon(pm), categorize(ai.applicationInfo, label)))
        }
        out.sortBy { it.label.lowercase(Locale.ROOT) }
        return out
    }

    fun launch(ctx: Context, a: AppItem) {
        try {
            ctx.startActivity(
                Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LAUNCHER)
                    .setClassName(a.pkg, a.activity)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            )
        } catch (e: Exception) {
            // app was just removed or cannot be opened
        }
    }
}

object Favs {
    private fun prefs(c: Context) = c.getSharedPreferences("favs", Context.MODE_PRIVATE)

    fun get(c: Context): Set<String> =
        HashSet(prefs(c).getStringSet("f", emptySet()) ?: emptySet())

    fun toggle(c: Context, pkg: String) {
        val s = HashSet(get(c))
        if (!s.add(pkg)) s.remove(pkg)
        prefs(c).edit().putStringSet("f", s).apply()
    }
}
