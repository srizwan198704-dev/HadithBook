package com.srizwan.islamipedia0

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.*
import org.json.JSONArray
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
    data class Hadith(
        val bookId: Int,
        val sectionId: Int,
        val bookTitle: String,
        val sectionTitle: String
    ) : PageState()
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
    private lateinit var statusView: View
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
    private val marqueeHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ── Status bar / Navigation bar fix ──
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.statusBarColor = Color.parseColor("#01837A")
        window.navigationBarColor = Color.BLACK

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            var flags = window.decorView.systemUiVisibility
            flags = flags and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
            window.decorView.systemUiVisibility = flags
        }

        val rootLayout = buildUI()
        setContentView(rootLayout)

        // Apply window insets so toolbar never goes under status bar
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, systemBars.top, 0, systemBars.bottom)
            insets
        }

        File(filesDir, cacheDirName).mkdirs()
        loadBooks()
    }

    // ─────────────────────────────────────────────────────────────
    // UI Builder
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
            setImageResource(R.drawable.back)
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
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            ).apply { setMargins(dp(10), 0, dp(10), 0) }
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
            setTextColor(Color.BLACK)
            setHintTextColor(Color.parseColor("#999999"))
            background = createRoundedBg(
                Color.WHITE, Color.parseColor("#01837A"), dp(2), dp(24)
            )
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
                override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
                override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
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

        // ── Content Frame ──
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
            layoutParams = FrameLayout.LayoutParams(
                size, size, Gravity.BOTTOM or Gravity.END
            ).apply { setMargins(0, 0, dp(20), dp(20)) }
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

    // ─────────────────────────────────────────────────────────────
    // Global Search Overlay
    // ─────────────────────────────────────────────────────────────
    private fun buildGlobalSearchOverlay(): FrameLayout {
        val overlay = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.TRANSPARENT)
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
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            ).apply { setMargins(dp(10), 0, 0, 0) }
        }
        header.addView(closeBtn)
        header.addView(headerTitle)

        // Search input wrap
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
            setTextColor(Color.BLACK)
            setHintTextColor(Color.parseColor("#999999"))
            background = createRoundedBg(
                Color.WHITE, Color.parseColor("#01837A"), dp(2), dp(24)
            )
            setPadding(dp(16), dp(10), dp(16), dp(10))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    val q = s?.toString() ?: ""
                    globalSearchHandler.removeCallbacks(globalSearchRunnable ?: Runnable {})
                    if (q.trim().length < 2) {
                        globalSearchStatus.text = "কমপক্ষে ২টি অক্ষর লিখুন..."
                        showGlobalHint("🔍 সমস্ত হাদিস বই থেকে সার্চ করুন\n\nহাদিস নম্বর, বাংলা অনুবাদ বা আরবি টেক্সট দিয়ে সার্চ করা যাবে")
                        return
                    }
                    globalSearchStatus.text = "⏳ টাইপ করা থামলে সার্চ শুরু হবে..."
                    globalSearchRunnable = Runnable { performGlobalSearchFromCache(q.trim()) }
                    globalSearchHandler.postDelayed(globalSearchRunnable!!, 600)
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

        // Results frame
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
            visibility = View.GONE
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
    // Toolbar
    // ─────────────────────────────────────────────────────────────
    private fun updateToolbar(title: String) {
        toolbarTitleView.text = title
    }

    // ─────────────────────────────────────────────────────────────
    // Loading / Error / Content states
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
        retryButton.visibility = if (retry != null) View.VISIBLE else View.GONE
        retry?.let { r -> retryButton.setOnClickListener { r() } }
    }

    private fun showContent() {
        statusView.visibility = View.GONE
        recyclerView.visibility = View.VISIBLE
    }

    // ─────────────────────────────────────────────────────────────
    // Network + Cache
    // ─────────────────────────────────────────────────────────────
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
        // Try fresh fetch first
        return withContext(Dispatchers.IO) {
            try {
                val text = URL(url).readText()
                cacheData(cacheKey, text)
                withContext(Dispatchers.Main) { offlineIndicator.visibility = View.GONE }
                text
            } catch (e: Exception) {
                // Fall back to cache
                val cached = getCachedData(cacheKey)
                if (cached != null) {
                    withContext(Dispatchers.Main) { offlineIndicator.visibility = View.VISIBLE }
                    cached
                } else {
                    throw e
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Bangla number
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
                val sections = HadithCache.sections[bookId] ?: run {
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
    private fun loadHadith(
        bookId: Int, sectionId: Int, bookTitle: String, sectionTitle: String
    ) {
        currentState = PageState.Hadith(bookId, sectionId, bookTitle, sectionTitle)
        updateToolbar(sectionTitle)
        recyclerView.scrollToPosition(0)
        showLoading()
        scope.launch {
            try {
                val key = "${bookId}_$sectionId"
                val hadithList = HadithCache.hadith[key] ?: run {
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
    // Page-level Search
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
                is PageState.Books -> HadithCache.books?.let { books ->
                    recyclerView.adapter = BookAdapter(books) { loadSections(it.id, it.titleEn) }
                }
                is PageState.Sections -> HadithCache.sections[s.bookId]?.let { sections ->
                    recyclerView.adapter = SectionAdapter(sections) { sec ->
                        loadHadith(s.bookId, sec.id, s.bookTitle, sec.title)
                    }
                }
                is PageState.Hadith -> HadithCache.hadith["${s.bookId}_${s.sectionId}"]?.let { list ->
                    recyclerView.adapter = HadithAdapter(list,
                        onCopy = { h -> copyHadith(h, s.bookTitle, s.sectionTitle) },
                        onShare = { h -> shareHadith(h, s.bookTitle, s.sectionTitle) }
                    )
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
        globalSearchHandler.removeCallbacks(globalSearchRunnable ?: Runnable {})
        globalSearchOverlay.visibility = View.GONE
        globalSearchInput.setText("")
        globalSearchStatus.text = "সার্চ করতে টাইপ করুন..."
        showGlobalHint("🔍 সমস্ত হাদিস বই থেকে সার্চ করুন\n\nহাদিস নম্বর, বাংলা অনুবাদ বা আরবি টেক্সট দিয়ে সার্চ করা যাবে")
    }

    private fun showGlobalHint(msg: String) {
        globalSearchHint.text = msg
        globalSearchHint.visibility = View.VISIBLE
        globalSearchRecycler.visibility = View.GONE
        globalSearchRecycler.adapter = null
    }

    private fun performGlobalSearchFromCache(query: String) {
        val books = HadithCache.books
        if (books == null || books.isEmpty()) {
            globalSearchStatus.text = "⚠️ ক্যাশে কোনো ডাটা নেই। প্রথমে বই লিস্ট লোড করুন।"
            showGlobalHint("প্রথমে বই লিস্ট থেকে কিছু হাদিস খুলুন, তারপর সার্চ করুন।")
            return
        }

        globalSearchHint.visibility = View.GONE
        globalSearchRecycler.visibility = View.GONE
        globalSearchStatus.text = "🔍 ক্যাশে অনুসন্ধান চলছে..."

        scope.launch(Dispatchers.Default) {
            val results = mutableListOf<GlobalSearchResult>()
            val term = query.lowercase()
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
                        results.add(
                            GlobalSearchResult(
                                hadith = h,
                                bookTitle = book.titleEn.ifBlank { book.titleAr },
                                bookId = book.id,
                                sectionTitle = section.title,
                                sectionId = section.id
                            )
                        )
                    }
                }

                if (bookHasData) {
                    booksSearched++
                    val snap = booksSearched
                    val rSnap = results.size
                    withContext(Dispatchers.Main) {
                        globalSearchStatus.text =
                            "🔍 ${toBangla(snap)} টি বই দেখা হয়েছে — ${toBangla(rSnap)} টি ফলাফল"
                    }
                }
            }

            withContext(Dispatchers.Main) {
                when {
                    totalHadith == 0 -> {
                        globalSearchStatus.text = "ক্যাশে হাদিস ডাটা নেই"
                        showGlobalHint("😔 ক্যাশে কোনো হাদিস ডাটা নেই।\nপ্রথমে বই খুলুন, তারপর সার্চ করুন।")
                    }
                    results.isEmpty() -> {
                        globalSearchStatus.text =
                            "মোট ${toBangla(totalHadith)} টি হাদিস দেখা হয়েছে — কোনো ফলাফল নেই"
                        showGlobalHint("😔 \"$query\" এর জন্য কোনো হাদিস পাওয়া যায়নি।")
                    }
                    else -> {
                        globalSearchStatus.text =
                            "✅ ${toBangla(results.size)} টি হাদিস পাওয়া গেছে"
                        globalSearchHint.visibility = View.GONE
                        globalSearchRecycler.visibility = View.VISIBLE
                        globalSearchRecycler.adapter = GlobalSearchAdapter(
                            results,
                            onCopy = { r -> copyHadith(r.hadith, r.bookTitle, r.sectionTitle) },
                            onShare = { r -> shareHadith(r.hadith, r.bookTitle, r.sectionTitle) }
                        )
                    }
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

    private fun buildHadithText(
        hadith: HadithItem,
        bookTitle: String,
        sectionTitle: String,
        withAppLink: Boolean
    ): String {
        return listOfNotNull(
            bookTitle.ifBlank { null },
            sectionTitle.ifBlank { null },
            "হাদিস নং: ${toBangla(hadith.hadithNumber)}",
            hadith.title.ifBlank { null },
            hadith.descriptionAr.ifBlank { null },
            hadith.description.ifBlank { null },
            if (withAppLink)
                "অ্যাপ: ইসলামী বিশ্বকোষ ও আল হাদিস\nhttps://play.google.com/store/apps/details?id=com.srizwan.islamipedia"
            else null
        ).joinToString("\n")
    }

    // ─────────────────────────────────────────────────────────────
    // Back Navigation
    // ─────────────────────────────────────────────────────────────
    private fun handleBack() {
        when {
            isGlobalSearchOpen -> closeGlobalSearch()
            isSearchOpen -> closeSearch()
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
        searchHandler.removeCallbacksAndMessages(null)
        globalSearchHandler.removeCallbacksAndMessages(null)
    }

    // ─────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────
    private fun getBengaliTypeface() = try {
        android.graphics.Typeface.createFromAsset(assets, "fonts/SolaimanLipi.ttf")
    } catch (e: Exception) { android.graphics.Typeface.DEFAULT }

    private fun getArabicTypeface() = try {
        android.graphics.Typeface.createFromAsset(assets, "fonts/noorehuda.ttf")
    } catch (e: Exception) { android.graphics.Typeface.DEFAULT }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun createRoundedBg(
        fillColor: Int, strokeColor: Int, strokeWidth: Int, radius: Int
    ): android.graphics.drawable.Drawable =
        android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            setColor(fillColor)
            setStroke(strokeWidth, strokeColor)
            cornerRadius = radius.toFloat()
        }

    private fun createRoundedSolid(
        fillColor: Int, radius: Int
    ): android.graphics.drawable.Drawable =
        android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            setColor(fillColor)
            cornerRadius = radius.toFloat()
        }

    // ─────────────────────────────────────────────────────────────
    // Book Adapter
    // ─────────────────────────────────────────────────────────────
    inner class BookAdapter(
        private val items: List<BookItem>,
        private val onClick: (BookItem) -> Unit
    ) : RecyclerView.Adapter<BookAdapter.VH>() {

        inner class VH(val card: LinearLayout) : RecyclerView.ViewHolder(card)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val card = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    RecyclerView.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(14) }
                background = createRoundedBg(
                    Color.WHITE, Color.parseColor("#01837A"), dp(2), dp(10)
                )
                elevation = dp(3).toFloat()
                setPadding(dp(16), dp(14), dp(16), dp(14))
            }
            return VH(card)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val book = items[position]
            holder.card.removeAllViews()
            holder.card.setOnClickListener { onClick(book) }

            // English title
            holder.card.addView(TextView(this@MainActivity).apply {
                text = book.titleEn
                textSize = 17f
                setTextColor(Color.parseColor("#01837A"))
                typeface = getBengaliTypeface()
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            })

            // Arabic title
            if (book.titleAr.isNotBlank()) {
                holder.card.addView(TextView(this@MainActivity).apply {
                    text = book.titleAr
                    textSize = 18f
                    setTextColor(Color.parseColor("#333333"))
                    typeface = getArabicTypeface()
                    gravity = Gravity.END
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = dp(10); bottomMargin = dp(10) }
                })
            }

            // Divider
            holder.card.addView(View(this@MainActivity).apply {
                setBackgroundColor(Color.parseColor("#DDDDDD"))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(1)
                )
            })

            // Meta row
            val metaRow = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(8) }
            }
            metaRow.addView(TextView(this@MainActivity).apply {
                text = "📚 ${toBangla(book.totalSection)} টি অধ্যায়"
                textSize = 13f
                setTextColor(Color.parseColor("#666666"))
                typeface = getBengaliTypeface()
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            metaRow.addView(TextView(this@MainActivity).apply {
                text = "📖 ${toBangla(book.totalHadith)} টি হাদিস"
                textSize = 13f
                setTextColor(Color.parseColor("#666666"))
                typeface = getBengaliTypeface()
            })
            holder.card.addView(metaRow)
        }

        override fun getItemCount() = items.size
    }

    // ─────────────────────────────────────────────────────────────
    // Section Adapter
    // ─────────────────────────────────────────────────────────────
    inner class SectionAdapter(
        private val items: List<SectionItem>,
        private val onClick: (SectionItem) -> Unit
    ) : RecyclerView.Adapter<SectionAdapter.VH>() {

        inner class VH(val card: LinearLayout) : RecyclerView.ViewHolder(card)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val card = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    RecyclerView.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(14) }
                background = createRoundedBg(
                    Color.WHITE, Color.parseColor("#01837A"), dp(2), dp(10)
                )
                elevation = dp(3).toFloat()
                setPadding(dp(16), dp(14), dp(16), dp(14))
            }
            return VH(card)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val section = items[position]
            holder.card.removeAllViews()
            holder.card.setOnClickListener { onClick(section) }

            // Title
            holder.card.addView(TextView(this@MainActivity).apply {
                text = section.title
                textSize = 17f
                setTextColor(Color.parseColor("#01837A"))
                typeface = getBengaliTypeface()
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            })

            // Arabic title
            if (section.titleAr.isNotBlank()) {
                holder.card.addView(TextView(this@MainActivity).apply {
                    text = section.titleAr
                    textSize = 18f
                    setTextColor(Color.parseColor("#333333"))
                    typeface = getArabicTypeface()
                    gravity = Gravity.END
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = dp(10); bottomMargin = dp(10) }
                })
            }

            // Divider
            holder.card.addView(View(this@MainActivity).apply {
                setBackgroundColor(Color.parseColor("#DDDDDD"))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(1)
                )
            })

            // Meta row
            val metaRow = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(8) }
            }
            metaRow.addView(TextView(this@MainActivity).apply {
                text = "📖 মোট ${toBangla(section.totalHadith)} টি হাদিস"
                textSize = 13f
                setTextColor(Color.parseColor("#666666"))
                typeface = getBengaliTypeface()
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            if (section.rangeStart > 0 && section.rangeEnd > 0) {
                metaRow.addView(TextView(this@MainActivity).apply {
                    text = "🔢 ব্যাপ্তি: ${toBangla(section.rangeStart)}-${toBangla(section.rangeEnd)}"
                    textSize = 13f
                    setTextColor(Color.parseColor("#666666"))
                    typeface = getBengaliTypeface()
                })
            }
            holder.card.addView(metaRow)
        }

        override fun getItemCount() = items.size
    }

    // ─────────────────────────────────────────────────────────────
    // Hadith Adapter
    // ─────────────────────────────────────────────────────────────
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
                background = createRoundedBg(
                    Color.WHITE, Color.parseColor("#01837A"), dp(2), dp(10)
                )
                elevation = dp(3).toFloat()
                setPadding(dp(16), dp(14), dp(16), dp(14))
            }
            return VH(card)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val hadith = items[position]
            holder.card.removeAllViews()

            // Header row: number badge + copy + share
            val headerRow = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            headerRow.addView(TextView(this@MainActivity).apply {
                text = "হাদিস নং: ${toBangla(hadith.hadithNumber)}"
                textSize = 13f
                setTextColor(Color.WHITE)
                typeface = getBengaliTypeface()
                background = createRoundedSolid(Color.parseColor("#01837A"), dp(20))
                setPadding(dp(12), dp(5), dp(12), dp(5))
            })
            headerRow.addView(View(this@MainActivity).apply {
                layoutParams = LinearLayout.LayoutParams(0, 0, 1f)
            })
            headerRow.addView(ImageView(this@MainActivity).apply {
                setImageResource(R.drawable.copy)
                setColorFilter(Color.parseColor("#01837A"))
                layoutParams = LinearLayout.LayoutParams(dp(24), dp(24)).apply {
                    marginEnd = dp(10)
                }
                setOnClickListener { onCopy(hadith) }
            })
            headerRow.addView(ImageView(this@MainActivity).apply {
                setImageResource(R.drawable.share)
                setColorFilter(Color.parseColor("#01837A"))
                layoutParams = LinearLayout.LayoutParams(dp(24), dp(24))
                setOnClickListener { onShare(hadith) }
            })
            holder.card.addView(headerRow)

            // Title
            if (hadith.title.isNotBlank()) {
                holder.card.addView(TextView(this@MainActivity).apply {
                    text = hadith.title
                    textSize = 17f
                    setTextColor(Color.parseColor("#01837A"))
                    typeface = getBengaliTypeface()
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = dp(10) }
                })
            }

            // Arabic
            if (hadith.descriptionAr.isNotBlank()) {
                holder.card.addView(TextView(this@MainActivity).apply {
                    text = hadith.descriptionAr
                    textSize = 20f
                    setTextColor(Color.parseColor("#333333"))
                    typeface = getArabicTypeface()
                    gravity = Gravity.END
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = dp(12); bottomMargin = dp(6) }
                })
            }

            // Bengali
            if (hadith.description.isNotBlank()) {
                holder.card.addView(TextView(this@MainActivity).apply {
                    text = hadith.description
                    textSize = 15f
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

    // ─────────────────────────────────────────────────────────────
    // Global Search Result + Adapter
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
                background = createRoundedBg(
                    Color.WHITE, Color.parseColor("#01837A"), dp(2), dp(10)
                )
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
            headerRow.addView(TextView(this@MainActivity).apply {
                text = "হাদিস নং: ${toBangla(hadith.hadithNumber)}"
                textSize = 12f
                setTextColor(Color.WHITE)
                typeface = getBengaliTypeface()
                background = createRoundedSolid(Color.parseColor("#01837A"), dp(20))
                setPadding(dp(10), dp(4), dp(10), dp(4))
            })
            headerRow.addView(TextView(this@MainActivity).apply {
                text = result.bookTitle
                textSize = 11f
                setTextColor(Color.parseColor("#01837A"))
                typeface = getBengaliTypeface()
                background = createRoundedBg(
                    Color.parseColor("#E8F8F7"), Color.parseColor("#01837A"), dp(1), dp(12)
                )
                setPadding(dp(8), dp(3), dp(8), dp(3))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginStart = dp(8) }
                maxWidth = dp(130)
                isSingleLine = true
                ellipsize = android.text.TextUtils.TruncateAt.END
            })
            headerRow.addView(View(this@MainActivity).apply {
                layoutParams = LinearLayout.LayoutParams(0, 0, 1f)
            })
            headerRow.addView(ImageView(this@MainActivity).apply {
                setImageResource(R.drawable.copy)
                setColorFilter(Color.parseColor("#01837A"))
                layoutParams = LinearLayout.LayoutParams(dp(22), dp(22)).apply { marginEnd = dp(8) }
                setOnClickListener { onCopy(result) }
            })
            headerRow.addView(ImageView(this@MainActivity).apply {
                setImageResource(R.drawable.share)
                setColorFilter(Color.parseColor("#01837A"))
                layoutParams = LinearLayout.LayoutParams(dp(22), dp(22))
                setOnClickListener { onShare(result) }
            })
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
