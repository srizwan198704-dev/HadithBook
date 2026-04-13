package com.srizwan.islamipedia0

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.Html
import android.text.Spanned
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
// Page State with scroll position
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
// Scroll position holder
// ─────────────────────────────────────────────────────────────────
object ScrollState {
    var booksPosition: Int = 0
    val sectionsPositions = mutableMapOf<Int, Int>()
    val hadithPositions = mutableMapOf<String, Int>()
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
// HTML / Plain-text helpers
// ─────────────────────────────────────────────────────────────────
fun String.toHtmlSpanned(): Spanned =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
        Html.fromHtml(this, Html.FROM_HTML_MODE_LEGACY)
    else
        @Suppress("DEPRECATION") Html.fromHtml(this)

fun String.stripHtml(): String =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
        Html.fromHtml(this, Html.FROM_HTML_MODE_LEGACY).toString().trim()
    else
        @Suppress("DEPRECATION") Html.fromHtml(this).toString().trim()

fun String.containsHtml(): Boolean = contains(Regex("<[a-zA-Z][^>]*>"))

// ─────────────────────────────────────────────────────────────────
// JSON null-safe helper
// ─────────────────────────────────────────────────────────────────
fun JSONObject.safeString(key: String, fallback: String = ""): String {
    if (isNull(key)) return fallback
    val v = optString(key, fallback)
    return if (v == "null") fallback else v
}

// ─────────────────────────────────────────────────────────────────
// Main Activity
// ─────────────────────────────────────────────────────────────────
class MainActivity : AppCompatActivity() {

    private val cacheDirName = "hadith_data"
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

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
    private lateinit var globalSearchInput: EditText
    private lateinit var globalSearchStatus: TextView
    private lateinit var globalSearchRecycler: RecyclerView
    private lateinit var globalSearchHint: TextView

    private var currentState: PageState = PageState.Books
    private var isSearchOpen = false
    private var isGlobalSearchOpen = false
    private val searchHandler = Handler(Looper.getMainLooper())
    private var searchRunnable: Runnable? = null
    private val globalSearchHandler = Handler(Looper.getMainLooper())
    private var globalSearchRunnable: Runnable? = null
    private val marqueeHandler = Handler(Looper.getMainLooper())

    private var currentBooks: List<BookItem> = emptyList()
    private var currentSections: List<SectionItem> = emptyList()
    private var currentHadithList: List<HadithItem> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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

        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }

        File(filesDir, cacheDirName).mkdirs()
        loadBooks()
    }

    private fun buildUI(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor("#F5F5F5"))
        }

        toolbarLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#01837A"))
            setPadding(dp(12), dp(14), dp(12), dp(14))
            elevation = dp(4).toFloat()
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
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
            textSize = 19f; setTextColor(Color.WHITE); typeface = getBengaliTypeface()
            isSingleLine = true
            ellipsize = android.text.TextUtils.TruncateAt.MARQUEE
            marqueeRepeatLimit = -1; isSelected = true; gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                .apply { setMargins(dp(10), 0, dp(10), 0) }
        }
        searchToggleBtn = ImageView(this).apply {
            setImageResource(R.drawable.search)
            layoutParams = LinearLayout.LayoutParams(dp(24), dp(24))
            setColorFilter(Color.WHITE); setOnClickListener { toggleSearch() }
        }
        toolbarLayout.addView(backButton); toolbarLayout.addView(toolbarTitleView)
        toolbarLayout.addView(searchToggleBtn); root.addView(toolbarLayout)

        searchContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.WHITE)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            elevation = dp(3).toFloat(); visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        searchInput = EditText(this).apply {
            hint = "খুঁজুন..."; typeface = getBengaliTypeface(); textSize = 16f
            setTextColor(Color.BLACK); setHintTextColor(Color.parseColor("#999999"))
            background = createRoundedBg(Color.WHITE, Color.parseColor("#01837A"), dp(2), dp(24))
            setPadding(dp(16), dp(10), dp(16), dp(10))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
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
        searchContainer.addView(searchInput); root.addView(searchContainer)

        offlineIndicator = TextView(this).apply {
            text = "⚠️ অফলাইন মোড - ক্যাশে করা ডেটা দেখানো হচ্ছে"
            textSize = 12f; setTextColor(Color.WHITE); typeface = getBengaliTypeface()
            gravity = Gravity.CENTER; setBackgroundColor(Color.parseColor("#FF9800"))
            setPadding(dp(8), dp(5), dp(8), dp(5)); visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        root.addView(offlineIndicator)

        val contentFrame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        recyclerView = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
            )
            setPadding(dp(12), dp(12), dp(12), dp(80)); clipToPadding = false
        }
        contentFrame.addView(recyclerView)

        val statusOverlay = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#F5F5F5"))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
            ); visibility = View.GONE
        }
        statusText = TextView(this).apply {
            textSize = 17f; typeface = getBengaliTypeface(); gravity = Gravity.CENTER
            setPadding(dp(24), 0, dp(24), 0)
        }
        retryButton = Button(this).apply {
            text = "আবার চেষ্টা করুন"; typeface = getBengaliTypeface()
            setTextColor(Color.WHITE)
            background = createRoundedSolid(Color.parseColor("#01837A"), dp(24))
            setPadding(dp(20), dp(10), dp(20), dp(10))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(16) }; visibility = View.GONE
        }
        statusOverlay.addView(statusText); statusOverlay.addView(retryButton)
        statusView = statusOverlay; contentFrame.addView(statusOverlay)

        fabSearchBtn = FrameLayout(this).apply {
            val size = dp(52)
            layoutParams = FrameLayout.LayoutParams(size, size, Gravity.BOTTOM or Gravity.END)
                .apply { setMargins(0, 0, dp(20), dp(20)) }
            background = createRoundedSolid(Color.parseColor("#01837A"), size / 2)
            elevation = dp(6).toFloat(); setOnClickListener { openGlobalSearch() }
        }
        fabSearchBtn.addView(ImageView(this).apply {
            setImageResource(R.drawable.search); setColorFilter(Color.WHITE)
            layoutParams = FrameLayout.LayoutParams(dp(26), dp(26), Gravity.CENTER)
        })
        contentFrame.addView(fabSearchBtn); root.addView(contentFrame)

        globalSearchOverlay = buildGlobalSearchOverlay()
        val decorFrame = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        decorFrame.addView(root); decorFrame.addView(globalSearchOverlay)
        return decorFrame
    }

    private fun buildGlobalSearchOverlay(): FrameLayout {
        val overlay = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
            ); setBackgroundColor(Color.TRANSPARENT); visibility = View.GONE
        }
        val popup = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.WHITE)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#01837A"))
            setPadding(dp(12), dp(14), dp(12), dp(14))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        header.addView(ImageView(this).apply {
            setImageResource(R.drawable.back); setColorFilter(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(dp(24), dp(24))
            setOnClickListener { closeGlobalSearch() }
        })
        header.addView(TextView(this).apply {
            text = "সম্পূর্ণ হাদিস সার্চ"; textSize = 17f
            setTextColor(Color.WHITE); typeface = getBengaliTypeface()
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                .apply { setMargins(dp(10), 0, 0, 0) }
        })
        val inputWrap = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#F0FFFE"))
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        globalSearchInput = EditText(this).apply {
            hint = "হাদিস নম্বর, শিরোনাম বা বাংলা/আরবি লিখুন..."
            typeface = getBengaliTypeface(); textSize = 16f
            setTextColor(Color.BLACK); setHintTextColor(Color.parseColor("#999999"))
            background = createRoundedBg(Color.WHITE, Color.parseColor("#01837A"), dp(2), dp(24))
            setPadding(dp(16), dp(10), dp(16), dp(10))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
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
        globalSearchStatus = TextView(this).apply {
            text = "সার্চ করতে টাইপ করুন..."; textSize = 13f
            setTextColor(Color.parseColor("#666666")); typeface = getBengaliTypeface()
            setBackgroundColor(Color.parseColor("#F9F9F9"))
            setPadding(dp(15), dp(8), dp(15), dp(8))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        val resultsFrame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        globalSearchRecycler = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
            )
            setPadding(dp(12), dp(10), dp(12), dp(12)); clipToPadding = false; visibility = View.GONE
        }
        globalSearchHint = TextView(this).apply {
            text = "🔍 সমস্ত হাদিস বই থেকে সার্চ করুন\n\nহাদিস নম্বর, বাংলা অনুবাদ বা আরবি টেক্সট দিয়ে সার্চ করা যাবে"
            textSize = 15f; typeface = getBengaliTypeface(); setTextColor(Color.parseColor("#999999"))
            gravity = Gravity.CENTER; setPadding(dp(24), dp(40), dp(24), dp(40))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP
            )
        }
        resultsFrame.addView(globalSearchRecycler); resultsFrame.addView(globalSearchHint)
        popup.addView(header); popup.addView(inputWrap)
        popup.addView(globalSearchStatus); popup.addView(resultsFrame)
        overlay.addView(popup)
        return overlay
    }

    private fun updateToolbar(title: String) { toolbarTitleView.text = title }

    private fun showLoading() {
        recyclerView.visibility = View.GONE; statusView.visibility = View.VISIBLE
        statusText.text = "লোড হচ্ছে..."; statusText.setTextColor(Color.parseColor("#01837A"))
        retryButton.visibility = View.GONE
    }

    private fun showError(message: String, retry: (() -> Unit)? = null) {
        recyclerView.visibility = View.GONE; statusView.visibility = View.VISIBLE
        statusText.text = "❌ $message"; statusText.setTextColor(Color.parseColor("#E74C3C"))
        retryButton.visibility = if (retry != null) View.VISIBLE else View.GONE
        retry?.let { r -> retryButton.setOnClickListener { r() } }
    }

    private fun showContent() {
        statusView.visibility = View.GONE
        recyclerView.visibility = View.VISIBLE
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
        getCachedData(cacheKey)?.let { cached ->
            withContext(Dispatchers.Main) { offlineIndicator.visibility = View.GONE }
            return cached
        }
        return withContext(Dispatchers.IO) {
            try {
                val text = URL(url).readText()
                cacheData(cacheKey, text)
                withContext(Dispatchers.Main) { offlineIndicator.visibility = View.GONE }
                text
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { offlineIndicator.visibility = View.VISIBLE }
                throw e
            }
        }
    }

    private fun toBangla(num: Int): String {
        val d = charArrayOf('০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯')
        return num.toString().map { if (it.isDigit()) d[it - '0'] else it }.joinToString("")
    }

    // ── Save & Restore Scroll Position ────────────────────────────
    private fun saveScrollPosition() {
        val layoutManager = recyclerView.layoutManager as LinearLayoutManager
        val position = layoutManager.findFirstVisibleItemPosition()
        when (currentState) {
            is PageState.Books -> ScrollState.booksPosition = position
            is PageState.Sections -> {
                val state = currentState as PageState.Sections
                ScrollState.sectionsPositions[state.bookId] = position
            }
            is PageState.Hadith -> {
                val state = currentState as PageState.Hadith
                ScrollState.hadithPositions["${state.bookId}_${state.sectionId}"] = position
            }
        }
    }

    private fun restoreScrollPosition() {
        val position = when (currentState) {
            is PageState.Books -> ScrollState.booksPosition
            is PageState.Sections -> {
                val state = currentState as PageState.Sections
                ScrollState.sectionsPositions[state.bookId] ?: 0
            }
            is PageState.Hadith -> {
                val state = currentState as PageState.Hadith
                ScrollState.hadithPositions["${state.bookId}_${state.sectionId}"] ?: 0
            }
        }
        if (position > 0 && position < (recyclerView.adapter?.itemCount ?: 0)) {
            recyclerView.scrollToPosition(position)
        }
    }

    // ── Load Books ────────────────────────────────────────────────
    private fun loadBooks() {
        saveScrollPosition()
        currentState = PageState.Books
        updateToolbar("হাদিস সমগ্র")
        closeSearchSilently()

        val memBooks = HadithCache.books
        if (memBooks != null) {
            currentBooks = memBooks
            showContent()
            recyclerView.adapter = BookAdapter(memBooks) { book -> loadSections(book.id, book.titleEn) }
            restoreScrollPosition()
            return
        }

        showLoading()
        scope.launch {
            try {
                val json = fetchJson(
                    "https://cdn.jsdelivr.net/gh/SunniPedia/sunnipedia@main/hadith-books/book/book-title.json",
                    "hadith_books_list"
                )
                val books = parseBooks(json)
                HadithCache.books = books
                currentBooks = books
                showContent()
                recyclerView.adapter = BookAdapter(books) { book -> loadSections(book.id, book.titleEn) }
                restoreScrollPosition()
            } catch (e: Exception) {
                showError("বই লোড করতে সমস্যা হয়েছে") { loadBooks() }
            }
        }
    }

    private fun parseBooks(json: String): List<BookItem> {
        val arr = JSONArray(json)
        return (0 until arr.length()).map { arr.getJSONObject(it) }.map { o ->
            BookItem(
                id           = o.optInt("id"),
                sequence     = o.optInt("sequence"),
                titleEn      = o.safeString("title_en", o.safeString("title", "")),
                titleAr      = o.safeString("title_ar"),
                totalSection = o.optInt("total_section"),
                totalHadith  = o.optInt("total_hadith")
            )
        }.sortedBy { it.sequence }
    }

    // ── Load Sections ─────────────────────────────────────────────
    private fun loadSections(bookId: Int, bookTitle: String) {
        saveScrollPosition()
        currentState = PageState.Sections(bookId, bookTitle)
        updateToolbar(bookTitle)
        closeSearchSilently()

        val memSections = HadithCache.sections[bookId]
        if (memSections != null) {
            currentSections = memSections
            showContent()
            recyclerView.adapter = SectionAdapter(memSections) { section ->
                loadHadith(bookId, section.id, bookTitle, section.title)
            }
            restoreScrollPosition()
            return
        }

        showLoading()
        scope.launch {
            try {
                val json = fetchJson(
                    "https://cdn.jsdelivr.net/gh/SunniPedia/sunnipedia@main/hadith-books/book/$bookId/title.json",
                    "sections_$bookId"
                )
                val sections = parseSections(json)
                HadithCache.sections[bookId] = sections
                currentSections = sections
                showContent()
                recyclerView.adapter = SectionAdapter(sections) { section ->
                    loadHadith(bookId, section.id, bookTitle, section.title)
                }
                restoreScrollPosition()
            } catch (e: Exception) {
                showError("অধ্যায় লোড করতে সমস্যা হয়েছে") { loadSections(bookId, bookTitle) }
            }
        }
    }

    private fun parseSections(json: String): List<SectionItem> {
        val arr = JSONArray(json)
        return (0 until arr.length()).map { arr.getJSONObject(it) }.map { o ->
            val rawTitle   = o.safeString("title", o.safeString("title_en", ""))
            val rawTitleAr = o.safeString("title_ar")
            SectionItem(
                id          = o.optInt("id"),
                sequence    = o.optInt("sequence"),
                title       = rawTitle,
                titleAr     = rawTitleAr,
                totalHadith = o.optInt("total_hadith"),
                rangeStart  = o.optInt("range_start"),
                rangeEnd    = o.optInt("range_end")
            )
        }.sortedBy { it.sequence }
    }

    // ── Load Hadith ───────────────────────────────────────────────
    private fun loadHadith(bookId: Int, sectionId: Int, bookTitle: String, sectionTitle: String) {
        saveScrollPosition()
        currentState = PageState.Hadith(bookId, sectionId, bookTitle, sectionTitle)
        updateToolbar(sectionTitle)
        closeSearchSilently()
        val key = "${bookId}_$sectionId"

        val memHadith = HadithCache.hadith[key]
        if (memHadith != null) {
            currentHadithList = memHadith
            showContent()
            recyclerView.adapter = HadithAdapter(memHadith,
                onCopy  = { h -> copyHadith(h, bookTitle, sectionTitle) },
                onShare = { h -> shareHadith(h, bookTitle, sectionTitle) }
            )
            restoreScrollPosition()
            return
        }

        showLoading()
        scope.launch {
            try {
                val json = fetchJson(
                    "https://cdn.jsdelivr.net/gh/SunniPedia/sunnipedia@main/hadith-books/book/$bookId/hadith/$sectionId.json",
                    "hadith_${bookId}_$sectionId"
                )
                val hadithList = parseHadith(json)
                HadithCache.hadith[key] = hadithList
                currentHadithList = hadithList
                showContent()
                recyclerView.adapter = HadithAdapter(hadithList,
                    onCopy  = { h -> copyHadith(h, bookTitle, sectionTitle) },
                    onShare = { h -> shareHadith(h, bookTitle, sectionTitle) }
                )
                restoreScrollPosition()
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
                hadithNumber  = o.optInt("hadith_number"),
                title         = o.safeString("title"),
                descriptionAr = o.safeString("description_ar"),
                description   = o.safeString("description")
            )
        }.sortedBy { it.hadithNumber }
    }

    // ── Search ────────────────────────────────────────────────────
    private fun toggleSearch() {
        if (isSearchOpen) closeSearch() else {
            isSearchOpen = true
            searchContainer.visibility = View.VISIBLE
            searchInput.requestFocus()
        }
    }

    private fun closeSearch() {
        isSearchOpen = false
        searchContainer.visibility = View.GONE
        searchInput.setText("")
        restoreFullList()
    }

    private fun closeSearchSilently() {
        isSearchOpen = false
        searchHandler.removeCallbacks(searchRunnable ?: return)
        searchContainer.visibility = View.GONE
        searchInput.setText("")
    }

    private fun restoreFullList() {
        showContent()
        when (val s = currentState) {
            is PageState.Books -> recyclerView.adapter = BookAdapter(currentBooks) { book ->
                loadSections(book.id, book.titleEn)
            }
            is PageState.Sections -> recyclerView.adapter = SectionAdapter(currentSections) { section ->
                loadHadith(s.bookId, section.id, s.bookTitle, section.title)
            }
            is PageState.Hadith -> recyclerView.adapter = HadithAdapter(
                currentHadithList,
                onCopy  = { h -> copyHadith(h, s.bookTitle, s.sectionTitle) },
                onShare = { h -> shareHadith(h, s.bookTitle, s.sectionTitle) }
            )
        }
    }

    private fun performSearch(query: String) {
        showContent()
        val term = query.lowercase().trim()
        if (term.isBlank()) { restoreFullList(); return }

        when (val s = currentState) {
            is PageState.Books -> {
                val filtered = currentBooks.filter { b ->
                    b.titleEn.lowercase().contains(term) || b.titleAr.contains(term)
                }
                recyclerView.adapter = BookAdapter(filtered) { book -> loadSections(book.id, book.titleEn) }
                if (filtered.isEmpty()) showEmptySearchResult()
            }
            is PageState.Sections -> {
                val filtered = currentSections.filter { sec ->
                    sec.title.lowercase().contains(term) || sec.titleAr.contains(term)
                }
                recyclerView.adapter = SectionAdapter(filtered) { section ->
                    loadHadith(s.bookId, section.id, s.bookTitle, section.title)
                }
                if (filtered.isEmpty()) showEmptySearchResult()
            }
            is PageState.Hadith -> {
                val filtered = currentHadithList.filter { h ->
                    h.hadithNumber.toString().contains(term) ||
                    h.title.stripHtml().lowercase().contains(term) ||
                    h.description.stripHtml().lowercase().contains(term) ||
                    h.descriptionAr.contains(term)
                }
                recyclerView.adapter = HadithAdapter(
                    filtered,
                    onCopy  = { h -> copyHadith(h, s.bookTitle, s.sectionTitle) },
                    onShare = { h -> shareHadith(h, s.bookTitle, s.sectionTitle) }
                )
                if (filtered.isEmpty()) showEmptySearchResult()
            }
        }
    }

    private fun showEmptySearchResult() {
        Toast.makeText(this, "কোনো ফলাফল পাওয়া যায়নি", Toast.LENGTH_SHORT).show()
    }

    // ── Global Search ─────────────────────────────────────────────
    private fun openGlobalSearch() {
        isGlobalSearchOpen = true; globalSearchOverlay.visibility = View.VISIBLE
        globalSearchInput.requestFocus()
    }

    private fun closeGlobalSearch() {
        isGlobalSearchOpen = false
        globalSearchHandler.removeCallbacks(globalSearchRunnable ?: Runnable {})
        globalSearchOverlay.visibility = View.GONE; globalSearchInput.setText("")
        globalSearchStatus.text = "সার্চ করতে টাইপ করুন..."
        showGlobalHint("🔍 সমস্ত হাদিস বই থেকে সার্চ করুন\n\nহাদিস নম্বর, বাংলা অনুবাদ বা আরবি টেক্সট দিয়ে সার্চ করা যাবে")
    }

    private fun showGlobalHint(msg: String) {
        globalSearchHint.text = msg; globalSearchHint.visibility = View.VISIBLE
        globalSearchRecycler.visibility = View.GONE; globalSearchRecycler.adapter = null
    }

    private fun performGlobalSearchFromCache(query: String) {
        val books = HadithCache.books
        if (books.isNullOrEmpty()) {
            globalSearchStatus.text = "⚠️ ক্যাশে কোনো ডাটা নেই।"
            showGlobalHint("প্রথমে বই লিস্ট থেকে কিছু হাদিস খুলুন, তারপর সার্চ করুন।"); return
        }
        globalSearchHint.visibility = View.GONE; globalSearchRecycler.visibility = View.GONE
        globalSearchStatus.text = "🔍 ক্যাশে অনুসন্ধান চলছে..."

        scope.launch(Dispatchers.Default) {
            val results = mutableListOf<GlobalSearchResult>()
            val term = query.lowercase()
            var totalHadith = 0; var booksSearched = 0

            for (book in books) {
                val sections = HadithCache.sections[book.id] ?: continue
                var bookHasData = false
                for (section in sections) {
                    val k = "${book.id}_${section.id}"
                    val hadithList = HadithCache.hadith[k] ?: continue
                    bookHasData = true; totalHadith += hadithList.size
                    hadithList.filter { h ->
                        h.hadithNumber.toString().contains(term) ||
                        h.title.stripHtml().lowercase().contains(term) ||
                        h.description.stripHtml().lowercase().contains(term) ||
                        h.descriptionAr.contains(term)
                    }.forEach { h ->
                        results.add(
                            GlobalSearchResult(h, book.titleEn.ifBlank { book.titleAr },
                                book.id, section.title, section.id)
                        )
                    }
                }
                if (bookHasData) {
                    booksSearched++
                    val snap = booksSearched; val rSnap = results.size
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
                        globalSearchStatus.text = "মোট ${toBangla(totalHadith)} টি হাদিস — কোনো ফলাফল নেই"
                        showGlobalHint("😔 \"$query\" এর জন্য কোনো হাদিস পাওয়া যায়নি।")
                    }
                    else -> {
                        globalSearchStatus.text = "✅ ${toBangla(results.size)} টি হাদিস পাওয়া গেছে"
                        globalSearchHint.visibility = View.GONE
                        globalSearchRecycler.visibility = View.VISIBLE
                        globalSearchRecycler.adapter = GlobalSearchAdapter(results,
                            onCopy  = { r -> copyHadith(r.hadith, r.bookTitle, r.sectionTitle) },
                            onShare = { r -> shareHadith(r.hadith, r.bookTitle, r.sectionTitle) })
                    }
                }
            }
        }
    }

    // ── Copy / Share ──────────────────────────────────────────────
    private fun copyHadith(hadith: HadithItem, bookTitle: String, sectionTitle: String) {
        val text = buildPlainText(hadith, bookTitle, sectionTitle, withAppLink = false)
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("হাদিস", text))
        Toast.makeText(this, "কপি করা হয়েছে!", Toast.LENGTH_SHORT).show()
    }

    private fun shareHadith(hadith: HadithItem, bookTitle: String, sectionTitle: String) {
        val text = buildPlainText(hadith, bookTitle, sectionTitle, withAppLink = true)
        startActivity(Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) },
            "শেয়ার করুন"
        ))
    }

    private fun buildPlainText(
        hadith: HadithItem,
        bookTitle: String,
        sectionTitle: String,
        withAppLink: Boolean
    ): String = listOfNotNull(
        bookTitle.ifBlank { null },
        sectionTitle.ifBlank { null },
        if (hadith.hadithNumber > 0) "হাদিস নং: ${toBangla(hadith.hadithNumber)}" else null,
        hadith.title.stripHtml().ifBlank { null },
        hadith.descriptionAr.stripHtml().ifBlank { null },
        hadith.description.stripHtml().ifBlank { null },
        if (withAppLink) "অ্যাপ: ইসলামী বিশ্বকোষ ও আল হাদিস\nhttps://play.google.com/store/apps/details?id=com.srizwan.islamipedia" else null
    ).joinToString("\n")

    // ── Back ──────────────────────────────────────────────────────
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
    override fun onBackPressed() { handleBack() }

    override fun onDestroy() {
        super.onDestroy(); scope.cancel()
        marqueeHandler.removeCallbacksAndMessages(null)
        searchHandler.removeCallbacksAndMessages(null)
        globalSearchHandler.removeCallbacksAndMessages(null)
    }

    // ── Helpers ───────────────────────────────────────────────────
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
            setColor(fillColor); setStroke(strokeWidth, strokeColor)
            cornerRadius = radius.toFloat()
        }

    private fun createRoundedSolid(fillColor: Int, radius: Int): android.graphics.drawable.Drawable =
        android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            setColor(fillColor); cornerRadius = radius.toFloat()
        }

    private fun TextView.setSmartText(raw: String) {
        if (raw.containsHtml()) setText(raw.toHtmlSpanned()) else text = raw
        setTextIsSelectable(true)
    }

    // ─────────────────────────────────────────────────────────────
    // Book Adapter
    // ─────────────────────────────────────────────────────────────
    inner class BookAdapter(
        private val items: List<BookItem>,
        private val onClick: (BookItem) -> Unit
    ) : RecyclerView.Adapter<BookAdapter.VH>() {

        inner class VH(val card: LinearLayout) : RecyclerView.ViewHolder(card)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
            LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(14) }
                background = createRoundedBg(Color.WHITE, Color.parseColor("#01837A"), dp(2), dp(10))
                elevation = dp(3).toFloat(); setPadding(dp(16), dp(14), dp(16), dp(14))
            }
        )

        override fun onBindViewHolder(holder: VH, position: Int) {
            val book = items[position]
            holder.card.removeAllViews()
            holder.card.setOnClickListener { onClick(book) }

            // Header row with badge
            val headerRow = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            // Bangla number badge
            val badge = TextView(this@MainActivity).apply {
                text = toBangla(position + 1)
                textSize = 13f
                setTextColor(Color.WHITE)
                typeface = getBengaliTypeface()
                background = createRoundedSolid(Color.parseColor("#01837A"), dp(16))
                gravity = Gravity.CENTER
                setPadding(dp(10), dp(4), dp(10), dp(4))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = dp(10) }
            }
            headerRow.addView(badge)

            // Title
            val displayTitle = book.titleEn.ifBlank { book.titleAr }
            if (displayTitle.isNotBlank()) {
                headerRow.addView(TextView(this@MainActivity).apply {
                    text = displayTitle
                    textSize = 17f
                    setTextColor(Color.parseColor("#01837A"))
                    typeface = getBengaliTypeface()
                    layoutParams = LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                    )
                })
            }
            holder.card.addView(headerRow)

            // আরবি শিরোনাম
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

            val hasSectionCount = book.totalSection > 0
            val hasHadithCount  = book.totalHadith > 0
            if (hasSectionCount || hasHadithCount) {
                val meta = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = dp(8) }
                }
                if (hasSectionCount) {
                    meta.addView(TextView(this@MainActivity).apply {
                        text = "📚 ${toBangla(book.totalSection)} টি অধ্যায়"
                        textSize = 13f; setTextColor(Color.parseColor("#666666"))
                        typeface = getBengaliTypeface()
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    })
                }
                if (hasHadithCount) {
                    meta.addView(TextView(this@MainActivity).apply {
                        text = "📖 ${toBangla(book.totalHadith)} টি হাদিস"
                        textSize = 13f; setTextColor(Color.parseColor("#666666"))
                        typeface = getBengaliTypeface()
                    })
                }
                holder.card.addView(meta)
            }
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

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
            LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(14) }
                background = createRoundedBg(Color.WHITE, Color.parseColor("#01837A"), dp(2), dp(10))
                elevation = dp(3).toFloat(); setPadding(dp(16), dp(14), dp(16), dp(14))
            }
        )

        override fun onBindViewHolder(holder: VH, position: Int) {
            val section = items[position]
            holder.card.removeAllViews()
            holder.card.setOnClickListener { onClick(section) }

            // Header row with badge
            val headerRow = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            // Bangla number badge
            val badge = TextView(this@MainActivity).apply {
                text = toBangla(position + 1)
                textSize = 13f
                setTextColor(Color.WHITE)
                typeface = getBengaliTypeface()
                background = createRoundedSolid(Color.parseColor("#01837A"), dp(16))
                gravity = Gravity.CENTER
                setPadding(dp(10), dp(4), dp(10), dp(4))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = dp(10) }
            }
            headerRow.addView(badge)

            // Title
            if (section.title.isNotBlank()) {
                headerRow.addView(TextView(this@MainActivity).apply {
                    text = section.title
                    textSize = 17f
                    setTextColor(Color.parseColor("#01837A"))
                    typeface = getBengaliTypeface()
                    layoutParams = LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                    )
                })
            }
            holder.card.addView(headerRow)

            // আরবি শিরোনাম
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

            val hasHadithCount = section.totalHadith > 0
            val hasRange       = section.rangeStart > 0 && section.rangeEnd > 0
            if (hasHadithCount || hasRange) {
                val meta = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = dp(8) }
                }
                if (hasHadithCount) {
                    meta.addView(TextView(this@MainActivity).apply {
                        text = "📖 মোট ${toBangla(section.totalHadith)} টি হাদিস"
                        textSize = 13f; setTextColor(Color.parseColor("#666666"))
                        typeface = getBengaliTypeface()
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    })
                }
                if (hasRange) {
                    meta.addView(TextView(this@MainActivity).apply {
                        text = "🔢 ব্যাপ্তি: ${toBangla(section.rangeStart)}-${toBangla(section.rangeEnd)}"
                        textSize = 13f; setTextColor(Color.parseColor("#666666"))
                        typeface = getBengaliTypeface()
                    })
                }
                holder.card.addView(meta)
            }
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

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
            LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(14) }
                background = createRoundedBg(Color.WHITE, Color.parseColor("#01837A"), dp(2), dp(10))
                elevation = dp(3).toFloat(); setPadding(dp(16), dp(14), dp(16), dp(14))
            }
        )

        override fun onBindViewHolder(holder: VH, position: Int) {
            val hadith = items[position]
            holder.card.removeAllViews()

            val headerRow = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            if (hadith.hadithNumber > 0) {
                headerRow.addView(TextView(this@MainActivity).apply {
                    text = "হাদিস নং: ${toBangla(hadith.hadithNumber)}"
                    textSize = 13f; setTextColor(Color.WHITE); typeface = getBengaliTypeface()
                    background = createRoundedSolid(Color.parseColor("#01837A"), dp(20))
                    setPadding(dp(12), dp(5), dp(12), dp(5))
                })
            }
            headerRow.addView(View(this@MainActivity).apply {
                layoutParams = LinearLayout.LayoutParams(0, 0, 1f)
            })
            headerRow.addView(ImageView(this@MainActivity).apply {
                setImageResource(R.drawable.copy); setColorFilter(Color.parseColor("#01837A"))
                layoutParams = LinearLayout.LayoutParams(dp(24), dp(24)).apply { marginEnd = dp(10) }
                setOnClickListener { onCopy(hadith) }
            })
            headerRow.addView(ImageView(this@MainActivity).apply {
                setImageResource(R.drawable.share); setColorFilter(Color.parseColor("#01837A"))
                layoutParams = LinearLayout.LayoutParams(dp(24), dp(24))
                setOnClickListener { onShare(hadith) }
            })
            holder.card.addView(headerRow)

            if (hadith.title.isNotBlank()) {
                holder.card.addView(TextView(this@MainActivity).apply {
                    textSize = 18f; setTextColor(Color.parseColor("#01837A"))
                    typeface = getBengaliTypeface()
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = dp(10) }
                    setSmartText(hadith.title)
                })
            }

            if (hadith.descriptionAr.isNotBlank()) {
                holder.card.addView(TextView(this@MainActivity).apply {
                    textSize = 20f; setTextColor(Color.parseColor("#333333"))
                    typeface = getArabicTypeface(); gravity = Gravity.END
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = dp(12); bottomMargin = dp(6) }
                    setSmartText(hadith.descriptionAr)
                })
            }

            if (hadith.description.isNotBlank()) {
                holder.card.addView(TextView(this@MainActivity).apply {
                    textSize = 18f; setTextColor(Color.parseColor("#444444"))
                    typeface = getBengaliTypeface()
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = dp(8) }
                    setSmartText(hadith.description)
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

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
            LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(12) }
                background = createRoundedBg(Color.WHITE, Color.parseColor("#01837A"), dp(2), dp(10))
                elevation = dp(3).toFloat(); setPadding(dp(14), dp(12), dp(14), dp(12))
            }
        )

        override fun onBindViewHolder(holder: VH, position: Int) {
            val result = items[position]; val hadith = result.hadith
            holder.card.removeAllViews()

            val headerRow = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            if (hadith.hadithNumber > 0) {
                headerRow.addView(TextView(this@MainActivity).apply {
                    text = "হাদিস নং: ${toBangla(hadith.hadithNumber)}"
                    textSize = 12f; setTextColor(Color.WHITE); typeface = getBengaliTypeface()
                    background = createRoundedSolid(Color.parseColor("#01837A"), dp(20))
                    setPadding(dp(10), dp(4), dp(10), dp(4))
                })
            }
            if (result.bookTitle.isNotBlank()) {
                headerRow.addView(TextView(this@MainActivity).apply {
                    text = result.bookTitle; textSize = 11f
                    setTextColor(Color.parseColor("#01837A")); typeface = getBengaliTypeface()
                    background = createRoundedBg(
                        Color.parseColor("#E8F8F7"), Color.parseColor("#01837A"), dp(1), dp(12)
                    )
                    setPadding(dp(8), dp(3), dp(8), dp(3))
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { marginStart = dp(8) }
                    maxWidth = dp(130); isSingleLine = true
                    ellipsize = android.text.TextUtils.TruncateAt.END
                })
            }
            headerRow.addView(View(this@MainActivity).apply {
                layoutParams = LinearLayout.LayoutParams(0, 0, 1f)
            })
            headerRow.addView(ImageView(this@MainActivity).apply {
                setImageResource(R.drawable.copy); setColorFilter(Color.parseColor("#01837A"))
                layoutParams = LinearLayout.LayoutParams(dp(22), dp(22)).apply { marginEnd = dp(8) }
                setOnClickListener { onCopy(result) }
            })
            headerRow.addView(ImageView(this@MainActivity).apply {
                setImageResource(R.drawable.share); setColorFilter(Color.parseColor("#01837A"))
                layoutParams = LinearLayout.LayoutParams(dp(22), dp(22))
                setOnClickListener { onShare(result) }
            })
            holder.card.addView(headerRow)

            if (hadith.title.isNotBlank()) {
                holder.card.addView(TextView(this@MainActivity).apply {
                    textSize = 15f; setTextColor(Color.parseColor("#01837A"))
                    typeface = getBengaliTypeface()
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = dp(8) }
                    setSmartText(hadith.title)
                })
            }

            if (hadith.descriptionAr.isNotBlank()) {
                holder.card.addView(TextView(this@MainActivity).apply {
                    textSize = 18f; setTextColor(Color.parseColor("#333333"))
                    typeface = getArabicTypeface(); gravity = Gravity.END
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = dp(10); bottomMargin = dp(6) }
                    setSmartText(hadith.descriptionAr)
                })
            }

            if (hadith.description.isNotBlank()) {
                holder.card.addView(TextView(this@MainActivity).apply {
                    textSize = 14f; setTextColor(Color.parseColor("#444444"))
                    typeface = getBengaliTypeface()
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = dp(8) }
                    setSmartText(hadith.description)
                })
            }
        }

        override fun getItemCount() = items.size
    }
}