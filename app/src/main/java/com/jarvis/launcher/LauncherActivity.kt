package com.jarvis.launcher

import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.GridView
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast

object Ui {
    val BG_TOP = Color.parseColor("#0C1117")
    val BG_BOTTOM = Color.parseColor("#05070A")
    val ACCENT = Color.parseColor("#3DB8E8")
    val TEXT = Color.parseColor("#E6EDF3")
    val DIM = Color.parseColor("#8B98A5")
    val PANEL = Color.parseColor("#CC161D26")
    val LINE = Color.parseColor("#26303B")
    val SCRIM_TOP = Color.parseColor("#66050A10")
    val SCRIM_BOTTOM = Color.parseColor("#CC03060A")
}

class LauncherActivity : Activity() {

    private var all: List<AppItem> = emptyList()
    private val shown = ArrayList<AppItem>()
    private var tab = "ALL"
    private var query = ""
    private val main = Handler(Looper.getMainLooper())

    private lateinit var root: LinearLayout
    private lateinit var grid: GridView
    private lateinit var tabsRow: LinearLayout
    private lateinit var search: EditText
    private lateinit var countText: TextView
    private lateinit var greetText: TextView
    private lateinit var dock: LinearLayout
    private val adapter = AppAdapter()

    private fun dp(v: Float) = v * resources.displayMetrics.density
    private fun dpi(v: Float) = dp(v).toInt()

    private fun lp(w: Int, h: Int, top: Float = 0f, weight: Float = 0f) =
        LinearLayout.LayoutParams(w, h, weight).apply { topMargin = dpi(top) }

    private fun box(fill: Int, stroke: Int, radius: Float) = GradientDrawable().apply {
        setColor(fill)
        setStroke(dpi(1f).coerceAtLeast(1), stroke)
        cornerRadius = dp(radius)
    }

    // ---------------------------------------------------------------- setup

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Ui.SCRIM_TOP, Ui.SCRIM_BOTTOM)
            )
            setPadding(dpi(20f), dpi(12f), dpi(20f), dpi(12f))
        }

        root.addView(buildHeader())
        root.addView(buildSearch(), lp(-1, -2, top = 16f))
        root.addView(buildTabs(), lp(-1, -2, top = 10f))

        grid = GridView(this).apply {
            numColumns = 4
            stretchMode = GridView.STRETCH_COLUMN_WIDTH
            verticalSpacing = dpi(16f)
            selector = ColorDrawable(0)
            overScrollMode = View.OVER_SCROLL_NEVER
            isVerticalScrollBarEnabled = false
            adapter = this@LauncherActivity.adapter
            setOnItemClickListener { _, _, pos, _ ->
                shown.getOrNull(pos)?.let { Apps.launch(this@LauncherActivity, it) }
            }
            setOnItemLongClickListener { _, _, pos, _ ->
                shown.getOrNull(pos)?.let { showMenu(it) }
                true
            }
        }
        root.addView(grid, lp(-1, 0, top = 14f, weight = 1f))

        dock = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dpi(8f), dpi(8f), dpi(8f), dpi(8f))
            background = box(Ui.PANEL, Ui.LINE, 24f)
        }
        root.addView(dock, lp(-1, -2, top = 10f))

        setContentView(root)
        root.isFocusableInTouchMode = true
        root.requestFocus()

        updateTabs()
        buildDock()

        val f = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(pkgRx, f, Context.RECEIVER_NOT_EXPORTED)
        else registerReceiver(pkgRx, f)

        reload()
    }

    private fun startGreeter() {
        try {
            startForegroundService(Intent(this, GreetService::class.java))
        } catch (e: Exception) {
            // not allowed right now; it will be retried next time the launcher opens
        }
    }

    private fun showSettings() {
        val voice = Greeting.voiceOn(this)
        val items = arrayOf(
            "Change my name (${Greeting.name(this)})",
            if (voice) "Turn voice greeting off" else "Turn voice greeting on",
            "Test greeting",
            "Change wallpaper"
        )
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("IONE settings")
            .setItems(items) { _, i ->
                when (i) {
                    0 -> askName()
                    1 -> Greeting.setVoice(this, !voice)
                    2 -> startService(Intent(this, GreetService::class.java).setAction(GreetService.ACTION_TEST))
                    3 -> chooseWallpaper()
                    else -> {}
                }
            }
            .show()
    }

    private fun chooseWallpaper() {
        try {
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Select wallpaper"))
        } catch (e: Exception) {
            Toast.makeText(this, "No wallpaper picker found on this phone", Toast.LENGTH_LONG).show()
        }
    }

    private fun askName() {
        val input = EditText(this)
        input.setText(Greeting.name(this))
        input.setSingleLine(true)
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Your name")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val n = input.text.toString().trim()
                if (n.isNotEmpty()) {
                    Greeting.setName(this, n)
                    greetText.text = Greeting.hello(this)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun buildHeader(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
        }
        val left = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val clock = TextClock(this).apply {
            format12Hour = "hh:mm"
            format24Hour = "HH:mm"
            textSize = 54f
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            setTextColor(Ui.TEXT)
            includeFontPadding = false
        }
        val date = TextClock(this).apply {
            format12Hour = "EEEE, d MMMM"
            format24Hour = "EEEE, d MMMM"
            textSize = 14f
            setTextColor(Ui.DIM)
        }
        greetText = TextView(this).apply {
            textSize = 14f
            setTextColor(Ui.ACCENT)
            text = Greeting.hello(this@LauncherActivity)
        }
        clock.setOnClickListener { showSettings() }
        left.addView(greetText)
        left.addView(clock)
        left.addView(date, lp(-2, -2, top = 2f))

        countText = TextView(this).apply {
            textSize = 12f
            setTextColor(Ui.DIM)
        }
        row.addView(left, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(countText)
        return row
    }

    private fun buildSearch(): View {
        search = EditText(this).apply {
            hint = "Search apps"
            setHintTextColor(Ui.DIM)
            setTextColor(Ui.TEXT)
            textSize = 15f
            setSingleLine(true)
            setPadding(dpi(18f), dpi(12f), dpi(18f), dpi(12f))
            background = box(Ui.PANEL, Ui.LINE, 26f)
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    query = s?.toString() ?: ""
                    refreshList()
                }
                override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
                override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            })
        }
        return search
    }

    private fun buildTabs(): View {
        tabsRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for (t in Apps.TABS) {
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                tag = t
                setPadding(0, dpi(6f), 0, 0)
                setOnClickListener {
                    tab = t
                    hideKeyboard()
                    search.setText("")
                    updateTabs()
                    refreshList()
                }
            }
            val label = TextView(this).apply {
                textSize = 12f
                letterSpacing = 0.08f
                setPadding(0, 0, 0, dpi(6f))
            }
            val bar = View(this)
            item.addView(label)
            item.addView(bar, LinearLayout.LayoutParams(-1, dpi(2f)))
            tabsRow.addView(item, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dpi(20f) })
        }
        return HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(tabsRow)
        }
    }

    private fun buildDock() {
        dock.removeAllViews()
        val pm = packageManager
        val intents = listOf(
            Intent(Intent.ACTION_DIAL),
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MESSAGING),
            Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com")),
            Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
        )
        for (target in intents) {
            val ri = pm.resolveActivity(target, PackageManager.MATCH_DEFAULT_ONLY) ?: continue
            if (ri.activityInfo.packageName == "android") continue
            val iv = ImageView(this)
            iv.setImageDrawable(ri.loadIcon(pm))
            iv.setOnClickListener {
                try {
                    startActivity(Intent(target).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (e: Exception) {
                }
            }
            dock.addView(iv, LinearLayout.LayoutParams(dpi(48f), dpi(48f)).apply {
                setMargins(dpi(14f), dpi(2f), dpi(14f), dpi(2f))
            })
        }
    }

    // ------------------------------------------------------------- app list

    private fun reload() {
        Thread {
            val list = Apps.load(this)
            runOnUiThread {
                all = list
                countText.text = "${list.size} apps"
                updateTabs()
                refreshList()
            }
        }.start()
    }

    private fun refreshList() {
        val q = query.trim().lowercase()
        val favs = Favs.get(this)
        shown.clear()
        shown.addAll(all.filter { a ->
            if (q.isNotEmpty()) a.label.lowercase().contains(q)
            else when (tab) {
                "ALL" -> true
                "FAVORITES" -> a.pkg in favs
                else -> a.cat == tab
            }
        })
        adapter.notifyDataSetChanged()
    }

    private fun updateTabs() {
        val favs = Favs.get(this)
        for (i in 0 until tabsRow.childCount) {
            val item = tabsRow.getChildAt(i) as LinearLayout
            val name = item.tag as String
            val n = when (name) {
                "ALL" -> all.size
                "FAVORITES" -> all.count { it.pkg in favs }
                else -> all.count { it.cat == name }
            }
            val on = name == tab
            val label = item.getChildAt(0) as TextView
            label.text = "$name  $n"
            label.setTextColor(if (on) Ui.TEXT else Ui.DIM)
            label.typeface = if (on) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            item.getChildAt(1).setBackgroundColor(if (on) Ui.ACCENT else Color.TRANSPARENT)
        }
    }

    private fun showMenu(a: AppItem) {
        val fav = Favs.get(this).contains(a.pkg)
        val items = arrayOf(
            if (fav) "Remove from favorites" else "Add to favorites",
            "App info",
            "Uninstall"
        )
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(a.label)
            .setItems(items) { _, i ->
                when (i) {
                    0 -> {
                        Favs.toggle(this, a.pkg)
                        updateTabs()
                        refreshList()
                    }
                    1 -> startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${a.pkg}"))
                    )
                    2 -> startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${a.pkg}")))
                    else -> {}
                }
            }
            .show()
    }

    inner class AppAdapter : BaseAdapter() {
        override fun getCount() = shown.size
        override fun getItem(position: Int): Any = shown[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val a = shown[position]
            val v = (convertView as? LinearLayout) ?: makeCell()
            (v.getChildAt(0) as ImageView).setImageDrawable(a.icon)
            (v.getChildAt(1) as TextView).text = a.label
            return v
        }
    }

    private fun makeCell(): LinearLayout {
        val cell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dpi(2f), dpi(4f), dpi(2f), dpi(2f))
        }
        cell.addView(ImageView(this), LinearLayout.LayoutParams(dpi(50f), dpi(50f)))
        val label = TextView(this).apply {
            textSize = 11.5f
            setTextColor(Ui.TEXT)
            gravity = Gravity.CENTER
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.END
        }
        cell.addView(label, lp(-1, -2, top = 6f))
        return cell
    }

    // -------------------------------------------------------------- system

    private val reloadTask = Runnable { reload() }

    private val pkgRx = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            main.removeCallbacks(reloadTask)
            main.postDelayed(reloadTask, 600)
        }
    }

    private fun hideKeyboard() {
        getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(search.windowToken, 0)
    }

    private fun resetHome() {
        hideKeyboard()
        search.setText("")
        search.clearFocus()
        tab = "ALL"
        updateTabs()
        refreshList()
        grid.setSelection(0)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) resetHome()
    }

    override fun onResume() {
        super.onResume()
        buildDock()
        greetText.text = Greeting.hello(this)
        startGreeter()
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        // A launcher never closes; Back just returns to the main screen.
        resetHome()
    }

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        unregisterReceiver(pkgRx)
        super.onDestroy()
    }
}
