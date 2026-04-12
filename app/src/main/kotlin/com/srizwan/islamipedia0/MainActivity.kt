package com.srizwan.islamipedia0

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.io.File
import java.security.MessageDigest

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private val cacheDirName = "hadith_data"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // স্ট্যাটাসবার এবং নেভিগেশন বার ভিজিবল রাখার জন্য সেটিংস
        WindowCompat.setDecorFitsSystemWindows(window, true)
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.show(WindowInsetsCompat.Type.systemBars())

        webView = findViewById(R.id.webView)
        setupWebView()

        // ক্যাশে ডিরেক্টরি তৈরি
        val cacheDir = File(filesDir, cacheDirName)
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }

        // অ্যাপ লোড করুন
        loadAppContent()
    }

    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            useWideViewPort = true
            loadWithOverviewMode = true
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                if (!isNetworkAvailable()) {
                    showOfflineMessage()
                }
            }
        }

        webView.webChromeClient = WebChromeClient()
        webView.addJavascriptInterface(AndroidJavaScriptInterface(), "AndroidApp")
    }

    private fun loadAppContent() {
        val htmlContent = generateMainHTML()
        val htmlFile = File(filesDir, "index.html")
        htmlFile.writeText(htmlContent)
        webView.loadUrl("file://${htmlFile.absolutePath}")
    }

    private fun generateMainHTML(): String {
        return """
<!DOCTYPE html>
<html lang="bn">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no, viewport-fit=cover">
    <meta name="description" content="ইসলামী বিশ্বকোষ ও আল হাদিস">
    <title>হাদিস সমগ্র</title>
    <style>
        @font-face {
            font-family: 'SolaimanLipi';
            src: url('file:///android_asset/fonts/SolaimanLipi.ttf') format('truetype');
            font-display: swap;
        }
        
        @font-face {
            font-family: 'Noorehuda';
            src: url('file:///android_asset/fonts/noorehuda.ttf') format('truetype');
            font-display: swap;
        }
        
        * {
            margin: 0;
            padding: 0;
            box-sizing: border-box;
        }
        
        body {
            font-family: 'SolaimanLipi', sans-serif;
            background: #f5f5f5;
            color: #333;
            line-height: 1.6;
            -webkit-tap-highlight-color: transparent;
            padding-bottom: env(safe-area-inset-bottom);
        }
        
        .toolbar {
            background: #01837A;
            color: white;
            padding: 15px;
            display: flex;
            align-items: center;
            box-shadow: 0 2px 5px rgba(0,0,0,0.1);
            position: sticky;
            top: 0;
            z-index: 100;
        }
        
        .toolbar-icon {
            width: 24px;
            height: 24px;
            cursor: pointer;
        }
        
        .toolbar-title {
            flex: 1;
            text-align: center;
            font-size: 20px;
            font-weight: bold;
            margin: 0 15px;
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
        }
        
        .search-container {
            background: white;
            padding: 10px 15px;
            box-shadow: 0 2px 5px rgba(0,0,0,0.1);
            display: none;
            position: sticky;
            top: 60px;
            z-index: 99;
        }
        
        .search-container.active {
            display: block;
        }
        
        .search-input {
            width: 100%;
            padding: 10px 15px;
            border: 2px solid #01837A;
            border-radius: 25px;
            font-family: 'SolaimanLipi', sans-serif;
            font-size: 16px;
            outline: none;
        }
        
        .content-container {
            padding: 15px;
            max-width: 800px;
            margin: 0 auto;
        }
        
        .book-box {
            background: white;
            border-radius: 10px;
            padding: 20px;
            margin-bottom: 15px;
            box-shadow: 0 3px 10px rgba(0,0,0,0.1);
            border: 2px solid #01837A;
            position: relative;
            cursor: pointer;
        }
        
        .book-id-badge {
            position: absolute;
            top: -12px;
            left: -12px;
            background: #01837A;
            color: white;
            width: 35px;
            height: 35px;
            border-radius: 50%;
            display: flex;
            align-items: center;
            justify-content: center;
            font-weight: bold;
            font-size: 16px;
        }
        
        .book-title-en {
            font-size: 18px;
            font-weight: bold;
            color: #01837A;
            margin-bottom: 8px;
            padding-left: 20px;
        }
        
        .book-title-ar {
            font-family: 'Noorehuda', sans-serif;
            font-size: 18px;
            text-align: right;
            color: #333;
            margin: 15px 0;
            direction: rtl;
        }
        
        .book-meta {
            display: flex;
            justify-content: space-between;
            padding-top: 10px;
            border-top: 1px dashed #ddd;
            font-size: 14px;
            color: #666;
        }
        
        .hadith-box {
            background: white;
            border-radius: 10px;
            padding: 20px;
            margin-bottom: 15px;
            box-shadow: 0 3px 10px rgba(0,0,0,0.1);
            border: 2px solid #01837A;
        }
        
        .hadith-header {
            display: flex;
            justify-content: space-between;
            align-items: center;
            margin-bottom: 15px;
        }
        
        .hadith-number {
            background: #01837A;
            color: white;
            padding: 5px 15px;
            border-radius: 20px;
            font-weight: bold;
            font-size: 14px;
        }
        
        .action-buttons {
            display: flex;
            gap: 10px;
        }
        
        .action-icon {
            width: 24px;
            height: 24px;
            cursor: pointer;
        }
        
        .hadith-title {
            font-size: 18px;
            font-weight: bold;
            color: #01837A;
            margin: 12px 0;
        }
        
        .hadith-description-ar {
            font-family: 'Noorehuda', sans-serif;
            font-size: 20px;
            color: #333;
            margin: 15px 0;
            text-align: right;
            direction: rtl;
        }
        
        .hadith-description {
            font-size: 16px;
            color: #444;
            margin: 15px 0;
            text-align: justify;
        }
        
        .loading-state {
            text-align: center;
            padding: 30px;
            color: #01837A;
            font-size: 18px;
        }
        
        .error-state {
            text-align: center;
            padding: 30px;
            color: #e74c3c;
            font-size: 16px;
        }
        
        .offline-indicator {
            background: #ff9800;
            color: white;
            text-align: center;
            padding: 5px;
            font-size: 12px;
            display: none;
        }
        
        .retry-button {
            background: #01837A;
            color: white;
            border: none;
            padding: 10px 20px;
            border-radius: 25px;
            font-family: 'SolaimanLipi', sans-serif;
            font-size: 14px;
            margin-top: 15px;
            cursor: pointer;
        }
    </style>
</head>
<body>
    <div class="toolbar">
        <img src="file:///android_asset/images/back.png" class="toolbar-icon" id="backButton" onclick="handleBack()" alt="Back">
        <div class="toolbar-title" id="pageTitle">হাদিস সমগ্র</div>
        <img src="file:///android_asset/images/search.png" class="toolbar-icon" id="searchToggle" onclick="toggleSearch()" alt="Search">
    </div>
    
    <div class="search-container" id="searchContainer">
        <input type="text" class="search-input" id="searchInput" placeholder="খুঁজুন..." oninput="handleSearch(this.value)">
    </div>
    
    <div class="offline-indicator" id="offlineIndicator">
        ⚠️ অফলাইন মোড - ক্যাশে করা ডেটা দেখানো হচ্ছে
    </div>
    
    <div class="content-container" id="contentContainer">
        <div class="loading-state">লোড হচ্ছে...</div>
    </div>

    <script>
        let currentState = {
            page: 'books',
            bookId: null,
            sectionId: null,
            bookTitle: '',
            sectionTitle: ''
        };
        
        let cachedData = {
            books: null,
            sections: {},
            hadith: {}
        };
        
        let currentDisplayData = [];
        
        const toBanglaNumber = (num) => {
            if (num === null || num === undefined || isNaN(num)) return '০';
            const banglaDigits = ['০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯'];
            return num.toString().replace(/\d/g, digit => banglaDigits[digit]);
        };
        
        const safeString = (str, fallback) => {
            return str || fallback || 'তথ্য নেই';
        };
        
        const updateToolbar = (title, showBack) => {
            document.getElementById('pageTitle').textContent = title;
            document.getElementById('backButton').style.display = showBack ? 'block' : 'none';
        };
        
        const showLoading = () => {
            document.getElementById('contentContainer').innerHTML = '<div class="loading-state">লোড হচ্ছে...</div>';
        };
        
        const showError = (message, retryCallback) => {
            const container = document.getElementById('contentContainer');
            let buttonHtml = '';
            if (retryCallback) {
                buttonHtml = '<button class="retry-button" onclick="(' + retryCallback.toString() + ')()">আবার চেষ্টা করুন</button>';
            }
            container.innerHTML = '<div class="error-state">❌ ' + message + buttonHtml + '</div>';
        };
        
        async function fetchData(url, cacheKey) {
            try {
                if (typeof AndroidApp !== 'undefined') {
                    const cached = AndroidApp.getCachedData(cacheKey);
                    if (cached && cached.length > 0) {
                        document.getElementById('offlineIndicator').style.display = 'block';
                        return JSON.parse(cached);
                    }
                }
                
                const response = await fetch(url);
                if (!response.ok) throw new Error('Network error');
                const data = await response.json();
                
                if (typeof AndroidApp !== 'undefined') {
                    AndroidApp.cacheData(cacheKey, JSON.stringify(data));
                }
                
                document.getElementById('offlineIndicator').style.display = 'none';
                return data;
                
            } catch (error) {
                if (typeof AndroidApp !== 'undefined') {
                    const cached = AndroidApp.getCachedData(cacheKey);
                    if (cached && cached.length > 0) {
                        document.getElementById('offlineIndicator').style.display = 'block';
                        return JSON.parse(cached);
                    }
                }
                throw error;
            }
        }
        
        async function loadBooks() {
            currentState = { page: 'books', bookId: null, sectionId: null, bookTitle: '', sectionTitle: '' };
            updateToolbar('হাদিস সমগ্র', false);
            showLoading();
            
            try {
                const data = await fetchData(
                    'https://cdn.jsdelivr.net/gh/SunniPedia/sunnipedia@main/hadith-books/book/book-title.json',
                    'hadith_books_list'
                );
                
                cachedData.books = data;
                currentDisplayData = data;
                renderBooks(data);
                
            } catch (error) {
                showError('বই লোড করতে সমস্যা হয়েছে', loadBooks);
            }
        }
        
        function renderBooks(books) {
            const container = document.getElementById('contentContainer');
            
            if (!books || books.length === 0) {
                container.innerHTML = '<div class="error-state">কোন বই পাওয়া যায়নি</div>';
                return;
            }
            
            container.innerHTML = '';
            
            books.sort((a, b) => (a.sequence || 0) - (b.sequence || 0)).forEach(book => {
                const box = document.createElement('div');
                box.className = 'book-box';
                box.onclick = () => loadSections(book.id, safeString(book.title, 'বই'));
                box.innerHTML = 
                    '<div class="book-id-badge">' + toBanglaNumber(book.sequence || 0) + '</div>' +
                    '<div class="book-title-en">' + safeString(book.title_en, 'Title') + '</div>' +
                    '<div class="book-title-ar">' + safeString(book.title_ar, 'العنوان') + '</div>' +
                    '<div class="book-meta">' +
                        '<span>📚 ' + toBanglaNumber(book.total_section || 0) + ' অধ্যায়</span>' +
                        '<span>📖 ' + toBanglaNumber(book.total_hadith || 0) + ' হাদিস</span>' +
                    '</div>';
                container.appendChild(box);
            });
        }
        
        async function loadSections(bookId, bookTitle) {
            currentState = { 
                page: 'sections', 
                bookId: bookId, 
                sectionId: null, 
                bookTitle: bookTitle, 
                sectionTitle: '' 
            };
            
            updateToolbar(bookTitle, true);
            showLoading();
            
            try {
                const cacheKey = 'sections_' + bookId;
                let data = cachedData.sections[bookId];
                
                if (!data) {
                    data = await fetchData(
                        'https://cdn.jsdelivr.net/gh/SunniPedia/sunnipedia@main/hadith-books/book/' + bookId + '/title.json',
                        cacheKey
                    );
                    cachedData.sections[bookId] = data;
                }
                
                currentDisplayData = data;
                renderSections(data);
                
            } catch (error) {
                showError('অধ্যায় লোড করতে সমস্যা হয়েছে', () => loadSections(bookId, bookTitle));
            }
        }
        
        function renderSections(sections) {
            const container = document.getElementById('contentContainer');
            
            if (!sections || sections.length === 0) {
                container.innerHTML = '<div class="error-state">কোন অধ্যায় পাওয়া যায়নি</div>';
                return;
            }
            
            container.innerHTML = '';
            
            sections.sort((a, b) => (a.sequence || 0) - (b.sequence || 0)).forEach(section => {
                const box = document.createElement('div');
                box.className = 'book-box';
                box.onclick = () => loadHadith(
                    currentState.bookId, 
                    section.id, 
                    safeString(section.title, 'অধ্যায়')
                );
                
                let rangeText = '';
                if (section.range_start && section.range_end) {
                    rangeText = '<span>🔢 ' + toBanglaNumber(section.range_start) + '-' + toBanglaNumber(section.range_end) + '</span>';
                }
                
                box.innerHTML = 
                    '<div class="book-id-badge">' + toBanglaNumber(section.sequence || 0) + '</div>' +
                    '<div class="book-title-en">' + safeString(section.title, 'অধ্যায়') + '</div>' +
                    '<div class="book-title-ar">' + safeString(section.title_ar, 'البাব') + '</div>' +
                    '<div class="book-meta">' +
                        '<span>📖 ' + toBanglaNumber(section.total_hadith || 0) + ' হাদিস</span>' +
                        rangeText +
                    '</div>';
                container.appendChild(box);
            });
        }
        
        async function loadHadith(bookId, sectionId, sectionTitle) {
            currentState = { 
                page: 'hadith', 
                bookId: bookId, 
                sectionId: sectionId, 
                bookTitle: currentState.bookTitle, 
                sectionTitle: sectionTitle 
            };
            
            updateToolbar(sectionTitle, true);
            showLoading();
            
            try {
                const cacheKey = 'hadith_' + bookId + '_' + sectionId;
                const dataKey = bookId + '_' + sectionId;
                let data = cachedData.hadith[dataKey];
                
                if (!data) {
                    data = await fetchData(
                        'https://cdn.jsdelivr.net/gh/SunniPedia/sunnipedia@main/hadith-books/book/' + bookId + '/hadith/' + sectionId + '.json',
                        cacheKey
                    );
                    cachedData.hadith[dataKey] = data;
                }
                
                currentDisplayData = data;
                renderHadith(data);
                
            } catch (error) {
                showError('হাদিস লোড করতে সমস্যা হয়েছে', () => loadHadith(bookId, sectionId, sectionTitle));
            }
        }
        
        function renderHadith(hadithList) {
            const container = document.getElementById('contentContainer');
            
            if (!hadithList || hadithList.length === 0) {
                container.innerHTML = '<div class="error-state">কোন হাদিস পাওয়া যায়নি</div>';
                return;
            }
            
            container.innerHTML = '';
            
            hadithList.sort((a, b) => (a.hadith_number || 0) - (b.hadith_number || 0)).forEach(hadith => {
                const box = document.createElement('div');
                box.className = 'hadith-box';
                box.innerHTML = 
                    '<div class="hadith-header">' +
                        '<span class="hadith-number">হাদিস নং: ' + toBanglaNumber(hadith.hadith_number) + '</span>' +
                        '<span class="action-buttons">' +
                            '<img src="file:///android_asset/images/copy.png" class="action-icon" onclick="event.stopPropagation(); copyHadith(' + hadith.hadith_number + ')" alt="Copy">' +
                            '<img src="file:///android_asset/images/share.png" class="action-icon" onclick="event.stopPropagation(); shareHadith(' + hadith.hadith_number + ')" alt="Share">' +
                        '</span>' +
                    '</div>' +
                    '<div class="hadith-title">' + safeString(hadith.title, '') + '</div>' +
                    '<div class="hadith-description-ar">' + safeString(hadith.description_ar, '') + '</div>' +
                    '<div class="hadith-description">' + safeString(hadith.description, '') + '</div>';
                container.appendChild(box);
            });
        }
        
        function findHadithByNumber(number) {
            return currentDisplayData.find(h => h.hadith_number == number);
        }
        
        function copyHadith(hadithNumber) {
            const hadith = findHadithByNumber(hadithNumber);
            if (!hadith) return;
            
            // শেয়ার টেক্সটে সরাসরি ব্যাকটিক ব্যবহার করে মাল্টিলাইন টেক্সট পাঠানো হচ্ছে যাতে \n না আসে
            const text = `${currentState.bookTitle}
${currentState.sectionTitle}
হাদিস নং: ${toBanglaNumber(hadith.hadith_number)}
${hadith.title || ''}
${hadith.description_ar || ''}
${hadith.description || ''}`;
            
            if (typeof AndroidApp !== 'undefined') {
                AndroidApp.copyToClipboard(text);
                AndroidApp.showToast('কপি করা হয়েছে!');
            }
        }
        
        function shareHadith(hadithNumber) {
            const hadith = findHadithByNumber(hadithNumber);
            if (!hadith) return;
            
            const text = `${currentState.bookTitle}

${currentState.sectionTitle}

হাদিস নং: ${toBanglaNumber(hadith.hadith_number)}
${hadith.title || ''}
${hadith.description_ar || ''}
${hadith.description || ''}

অ্যাপ: ইসলামী বিশ্বকোষ ও আল হাদিস`;
            
            if (typeof AndroidApp !== 'undefined') {
                AndroidApp.shareText(text);
            }
        }
        
        let searchTimeout;
        function handleSearch(query) {
            clearTimeout(searchTimeout);
            searchTimeout = setTimeout(() => {
                performSearch(query);
            }, 300);
        }
        
        function performSearch(query) {
            if (!query || query.trim() === '') {
                if (currentState.page === 'books') {
                    renderBooks(cachedData.books);
                } else if (currentState.page === 'sections') {
                    renderSections(cachedData.sections[currentState.bookId]);
                } else if (currentState.page === 'hadith') {
                    renderHadith(cachedData.hadith[currentState.bookId + '_' + currentState.sectionId]);
                }
                return;
            }
            
            const searchTerm = query.toLowerCase().trim();
            
            if (currentState.page === 'books' && cachedData.books) {
                const filtered = cachedData.books.filter(book => 
                    (book.title_en || '').toLowerCase().includes(searchTerm) ||
                    (book.title_ar || '').includes(searchTerm) ||
                    (book.title || '').toLowerCase().includes(searchTerm)
                );
                renderBooks(filtered);
                
            } else if (currentState.page === 'sections') {
                const sections = cachedData.sections[currentState.bookId];
                if (sections) {
                    const filtered = sections.filter(section => 
                        (section.title || '').toLowerCase().includes(searchTerm) ||
                        (section.title_ar || '').includes(searchTerm)
                    );
                    renderSections(filtered);
                }
                
            } else if (currentState.page === 'hadith') {
                const hadithList = cachedData.hadith[currentState.bookId + '_' + currentState.sectionId];
                if (hadithList) {
                    const filtered = hadithList.filter(hadith => 
                        hadith.hadith_number.toString().includes(searchTerm) ||
                        (hadith.title || '').toLowerCase().includes(searchTerm) ||
                        (hadith.description || '').toLowerCase().includes(searchTerm) ||
                        (hadith.description_ar || '').toLowerCase().includes(searchTerm)
                    );
                    renderHadith(filtered);
                }
            }
        }
        
        function toggleSearch() {
            const searchContainer = document.getElementById('searchContainer');
            const searchInput = document.getElementById('searchInput');
            
            if (searchContainer.classList.contains('active')) {
                searchContainer.classList.remove('active');
                searchInput.value = '';
                handleSearch('');
            } else {
                searchContainer.classList.add('active');
                searchInput.focus();
            }
        }
        
        function handleBack() {
            if (currentState.page === 'hadith') {
                loadSections(currentState.bookId, currentState.bookTitle);
            } else if (currentState.page === 'sections') {
                loadBooks();
            } else {
                if (typeof AndroidApp !== 'undefined') {
                    AndroidApp.finishActivity();
                }
            }
        }
        
        document.addEventListener('DOMContentLoaded', () => {
            loadBooks();
        });
    </script>
</body>
</html>
        """.trimIndent()
    }

    inner class AndroidJavaScriptInterface {
        
        @android.webkit.JavascriptInterface
        fun finishActivity() {
            runOnUiThread {
                finish()
            }
        }
        
        @android.webkit.JavascriptInterface
        fun getCachedData(key: String): String {
            return try {
                val cacheFile = File(filesDir, "$cacheDirName/${getCacheFileName(key)}")
                if (cacheFile.exists()) {
                    cacheFile.readText()
                } else {
                    ""
                }
            } catch (e: Exception) {
                e.printStackTrace()
                ""
            }
        }
        
        @android.webkit.JavascriptInterface
        fun cacheData(key: String, data: String) {
            try {
                val cacheDir = File(filesDir, cacheDirName)
                if (!cacheDir.exists()) {
                    cacheDir.mkdirs()
                }
                
                val cacheFile = File(cacheDir, getCacheFileName(key))
                cacheFile.writeText(data)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        @android.webkit.JavascriptInterface
        fun showToast(message: String) {
            runOnUiThread {
                Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
            }
        }
        
        @android.webkit.JavascriptInterface
        fun copyToClipboard(text: String) {
            runOnUiThread {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("হাদিস", text)
                clipboard.setPrimaryClip(clip)
            }
        }
        
        @android.webkit.JavascriptInterface
        fun shareText(text: String) {
            runOnUiThread {
                val sendIntent = android.content.Intent().apply {
                    action = android.content.Intent.ACTION_SEND
                    putExtra(android.content.Intent.EXTRA_TEXT, text)
                    type = "text/plain"
                }
                val shareIntent = android.content.Intent.createChooser(sendIntent, "শেয়ার করুন")
                startActivity(shareIntent)
            }
        }
        
        private fun getCacheFileName(key: String): String {
            val md = MessageDigest.getInstance("MD5")
            val digest = md.digest(key.toByteArray())
            return digest.joinToString("") { "%02x".format(it) } + ".json"
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
               capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun showOfflineMessage() {
        webView.evaluateJavascript("""
            document.getElementById('offlineIndicator').style.display = 'block';
        """.trimIndent(), null)
    }

    override fun onBackPressed() {
        webView.evaluateJavascript("""
            (function() {
                if (typeof currentState !== 'undefined') {
                    return JSON.stringify({
                        page: currentState.page,
                        canGoBack: currentState.page !== 'books'
                    });
                }
                return JSON.stringify({page: 'unknown', canGoBack: false});
            })();
        """.trimIndent()) { result ->
            
            try {
                val cleanResult = result.replace("\\\"", "\"").trim('"')
                
                if (cleanResult.contains("\"page\":\"books\"")) {
                    super.onBackPressed()
                } else if (cleanResult.contains("\"page\":\"sections\"") || cleanResult.contains("\"page\":\"hadith\"")) {
                    webView.evaluateJavascript("handleBack()", null)
                } else {
                    super.onBackPressed()
                }
            } catch (e: Exception) {
                super.onBackPressed()
            }
        }
    }
}
