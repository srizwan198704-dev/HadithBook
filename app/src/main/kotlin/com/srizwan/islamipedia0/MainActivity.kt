package com.srizwan.islamipedia0

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.TranslateAnimation
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URL
import java.security.MessageDigest

// ─────────────────────────────────────────────────────────────────
// Data Models
// ─────────────────────────────────────────────────────────────────
data class BookItem(
    val id: Int,
    val sequence: Int,
    val titleEn: String,
    val titleAr: String,
    val totalSection: Int,
    val totalHadith: Int
)

data class SectionItem(
    val id: Int,
    val sequence: Int,
    val title: String,
    val titleAr: String,
    val totalHadith: Int,
    val rangeStart: Int,
    val rangeEnd: Int
)

data class HadithItem(
    val hadithNumber: Int,
    val title: String,
    val descriptionAr: String,
    val description: String
)

// ─────────────────────────────────────────────────────────────────
// Page State
// ─────────────────────────────────────────────────────────────────
sealed class PageState {
    object Books : PageState()
    data class Sections(val bookId: Int, val bookTitle: String) : PageState()
    data class Hadith(val bookId: Int, val sectionId: Int, val bookTitle: String, val sectionTitle: String) : PageState()
}

// ─────────────────────────────────────────────────────────────────
// In-memory Cache
// ─────────────────────────────────────────────────────────────────
object HadithCache {
    var books: List<BookItem>? = null
    val sections = mutableMapOf<Int, List<SectionItem>>()
    val hadith = mutableMapOf<String, List<HadithItem>>()
}

// ─────────────────────────────────────────────────────────────────
// Main Activity
// ─────────────────────────────────────────────────────────────────
class MainActivity : AppCompatActivity() {

    private val cacheDirName = "hadith_data"
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Views
    private lateinit var toolbarLayout: LinearLayout
    private lateinit var backButton: ImageView
    private lateinit var toolbarTitleView: TextView
    private lateinit var searchToggleBtn: ImageView
    private lateinit var searchContainer: LinearLayout
    private lateinit var searchInput: EditText
    private lateinit var offlineIndicator: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var statusView: View  // loading/error overlay
    private lateinit var statusText: TextView
    private lateinit var retryButton: Button
    private lateinit var fabSearchBtn: FrameLayout
    private lateinit var globalSearchOverlay: FrameLayout

    // Global Search views
    private lateinit var globalSearchInput: EditText
    private lateinit var globalSearchStatus: TextView
    private lateinit var globalSearchRecycler: RecyclerView
    private lateinit var globalSearchHint: TextView

    // State
    private var currentState: PageState = PageState.Books
    private var allCurrentData: List<Any> = emptyList()
    private var isSearchOpen = false
    private var isGlobalSearchOpen = false
    private val searchHandler = Handler(Looper.getMainLooper())
    private var searchRunnable: Runnable? = null
    private val globalSearchHandler = Handler(Looper.getMainLooper())
    private var globalSearchRunnable: Runnable? = null

    // Marquee scroll
    private val marqueeHandler = Handler(Looper.getMainLooper())
    private var marqueeRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, true)
        val insetsController = WindowInsetsControllerCompat(window, window.decorView)
        insetsController.show(WindowInsetsCompat.Type.systemBars())
        insetsController.isAppearanceLightStatusBars = false
        insetsController.isAppearanceLightNavigationBars = false
        window.statusBarColor = Color.parseColor("#01837A")
        window.navigationBarColor = Color.BLACK

        val rootLayout = buildUI()
        setContentView(rootLayout)

        File(filesDir, cacheDirName).mkdirs()
        loadBooks()
    }

    // ─────────────────────────────────────────────────────────────
    // UI Builder (pure code, no XML needed)
    // ─────────────────────────────────────────────────────────────
    private fun buildUI(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor("#F5F5F5"))
        }

        // ── Toolbar ──
        toolbarLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#01837A"))
            setPadding(dp(12), dp(14), dp(12), dp(14))
            elevation = dp(4).toFloat()
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        backButton = ImageView(this).apply {
            setImageResource(R.drawable.back) // use your back drawable
            layoutParams = LinearLayout.LayoutParams(dp(24), dp(24))
            setColorFilter(Color.WHITE)
            setOnClickListener { handleBack() }
        }
        toolbarTitleView = TextView(this).apply {
            text = "হাদিস সমগ্র"
            textSize = 19f
            setTextColor(Color.WHITE)
            typeface = getBengaliTypeface()
            isSingleLine = true
            ellipsize = android.text.TextUtils.TruncateAt.MARQUEE
            marqueeRepeatLimit = -1
            isSelected = true
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(dp(10), 0, dp(10), 0)
            }
        }
        searchToggleBtn = ImageView(this).apply {
            setImageResource(R.drawable.search)
            layoutParams = LinearLayout.LayoutParams(dp(24), dp(24))
            setColorFilter(Color.WHITE)
            setOnClickListener { toggleSearch() }
        }
        toolbarLayout.addView(backButton)
        toolbarLayout.addView(toolbarTitleView)
        toolbarLayout.addView(searchToggleBtn)
        root.addView(toolbarLayout)

        // ── Search bar ──
        searchContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.WHITE)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            elevation = dp(3).toFloat()
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        searchInput = EditText(this).apply {
            hint = "খুঁজুন..."
            typeface = getBengaliTypeface()
            textSize = 16f
            setHintTextColor(Color.parseColor("#999999"))
            background = createRoundedBg(Color.WHITE, Color.parseColor("#01837A"), dp(2), dp(24))
            setPadding(dp(16), dp(10), dp(16), dp(10))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    searchHandler.removeCallbacks(searchRunnable ?: return)
                    searchRunnable = Runnable { performSearch(s?.toString() ?: "") }
                    searchHandler.postDelayed(searchRunnable!!, 300)
                }
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            })
        }
        searchContainer.addView(searchInput)
        root.addView(searchContainer)

        // ── Offline Indicator ──
        offlineIndicator = TextView(this).apply {
            text = "⚠️ অফলাইন মোড - ক্যাশে করা ডেটা দেখানো হচ্ছে"
            textSize = 12f
            setTextColor(Color.WHITE)
            typeface = getBengaliTypeface()
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#FF9800"))
            setPadding(dp(8), dp(5), dp(8), dp(5))
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        root.addView(offlineIndicator)

        // ── Content frame (RecyclerView + status overlay) ──
        val contentFrame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }

        recyclerView = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setPadding(dp(12), dp(12), dp(12), dp(80))
            clipToPadding = false
        }
        contentFrame.addView(recyclerView)

        // Status overlay
        val statusOverlay = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#F5F5F5"))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            visibility = View.GONE
        }
        statusText = TextView(this).apply {
            textSize = 17f
            typeface = getBengaliTypeface()
            gravity = Gravity.CENTER
            setPadding(dp(24), 0, dp(24), 0)
        }
        retryButton = Button(this).apply {
            text = "আবার চেষ্টা করুন"
            typeface = getBengaliTypeface()
            setTextColor(Color.WHITE)
            background = createRoundedSolid(Color.parseColor("#01837A"), dp(24))
            setPadding(dp(20), dp(10), dp(20), dp(10))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(16) }
            visibility = View.GONE
        }
        statusOverlay.addView(statusText)
        statusOverlay.addView(retryButton)
        statusView = statusOverlay
        contentFrame.addView(statusOverlay)

        // ── FAB Global Search ──
        fabSearchBtn = FrameLayout(this).apply {
            val size = dp(52)
            layoutParams = FrameLayout.LayoutParams(size, size, Gravity.BOTTOM or Gravity.END).apply {
                setMargins(0, 0, dp(20), dp(20))
            }
            background = createRoundedSolid(Color.parseColor("#01837A"), size / 2)
            elevation = dp(6).toFloat()
            setOnClickListener { openGlobalSearch() }
        }
        val fabIcon = ImageView(this).apply {
            setImageResource(R.drawable.search)
            setColorFilter(Color.WHITE)
            layoutParams = FrameLayout.LayoutParams(dp(26), dp(26), Gravity.CENTER)
        }
        fabSearchBtn.addView(fabIcon)
        contentFrame.addView(fabSearchBtn)

        root.addView(contentFrame)

        // ── Global Search Overlay ──
        globalSearchOverlay = buildGlobalSearchOverlay()
        val decorFrame = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        decorFrame.addView(root)
        decorFrame.addView(globalSearchOverlay)
        return decorFrame
    }

    private fun buildGlobalSearchOverlay(): FrameLayout {
        val overlay = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor("#80000000"))
            visibility = View.GONE
        }

        val popup = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        // Header
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#01837A"))
            setPadding(dp(12), dp(14), dp(12), dp(14))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        val closeBtn = ImageView(this).apply {
            setImageResource(R.drawable.back)
            setColorFilter(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(dp(24), dp(24))
            setOnClickListener { closeGlobalSearch() }
        }
        val headerTitle = TextView(this).apply {
            text = "সম্পূর্ণ হাদিস সার্চ"
            textSize = 17f
            setTextColor(Color.WHITE)
            typeface = getBengaliTypeface()
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(dp(10), 0, 0, 0)
            }
        }
        header.addView(closeBtn)
        header.addView(headerTitle)

        // Search input
        val inputWrap = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#F0FFFE"))
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        globalSearchInput = EditText(this).apply {
            hint = "হাদিস নম্বর, শিরোনাম বা বাংলা/আরবি লিখুন..."
            typeface = getBengaliTypeface()
            textSize = 16f
            setHintTextColor(Color.parseColor("#999999"))
            background = createRoundedBg(Color.WHITE, Color.parseColor("#01837A"), dp(2), dp(24))
            setPadding(dp(16), dp(10), dp(16), dp(10))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    val q = s?.toString() ?: ""
                    if (q.trim().length < 2) {
                        globalSearchStatus.text = "কমপক্ষে ২টি অক্ষর লিখুন..."
                        showGlobalHint()
                        return
                    }
                    globalSearchHandler.removeCallbacks(globalSearchRunnable ?: return)
                    globalSearchRunnable = Runnable { performGlobalSearchFromCache(q.trim()) }
                    globalSearchHandler.postDelayed(globalSearchRunnable!!, 400)
                }
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            })
        }
        inputWrap.addView(globalSearchInput)

        // Status bar
        globalSearchStatus = TextView(this).apply {
            text = "সার্চ করতে টাইপ করুন..."
            textSize = 13f
            setTextColor(Color.parseColor("#666666"))
            typeface = getBengaliTypeface()
            setBackgroundColor(Color.parseColor("#F9F9F9"))
            setPadding(dp(15), dp(8), dp(15), dp(8))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // Results RecyclerView
        val resultsFrame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }
        globalSearchRecycler = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setPadding(dp(12), dp(10), dp(12), dp(12))
            clipToPadding = false
        }
        globalSearchHint = TextView(this).apply {
            text = "🔍 সমস্ত হাদিস বই থেকে সার্চ করুন\n\nহাদিস নম্বর, বাংলা অনুবাদ বা আরবি টেক্সট দিয়ে সার্চ করা যাবে"
            textSize = 15f
            typeface = getBengaliTypeface()
            setTextColor(Color.parseColor("#999999"))
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(40), dp(24), dp(40))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP
            )
        }
        resultsFrame.addView(globalSearchRecycler)
        resultsFrame.addView(globalSearchHint)

        popup.addView(header)
        popup.addView(inputWrap)
        popup.addView(globalSearchStatus)
        popup.addView(resultsFrame)
        overlay.addView(popup)
        return overlay
    }

    // ─────────────────────────────────────────────────────────────
    // Toolbar helpers
    // ─────────────────────────────────────────────────────────────
    private fun updateToolbar(title: String) {
        toolbarTitleView.text = title
    }

    // ─────────────────────────────────────────────────────────────
    // Loading / Error states
    // ─────────────────────────────────────────────────────────────
    private fun showLoading() {
        recyclerView.visibility = View.GONE
        statusView.visibility = View.VISIBLE
        statusText.text = "লোড হচ্ছে..."
        statusText.setTextColor(Color.parseColor("#01837A"))
        retryButton.visibility = View.GONE
    }

    private fun showError(message: String, retry: (() -> Unit)? = null) {
        recyclerView.visibility = View.GONE
        statusView.visibility = View.VISIBLE
        statusText.text = "❌ $message"
        statusText.setTextColor(Color.parseColor("#E74C3C"))
        if (retry != null) {
            retryButton.visibility = View.VISIBLE
            retryButton.setOnClickListener { retry() }
        } else {
            retryButton.visibility = View.GONE
        }
    }

    private fun showContent() {
        statusView.visibility = View.GONE
        recyclerView.visibility = View.VISIBLE
    }

    // ─────────────────────────────────────────────────────────────
    // Network + Cache helpers
    // ─────────────────────────────────────────────────────────────
    private fun isNetworkAvailable(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val cap = cm.getNetworkCapabilities(network) ?: return false
        return cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
               cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun cacheFileName(key: String): String {
        val md = MessageDigest.getInstance("MD5")
        return md.digest(key.toByteArray()).joinToString("") { "%02x".format(it) } + ".json"
    }

    private fun getCachedData(key: String): String? {
        val f = File(File(filesDir, cacheDirName), cacheFileName(key))
        return if (f.exists()) f.readText() else null
    }

    private fun cacheData(key: String, data: String) {
        val dir = File(filesDir, cacheDirName)
        if (!dir.exists()) dir.mkdirs()
        File(dir, cacheFileName(key)).writeText(data)
    }

    private suspend fun fetchJson(url: String, cacheKey: String): String {
        getCachedData(cacheKey)?.let {
            offlineIndicator.visibility = View.VISIBLE
            return it
        }
        return withContext(Dispatchers.IO) {
            try {
                val text = URL(url).readText()
                cacheData(cacheKey, text)
                withContext(Dispatchers.Main) { offlineIndicator.visibility = View.GONE }
                text
            } catch (e: Exception) {
                getCachedData(cacheKey)?.also {
                    withContext(Dispatchers.Main) { offlineIndicator.visibility = View.VISIBLE }
                } ?: throw e
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Number → Bangla helper
    // ─────────────────────────────────────────────────────────────
    private fun toBangla(num: Int): String {
        val d = charArrayOf('০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯')
        return num.toString().map { if (it.isDigit()) d[it - '0'] else it }.joinToString("")
    }

    // ─────────────────────────────────────────────────────────────
    // Load Books
    // ─────────────────────────────────────────────────────────────
    private fun loadBooks() {
        currentState = PageState.Books
        updateToolbar("হাদিস সমগ্র")
        recyclerView.scrollToPosition(0)
        showLoading()
        scope.launch {
            try {
                val json = fetchJson(
                    "https://cdn.jsdelivr.net/gh/SunniPedia/sunnipedia@main/hadith-books/book/book-title.json",
                    "hadith_books_list"
                )
                val books = parseBooks(json)
                HadithCache.books = books
                allCurrentData = books
                showContent()
                recyclerView.adapter = BookAdapter(books) { book ->
                    loadSections(book.id, book.titleEn)
                }
            } catch (e: Exception) {
                showError("বই লোড করতে সমস্যা হয়েছে") { loadBooks() }
            }
        }
    }

    private fun parseBooks(json: String): List<BookItem> {
        val arr = JSONArray(json)
        return (0 until arr.length()).map { arr.getJSONObject(it) }.map { o ->
            BookItem(
                id = o.optInt("id"),
                sequence = o.optInt("sequence"),
                titleEn = o.optString("title_en", o.optString("title", "Book")),
                titleAr = o.optString("title_ar", ""),
                totalSection = o.optInt("total_section"),
                totalHadith = o.optInt("total_hadith")
            )
        }.sortedBy { it.sequence }
    }

    // ─────────────────────────────────────────────────────────────
    // Load Sections
    // ─────────────────────────────────────────────────────────────
    private fun loadSections(bookId: Int, bookTitle: String) {
        currentState = PageState.Sections(bookId, bookTitle)
        updateToolbar(bookTitle)
        recyclerView.scrollToPosition(0)
        showLoading()
        scope.launch {
            try {
                val cached = HadithCache.sections[bookId]
                val sections = if (cached != null) cached else {
                    val json = fetchJson(
                        "https://cdn.jsdelivr.net/gh/SunniPedia/sunnipedia@main/hadith-books/book/$bookId/title.json",
                        "sections_$bookId"
                    )
                    parseSections(json).also { HadithCache.sections[bookId] = it }
                }
                allCurrentData = sections
                showContent()
                recyclerView.adapter = SectionAdapter(sections) { section ->
                    loadHadith(bookId, section.id, bookTitle, section.title)
                }
            } catch (e: Exception) {
                showError("অধ্যায় লোড করতে সমস্যা হয়েছে") { loadSections(bookId, bookTitle) }
            }
        }
    }

    private fun parseSections(json: String): List<SectionItem> {
        val arr = JSONArray(json)
        return (0 until arr.length()).map { arr.getJSONObject(it) }.map { o ->
            SectionItem(
                id = o.optInt("id"),
                sequence = o.optInt("sequence"),
                title = o.optString("title", "অধ্যায়"),
                titleAr = o.optString("title_ar", ""),
                totalHadith = o.optInt("total_hadith"),
                rangeStart = o.optInt("range_start"),
                rangeEnd = o.optInt("range_end")
            )
        }.sortedBy { it.sequence }
    }

    // ─────────────────────────────────────────────────────────────
    // Load Hadith
    // ─────────────────────────────────────────────────────────────
    private fun loadHadith(bookId: Int, sectionId: Int, bookTitle: String, sectionTitle: String) {
        currentState = PageState.Hadith(bookId, sectionId, bookTitle, sectionTitle)
        updateToolbar(sectionTitle)
        recyclerView.scrollToPosition(0)
        showLoading()
        scope.launch {
            try {
                val key = "${bookId}_$sectionId"
                val cached = HadithCache.hadith[key]
                val hadithList = if (cached != null) cached else {
                    val json = fetchJson(
                        "https://cdn.jsdelivr.net/gh/SunniPedia/sunnipedia@main/hadith-books/book/$bookId/hadith/$sectionId.json",
                        "hadith_${bookId}_$sectionId"
                    )
                    parseHadith(json).also { HadithCache.hadith[key] = it }
                }
                allCurrentData = hadithList
                showContent()
                recyclerView.adapter = HadithAdapter(hadithList,
                    onCopy = { h -> copyHadith(h, bookTitle, sectionTitle) },
                    onShare = { h -> shareHadith(h, bookTitle, sectionTitle) }
                )
            } catch (e: Exception) {
                showError("হাদিস লোড করতে সমস্যা হয়েছে") {
                    loadHadith(bookId, sectionId, bookTitle, sectionTitle)
                }
            }
        }
    }

    private fun parseHadith(json: String): List<HadithItem> {
        val arr = JSONArray(json)
        return (0 until arr.length()).map { arr.getJSONObject(it) }.map { o ->
            HadithItem(
                hadithNumber = o.optInt("hadith_number"),
                title = o.optString("title", ""),
                descriptionAr = o.optString("description_ar", ""),
                description = o.optString("description", "")
            )
        }.sortedBy { it.hadithNumber }
    }

    // ─────────────────────────────────────────────────────────────
    // Search
    // ─────────────────────────────────────────────────────────────
    private fun toggleSearch() {
        if (isSearchOpen) {
            isSearchOpen = false
            searchContainer.visibility = View.GONE
            searchInput.setText("")
            performSearch("")
        } else {
            isSearchOpen = true
            searchContainer.visibility = View.VISIBLE
            searchInput.requestFocus()
        }
    }

    private fun closeSearch() {
        isSearchOpen = false
        searchContainer.visibility = View.GONE
        searchInput.setText("")
        performSearch("")
    }

    private fun performSearch(query: String) {
        if (query.isBlank()) {
            when (val s = currentState) {
                is PageState.Books -> {
                    HadithCache.books?.let { books ->
                        allCurrentData = books
                        recyclerView.adapter = BookAdapter(books) { loadSections(it.id, it.titleEn) }
                    }
                }
                is PageState.Sections -> {
                    HadithCache.sections[s.bookId]?.let { sections ->
                        allCurrentData = sections
                        recyclerView.adapter = SectionAdapter(sections) { sec ->
                            loadHadith(s.bookId, sec.id, s.bookTitle, sec.title)
                        }
                    }
                }
                is PageState.Hadith -> {
                    HadithCache.hadith["${s.bookId}_${s.sectionId}"]?.let { list ->
                        allCurrentData = list
                        recyclerView.adapter = HadithAdapter(list,
                            onCopy = { h -> copyHadith(h, s.bookTitle, s.sectionTitle) },
                            onShare = { h -> shareHadith(h, s.bookTitle, s.sectionTitle) }
                        )
                    }
                }
            }
            return
        }
        val term = query.lowercase().trim()
        when (val s = currentState) {
            is PageState.Books -> {
                val filtered = HadithCache.books?.filter {
                    it.titleEn.lowercase().contains(term) || it.titleAr.contains(term)
                } ?: emptyList()
                recyclerView.adapter = BookAdapter(filtered) { loadSections(it.id, it.titleEn) }
            }
            is PageState.Sections -> {
                val filtered = HadithCache.sections[s.bookId]?.filter {
                    it.title.lowercase().contains(term) || it.titleAr.contains(term)
                } ?: emptyList()
                recyclerView.adapter = SectionAdapter(filtered) { sec ->
                    loadHadith(s.bookId, sec.id, s.bookTitle, sec.title)
                }
            }
            is PageState.Hadith -> {
                val filtered = HadithCache.hadith["${s.bookId}_${s.sectionId}"]?.filter {
                    it.hadithNumber.toString().contains(term) ||
                    it.title.lowercase().contains(term) ||
                    it.description.lowercase().contains(term) ||
                    it.descriptionAr.contains(term)
                } ?: emptyList()
                recyclerView.adapter = HadithAdapter(filtered,
                    onCopy = { h -> copyHadith(h, s.bookTitle, s.sectionTitle) },
                    onShare = { h -> shareHadith(h, s.bookTitle, s.sectionTitle) }
                )
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Global Search
    // ─────────────────────────────────────────────────────────────
    private fun openGlobalSearch() {
        isGlobalSearchOpen = true
        globalSearchOverlay.visibility = View.VISIBLE
        globalSearchInput.requestFocus()
    }

    private fun closeGlobalSearch() {
        isGlobalSearchOpen = false
        globalSearchOverlay.visibility = View.GONE
        globalSearchInput.setText("")
        globalSearchStatus.text = "সার্চ করতে টাইপ করুন..."
        showGlobalHint()
    }

    private fun showGlobalHint() {
        globalSearchHint.visibility = View.VISIBLE
        globalSearchRecycler.adapter = null
    }

    private fun performGlobalSearchFromCache(query: String) {
        val books = HadithCache.books
        if (books == null) {
            globalSearchStatus.text = "⚠️ ক্যাশে কোনো ডাটা নেই। প্রথমে অনলাইনে ডাটা লোড করুন।"
            globalSearchHint.visibility = View.VISIBLE
            return
        }
        globalSearchHint.visibility = View.GONE
        globalSearchStatus.text = "⏳ ক্যাশে অনুসন্ধান করা হচ্ছে..."
        val term = query.lowercase()

        scope.launch(Dispatchers.Default) {
            val results = mutableListOf<GlobalSearchResult>()
            var totalHadith = 0
            var booksSearched = 0

            for (book in books) {
                val sections = HadithCache.sections[book.id] ?: continue
                var bookHasData = false
                for (section in sections) {
                    val key = "${book.id}_${section.id}"
                    val hadithList = HadithCache.hadith[key] ?: continue
                    bookHasData = true
                    totalHadith += hadithList.size
                    hadithList.filter { h ->
                        h.hadithNumber.toString().contains(term) ||
                        h.title.lowercase().contains(term) ||
                        h.description.lowercase().contains(term) ||
                        h.descriptionAr.contains(term)
                    }.forEach { h ->
                        results.add(GlobalSearchResult(h, book.titleEn.ifBlank { book.titleAr }, book.id, section.title, section.id))
                    }
                }
                if (bookHasData) {
                    booksSearched++
                    val snap = booksSearched
                    val rSnap = results.size
                    withContext(Dispatchers.Main) {
                        globalSearchStatus.text = "🔍 ${toBangla(snap)} টি বই ক্যাশে অনুসন্ধান হয়েছে — ${toBangla(rSnap)} টি ফলাফল"
                    }
                }
            }

            withContext(Dispatchers.Main) {
                if (results.isEmpty()) {
                    globalSearchStatus.text = if (totalHadith == 0)
                        "ক্যাশে ডাটা পাওয়া যায়নি"
                    else
                        "মোট ${toBangla(totalHadith)} টি হাদিস ক্যাশে আছে — কোনো ফলাফল নেই"
                    globalSearchHint.text = if (totalHadith == 0)
                        "😔 ক্যাশে কোনো হাদিস ডাটা নেই। ইন্টারনেট সংযোগ দিয়ে প্রথমে ডাটা ডাউনলোড করুন।"
                    else
                        "😔 ক্যাশে কোনো হাদিস পাওয়া যায়নি"
                    globalSearchHint.visibility = View.VISIBLE
                } else {
                    globalSearchStatus.text = "✅ ক্যাশে থেকে ${toBangla(results.size)} টি হাদিস পাওয়া গেছে"
                    globalSearchHint.visibility = View.GONE
                    globalSearchRecycler.adapter = GlobalSearchAdapter(results,
                        onCopy = { r -> copyHadith(r.hadith, r.bookTitle, r.sectionTitle) },
                        onShare = { r -> shareHadith(r.hadith, r.bookTitle, r.sectionTitle) }
                    )
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Copy / Share
    // ─────────────────────────────────────────────────────────────
    private fun copyHadith(hadith: HadithItem, bookTitle: String, sectionTitle: String) {
        val text = buildHadithText(hadith, bookTitle, sectionTitle, withAppLink = false)
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("হাদিস", text))
        Toast.makeText(this, "কপি করা হয়েছে!", Toast.LENGTH_SHORT).show()
    }

    private fun shareHadith(hadith: HadithItem, bookTitle: String, sectionTitle: String) {
        val text = buildHadithText(hadith, bookTitle, sectionTitle, withAppLink = true)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(intent, "শেয়ার করুন"))
    }

    private fun buildHadithText(hadith: HadithItem, bookTitle: String, sectionTitle: String, withAppLink: Boolean): String {
        return listOfNotNull(
            bookTitle.ifBlank { null },
            sectionTitle.ifBlank { null },
            "হাদিস নং: ${toBangla(hadith.hadithNumber)}",
            hadith.title.ifBlank { null },
            hadith.descriptionAr.ifBlank { null },
            hadith.description.ifBlank { null },
            if (withAppLink) "অ্যাপ: ইসলামী বিশ্বকোষ ও আল হাদিস\nhttps://play.google.com/store/apps/details?id=com.srizwan.islamipedia" else null
        ).joinToString("\n")
    }

    // ─────────────────────────────────────────────────────────────
    // Back navigation
    // ─────────────────────────────────────────────────────────────
    private fun handleBack() {
        when {
            isSearchOpen -> closeSearch()
            isGlobalSearchOpen -> closeGlobalSearch()
            currentState is PageState.Hadith -> {
                val s = currentState as PageState.Hadith
                loadSections(s.bookId, s.bookTitle)
            }
            currentState is PageState.Sections -> loadBooks()
            else -> finish()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        handleBack()
    }

    // ─────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────
    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        marqueeHandler.removeCallbacksAndMessages(null)
    }

    // ─────────────────────────────────────────────────────────────
    // Font / Drawing helpers
    // ─────────────────────────────────────────────────────────────
    private fun getBengaliTypeface() = try {
        android.graphics.Typeface.createFromAsset(assets, "fonts/SolaimanLipi.ttf")
    } catch (e: Exception) {
        android.graphics.Typeface.DEFAULT
    }

    private fun getArabicTypeface() = try {
        android.graphics.Typeface.createFromAsset(assets, "fonts/noorehuda.ttf")
    } catch (e: Exception) {
        android.graphics.Typeface.DEFAULT
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun createRoundedBg(fillColor: Int, strokeColor: Int, strokeWidth: Int, radius: Int): android.graphics.drawable.Drawable {
        return android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            setColor(fillColor)
            setStroke(strokeWidth, strokeColor)
            cornerRadius = radius.toFloat()
        }
    }

    private fun createRoundedSolid(fillColor: Int, radius: Int): android.graphics.drawable.Drawable {
        return android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            setColor(fillColor)
            cornerRadius = radius.toFloat()
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Adapters
    // ─────────────────────────────────────────────────────────────

    inner class BookAdapter(
        private val items: List<BookItem>,
        private val onClick: (BookItem) -> Unit
    ) : RecyclerView.Adapter<BookAdapter.VH>() {

        inner class VH(val card: FrameLayout) : RecyclerView.ViewHolder(card)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val card = FrameLayout(this@MainActivity).apply {
                layoutParams = RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    RecyclerView.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(14) }
                background = createRoundedBg(Color.WHITE, Color.parseColor("#01837A"), dp(2), dp(10))
                elevation = dp(3).toFloat()
                setPadding(dp(18), dp(16), dp(18), dp(14))
            }
            return VH(card)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val book = items[position]
            holder.card.removeAllViews()

            val innerLayout = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                )
            }

            // Badge
            val badge = TextView(this@MainActivity).apply {
                text = toBangla(book.sequence)
                textSize = 15f
                setTextColor(Color.WHITE)
                typeface = getBengaliTypeface()
                gravity = Gravity.CENTER
                background = createRoundedSolid(Color.parseColor("#01837A"), dp(18))
                layoutParams = FrameLayout.LayoutParams(dp(36), dp(36)).apply {
                    topMargin = -dp(18)
                    leftMargin = -dp(18)
                }
            }

            // English title
            val titleEn = TextView(this@MainActivity).apply {
                text = book.titleEn
                textSize = 17f
                setTextColor(Color.parseColor("#01837A"))
                typeface = getBengaliTypeface()
                setPadding(dp(20), 0, 0, 0)
            }

            // Arabic title
            val titleAr = TextView(this@MainActivity).apply {
                text = book.titleAr
                textSize = 18f
                setTextColor(Color.parseColor("#333333"))
                typeface = getArabicTypeface()
                gravity = Gravity.END
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(10); bottomMargin = dp(10) }
            }

            // Meta row
            val metaRow = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(8) }
            }
            val divider = View(this@MainActivity).apply {
                setBackgroundColor(Color.parseColor("#DDDDDD"))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(1)
                )
            }
            val sectionMeta = TextView(this@MainActivity).apply {
                text = "📚 ${toBangla(book.totalSection)} টি অধ্যায়"
                textSize = 13f
                setTextColor(Color.parseColor("#666666"))
                typeface = getBengaliTypeface()
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val hadithMeta = TextView(this@MainActivity).apply {
                text = "📖 ${toBangla(book.totalHadith)} টি হাদিস"
                textSize = 13f
                setTextColor(Color.parseColor("#666666"))
                typeface = getBengaliTypeface()
            }
            metaRow.addView(sectionMeta)
            metaRow.addView(hadithMeta)

            innerLayout.addView(titleEn)
            innerLayout.addView(titleAr)
            innerLayout.addView(divider)
            innerLayout.addView(metaRow)

            holder.card.addView(innerLayout)
            holder.card.addView(badge)
            holder.card.setOnClickListener { onClick(book) }
        }

        override fun getItemCount() = items.size
    }

    inner class SectionAdapter(
        private val items: List<SectionItem>,
        private val onClick: (SectionItem) -> Unit
    ) : RecyclerView.Adapter<SectionAdapter.VH>() {

        inner class VH(val card: FrameLayout) : RecyclerView.ViewHolder(card)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val card = FrameLayout(this@MainActivity).apply {
                layoutParams = RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    RecyclerView.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(14) }
                background = createRoundedBg(Color.WHITE, Color.parseColor("#01837A"), dp(2), dp(10))
                elevation = dp(3).toFloat()
                setPadding(dp(18), dp(16), dp(18), dp(14))
            }
            return VH(card)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val section = items[position]
            holder.card.removeAllViews()

            val inner = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                )
            }

            val badge = TextView(this@MainActivity).apply {
                text = toBangla(section.sequence)
                textSize = 15f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                background = createRoundedSolid(Color.parseColor("#01837A"), dp(18))
                layoutParams = FrameLayout.LayoutParams(dp(36), dp(36)).apply {
                    topMargin = -dp(18); leftMargin = -dp(18)
                }
            }

            val titleView = TextView(this@MainActivity).apply {
                text = section.title
                textSize = 17f
                setTextColor(Color.parseColor("#01837A"))
                typeface = getBengaliTypeface()
                setPadding(dp(20), 0, 0, 0)
            }

            val titleAr = TextView(this@MainActivity).apply {
                text = section.titleAr
                textSize = 18f
                setTextColor(Color.parseColor("#333333"))
                typeface = getArabicTypeface()
                gravity = Gravity.END
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(10); bottomMargin = dp(10) }
            }

            val divider = View(this@MainActivity).apply {
                setBackgroundColor(Color.parseColor("#DDDDDD"))
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1))
            }

            val metaRow = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(8) }
            }
            val hadithMeta = TextView(this@MainActivity).apply {
                text = "📖 মোট ${toBangla(section.totalHadith)} টি হাদিস"
                textSize = 13f
                setTextColor(Color.parseColor("#666666"))
                typeface = getBengaliTypeface()
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            metaRow.addView(hadithMeta)

            if (section.rangeStart > 0 && section.rangeEnd > 0) {
                val rangeMeta = TextView(this@MainActivity).apply {
                    text = "🔢 ব্যাপ্তি: ${toBangla(section.rangeStart)}-${toBangla(section.rangeEnd)}"
                    textSize = 13f
                    setTextColor(Color.parseColor("#666666"))
                    typeface = getBengaliTypeface()
                }
                metaRow.addView(rangeMeta)
            }

            inner.addView(titleView)
            inner.addView(titleAr)
            inner.addView(divider)
            inner.addView(metaRow)

            holder.card.addView(inner)
            holder.card.addView(badge)
            holder.card.setOnClickListener { onClick(section) }
        }

        override fun getItemCount() = items.size
    }

    inner class HadithAdapter(
        private val items: List<HadithItem>,
        private val onCopy: (HadithItem) -> Unit,
        private val onShare: (HadithItem) -> Unit
    ) : RecyclerView.Adapter<HadithAdapter.VH>() {

        inner class VH(val card: LinearLayout) : RecyclerView.ViewHolder(card)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val card = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    RecyclerView.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(14) }
                background = createRoundedBg(Color.WHITE, Color.parseColor("#01837A"), dp(2), dp(10))
                elevation = dp(3).toFloat()
                setPadding(dp(16), dp(14), dp(16), dp(14))
            }
            return VH(card)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val hadith = items[position]
            holder.card.removeAllViews()

            // Header row
            val headerRow = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            val numberBadge = TextView(this@MainActivity).apply {
                text = "হাদিস নং: ${toBangla(hadith.hadithNumber)}"
                textSize = 13f
                setTextColor(Color.WHITE)
                typeface = getBengaliTypeface()
                background = createRoundedSolid(Color.parseColor("#01837A"), dp(20))
                setPadding(dp(12), dp(5), dp(12), dp(5))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            val spacer = View(this@MainActivity).apply {
                layoutParams = LinearLayout.LayoutParams(0, 0, 1f)
            }
            val copyBtn = ImageView(this@MainActivity).apply {
                setImageResource(R.drawable.copy)
                setColorFilter(Color.parseColor("#01837A"))
                layoutParams = LinearLayout.LayoutParams(dp(24), dp(24)).apply { marginEnd = dp(10) }
                setOnClickListener { onCopy(hadith) }
            }
            val shareBtn = ImageView(this@MainActivity).apply {
                setImageResource(R.drawable.share)
                setColorFilter(Color.parseColor("#01837A"))
                layoutParams = LinearLayout.LayoutParams(dp(24), dp(24))
                setOnClickListener { onShare(hadith) }
            }
            headerRow.addView(numberBadge)
            headerRow.addView(spacer)
            headerRow.addView(copyBtn)
            headerRow.addView(shareBtn)

            holder.card.addView(headerRow)

            // Title
            if (hadith.title.isNotBlank()) {
                val titleView = TextView(this@MainActivity).apply {
                    text = hadith.title
                    textSize = 17f
                    setTextColor(Color.parseColor("#01837A"))
                    typeface = getBengaliTypeface()
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = dp(10) }
                }
                holder.card.addView(titleView)
            }

            // Arabic text
            if (hadith.descriptionAr.isNotBlank()) {
                val arView = TextView(this@MainActivity).apply {
                    text = hadith.descriptionAr
                    textSize = 20f
                    setTextColor(Color.parseColor("#333333"))
                    typeface = getArabicTypeface()
                    gravity = Gravity.END
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = dp(12); bottomMargin = dp(6) }
                }
                holder.card.addView(arView)
            }

            // Bengali description
            if (hadith.description.isNotBlank()) {
                val descView = TextView(this@MainActivity).apply {
                    text = hadith.description
                    textSize = 15f
                    setTextColor(Color.parseColor("#444444"))
                    typeface = getBengaliTypeface()
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = dp(8) }
                }
                holder.card.addView(descView)
            }
        }

        override fun getItemCount() = items.size
    }

    // ─────────────────────────────────────────────────────────────
    // Global Search Result model + Adapter
    // ─────────────────────────────────────────────────────────────
    data class GlobalSearchResult(
        val hadith: HadithItem,
        val bookTitle: String,
        val bookId: Int,
        val sectionTitle: String,
        val sectionId: Int
    )

    inner class GlobalSearchAdapter(
        private val items: List<GlobalSearchResult>,
        private val onCopy: (GlobalSearchResult) -> Unit,
        private val onShare: (GlobalSearchResult) -> Unit
    ) : RecyclerView.Adapter<GlobalSearchAdapter.VH>() {

        inner class VH(val card: LinearLayout) : RecyclerView.ViewHolder(card)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val card = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    RecyclerView.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(12) }
                background = createRoundedBg(Color.WHITE, Color.parseColor("#01837A"), dp(2), dp(10))
                elevation = dp(3).toFloat()
                setPadding(dp(14), dp(12), dp(14), dp(12))
            }
            return VH(card)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val result = items[position]
            val hadith = result.hadith
            holder.card.removeAllViews()

            // Header row
            val headerRow = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            val numberBadge = TextView(this@MainActivity).apply {
                text = "হাদিস নং: ${toBangla(hadith.hadithNumber)}"
                textSize = 12f
                setTextColor(Color.WHITE)
                typeface = getBengaliTypeface()
                background = createRoundedSolid(Color.parseColor("#01837A"), dp(20))
                setPadding(dp(10), dp(4), dp(10), dp(4))
            }
            val bookLabel = TextView(this@MainActivity).apply {
                text = result.bookTitle
                textSize = 11f
                setTextColor(Color.parseColor("#01837A"))
                typeface = getBengaliTypeface()
                background = createRoundedBg(Color.parseColor("#E8F8F7"), Color.parseColor("#01837A"), dp(1), dp(12))
                setPadding(dp(8), dp(3), dp(8), dp(3))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginStart = dp(8) }
                maxWidth = dp(120)
                isSingleLine = true
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
            val spacer = View(this@MainActivity).apply {
                layoutParams = LinearLayout.LayoutParams(0, 0, 1f)
            }
            val copyBtn = ImageView(this@MainActivity).apply {
                setImageResource(R.drawable.copy)
                setColorFilter(Color.parseColor("#01837A"))
                layoutParams = LinearLayout.LayoutParams(dp(22), dp(22)).apply { marginEnd = dp(8) }
                setOnClickListener { onCopy(result) }
            }
            val shareBtn = ImageView(this@MainActivity).apply {
                setImageResource(R.drawable.share)
                setColorFilter(Color.parseColor("#01837A"))
                layoutParams = LinearLayout.LayoutParams(dp(22), dp(22))
                setOnClickListener { onShare(result) }
            }
            headerRow.addView(numberBadge)
            headerRow.addView(bookLabel)
            headerRow.addView(spacer)
            headerRow.addView(copyBtn)
            headerRow.addView(shareBtn)
            holder.card.addView(headerRow)

            if (hadith.title.isNotBlank()) {
                holder.card.addView(TextView(this@MainActivity).apply {
                    text = hadith.title
                    textSize = 15f
                    setTextColor(Color.parseColor("#01837A"))
                    typeface = getBengaliTypeface()
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = dp(8) }
                })
            }

            if (hadith.descriptionAr.isNotBlank()) {
                holder.card.addView(TextView(this@MainActivity).apply {
                    text = hadith.descriptionAr
                    textSize = 18f
                    setTextColor(Color.parseColor("#333333"))
                    typeface = getArabicTypeface()
                    gravity = Gravity.END
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = dp(10); bottomMargin = dp(6) }
                })
            }

            if (hadith.description.isNotBlank()) {
                holder.card.addView(TextView(this@MainActivity).apply {
                    text = hadith.description
                    textSize = 14f
                    setTextColor(Color.parseColor("#444444"))
                    typeface = getBengaliTypeface()
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = dp(8) }
                })
            }
        }

        override fun getItemCount() = items.size
    }
}
