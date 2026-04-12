package com.srizwan.islamipedia0

import android.content.Context
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
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

        WindowCompat.setDecorFitsSystemWindows(window, true)
        val insetsController = WindowInsetsControllerCompat(window, window.decorView)
        insetsController.show(WindowInsetsCompat.Type.systemBars())
        insetsController.isAppearanceLightStatusBars = false
        insetsController.isAppearanceLightNavigationBars = false

        window.statusBarColor = Color.parseColor("#01837A")
        window.navigationBarColor = Color.BLACK

        webView = findViewById(R.id.webView)
        setupWebView()

        val cacheDir = File(filesDir, cacheDirName)
        if (!cacheDir.exists()) cacheDir.mkdirs()

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
                view: WebView?, request: WebResourceRequest?, error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                if (!isNetworkAvailable()) showOfflineMessage()
            }
        }

        webView.webChromeClient = WebChromeClient()
        webView.addJavascriptInterface(AndroidJavaScriptInterface(), "AndroidApp")
    }

    private fun loadAppContent() {
        val htmlFile = File(filesDir, "index.html")
        htmlFile.writeText(generateMainHTML())
        webView.loadUrl("file://${htmlFile.absolutePath}")
    }

    private fun generateMainHTML(): String {
        return """
<!DOCTYPE html>
<html lang="bn">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no, shrink-to-fit=no">
    <meta name="description" content="ইসলামী বিশ্বকোষ ও আল হাদিস - আল কুরআন, হাদিস, ইসলামী বই ও ইসলামী তথ্য ভান্ডারের সমাহার">
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

        * { margin: 0; padding: 0; box-sizing: border-box; }

        body {
            font-family: 'SolaimanLipi', sans-serif;
            background: #f5f5f5;
            color: #333;
            line-height: 1.6;
            -webkit-tap-highlight-color: transparent;
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
        .toolbar-icon { width: 24px; height: 24px; cursor: pointer; }
        .toolbar-title {
            flex: 1;
            text-align: center;
            font-size: 20px;
            font-weight: bold;
            margin: 0 15px;
            overflow: hidden;
            white-space: nowrap;
        }
        
        /* Marquee Animation */
        .toolbar-title span {
            display: inline-block;
            padding-left: 100%;
            animation: marquee 12s linear infinite;
            white-space: nowrap;
        }
        
        @keyframes marquee {
            0% { transform: translateX(0); }
            100% { transform: translateX(-100%); }
        }
        
        /* Only apply marquee when needed */
        .toolbar-title.marquee-active span {
            animation: marquee 12s linear infinite;
        }
        
        .toolbar-title:not(.marquee-active) span {
            animation: none;
            padding-left: 0;
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
        .search-container.active { display: block; }
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
            top: -12px; left: -12px;
            background: #01837A;
            color: white;
            width: 35px; height: 35px;
            border-radius: 50%;
            display: flex; align-items: center; justify-content: center;
            font-weight: bold; font-size: 16px;
        }
        .book-title-en {
            font-size: 18px; font-weight: bold;
            color: #01837A; margin-bottom: 8px; padding-left: 20px;
        }
        .book-title-ar {
            font-family: 'Noorehuda', sans-serif;
            font-size: 18px; text-align: right;
            color: #333; margin: 15px 0; direction: rtl;
        }
        .book-meta {
            display: flex; justify-content: space-between;
            padding-top: 10px; border-top: 1px dashed #ddd;
            font-size: 14px; color: #666;
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
            display: flex; justify-content: space-between;
            align-items: center; margin-bottom: 15px;
        }
        .hadith-number {
            background: #01837A; color: white;
            padding: 5px 15px; border-radius: 20px;
            font-weight: bold; font-size: 14px;
        }
        .action-buttons { display: flex; gap: 10px; }
        .action-icon { width: 24px; height: 24px; cursor: pointer; }
        .hadith-title {
            font-size: 18px; font-weight: bold;
            color: #01837A; margin: 12px 0;
        }
        .hadith-description-ar {
            font-family: 'Noorehuda', sans-serif;
            font-size: 20px; color: #333;
            margin: 15px 0; text-align: right; direction: rtl;
        }
        .hadith-description {
            font-size: 16px; color: #444;
            margin: 15px 0; text-align: justify;
        }

        .loading-state {
            text-align: center; padding: 30px;
            color: #01837A; font-size: 18px;
        }
        .error-state {
            text-align: center; padding: 30px;
            color: #e74c3c; font-size: 16px;
        }
        .offline-indicator {
            background: #ff9800; color: white;
            text-align: center; padding: 5px;
            font-size: 12px; display: none;
        }
        .retry-button {
            background: #01837A; color: white;
            border: none; padding: 10px 20px;
            border-radius: 25px;
            font-family: 'SolaimanLipi', sans-serif;
            font-size: 14px; margin-top: 15px; cursor: pointer;
        }

        /* ========== GLOBAL SEARCH POPUP ========== */
        .global-search-overlay {
            display: none;
            position: fixed;
            top: 0; left: 0; right: 0; bottom: 0;
            background: rgba(0,0,0,0.5);
            z-index: 999;
        }
        .global-search-overlay.active { display: flex; flex-direction: column; }

        .global-search-popup {
            background: white;
            width: 100%;
            height: 100%;
            display: flex;
            flex-direction: column;
        }

        .global-search-header {
            background: #01837A;
            color: white;
            padding: 15px;
            display: flex;
            align-items: center;
            gap: 10px;
            flex-shrink: 0;
        }
        .global-search-header-title {
            font-size: 18px;
            font-weight: bold;
            flex: 1;
        }
        .global-search-close {
            width: 24px; height: 24px;
            cursor: pointer;
        }

        .global-search-input-wrap {
            padding: 12px 15px;
            background: #f0fffe;
            border-bottom: 1px solid #ddd;
            flex-shrink: 0;
        }
        .global-search-input {
            width: 100%;
            padding: 10px 15px;
            border: 2px solid #01837A;
            border-radius: 25px;
            font-family: 'SolaimanLipi', sans-serif;
            font-size: 16px;
            outline: none;
        }

        .global-search-status {
            padding: 8px 15px;
            font-size: 13px;
            color: #666;
            background: #f9f9f9;
            border-bottom: 1px solid #eee;
            flex-shrink: 0;
            min-height: 35px;
        }

        .global-search-results {
            flex: 1;
            overflow-y: auto;
            padding: 10px 15px;
        }

        .global-search-hint {
            text-align: center;
            padding: 40px 20px;
            color: #999;
            font-size: 16px;
        }

        .gs-hadith-box {
            background: white;
            border-radius: 10px;
            padding: 15px;
            margin-bottom: 12px;
            box-shadow: 0 2px 8px rgba(0,0,0,0.1);
            border: 2px solid #01837A;
        }
        .gs-hadith-header {
            display: flex;
            justify-content: space-between;
            align-items: center;
            margin-bottom: 10px;
            flex-wrap: wrap;
            gap: 6px;
        }
        .gs-hadith-number {
            background: #01837A; color: white;
            padding: 4px 12px; border-radius: 20px;
            font-weight: bold; font-size: 13px;
        }
        .gs-book-label {
            font-size: 12px;
            color: #01837A;
            background: #e8f8f7;
            padding: 3px 10px;
            border-radius: 12px;
            border: 1px solid #01837A;
        }
        .gs-action-buttons { display: flex; gap: 8px; }
        .gs-action-icon { width: 22px; height: 22px; cursor: pointer; }
        .gs-hadith-title {
            font-size: 16px; font-weight: bold;
            color: #01837A; margin: 8px 0;
        }
        .gs-hadith-description-ar {
            font-family: 'Noorehuda', sans-serif;
            font-size: 18px; color: #333;
            margin: 10px 0; text-align: right; direction: rtl;
        }
        .gs-hadith-description {
            font-size: 15px; color: #444;
            margin: 10px 0; text-align: justify;
        }
        .gs-loading {
            text-align: center; padding: 20px;
            color: #01837A; font-size: 16px;
        }
        .gs-no-result {
            text-align: center; padding: 30px;
            color: #999; font-size: 16px;
        }

        /* Floating global search button */
        .fab-global-search {
            position: fixed;
            bottom: 20px;
            right: 20px;
            width: 52px;
            height: 52px;
            background: #01837A;
            border-radius: 50%;
            display: flex;
            align-items: center;
            justify-content: center;
            box-shadow: 0 4px 12px rgba(0,0,0,0.3);
            cursor: pointer;
            z-index: 200;
        }
        .fab-global-search img { width: 26px; height: 26px; }
    </style>
</head>
<body>
    <div class="toolbar">
        <img src="file:///android_asset/images/back.png" class="toolbar-icon" id="backButton" onclick="handleBack()" alt="Back">
        <div class="toolbar-title" id="pageTitle"><span>হাদিস সমগ্র</span></div>
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

    <!-- Floating Global Search Button -->
    <div class="fab-global-search" onclick="openGlobalSearch()" title="সম্পূর্ণ হাদিস সার্চ">
        <img src="file:///android_asset/images/search.png" alt="Global Search">
    </div>

    <!-- Global Search Popup -->
    <div class="global-search-overlay" id="globalSearchOverlay">
        <div class="global-search-popup">
            <div class="global-search-header">
                <img src="file:///android_asset/images/back.png" class="global-search-close" onclick="closeGlobalSearch()" alt="Close">
                <div class="global-search-header-title">সম্পূর্ণ হাদিস সার্চ</div>
            </div>
            <div class="global-search-input-wrap">
                <input type="text" class="global-search-input" id="globalSearchInput"
                    placeholder="হাদিস নম্বর, শিরোনাম বা বাংলা/আরবি লিখুন..."
                    oninput="handleGlobalSearch(this.value)">
            </div>
            <div class="global-search-status" id="globalSearchStatus">
                সার্চ করতে টাইপ করুন...
            </div>
            <div class="global-search-results" id="globalSearchResults">
                <div class="global-search-hint">
                    🔍 সমস্ত হাদিস বই থেকে সার্চ করুন<br><br>
                    <small>হাদিস নম্বর, বাংলা অনুবাদ বা আরবি টেক্সট দিয়ে সার্চ করা যাবে</small>
                </div>
            </div>
        </div>
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

        // ====== Global Search State ======
        let globalSearchTimeout = null;
        let allBooksData = null;
        let globalSearchActive = false;
        let globalSearchAbortFlag = false;

        const toBanglaNumber = (num) => {
            if (num === null || num === undefined || isNaN(num)) return '০';
            const banglaDigits = ['০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯'];
            return num.toString().replace(/\d/g, digit => banglaDigits[digit]);
        };

        const safeString = (str, fallback) => str || fallback || 'তথ্য নেই';

        // Marquee function for toolbar title
        function updateToolbar(title, showBack) {
            const titleElement = document.getElementById('pageTitle');
            const spanElement = titleElement.querySelector('span');
            spanElement.textContent = title;
            
            // Check if text needs marquee (longer than container)
            setTimeout(() => {
                const containerWidth = titleElement.clientWidth;
                const textWidth = spanElement.scrollWidth;
                if (textWidth > containerWidth) {
                    titleElement.classList.add('marquee-active');
                } else {
                    titleElement.classList.remove('marquee-active');
                }
            }, 10);
            
            document.getElementById('backButton').style.display = 'block';
        }

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
                allBooksData = data;
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
                        '<span>📚 ' + toBanglaNumber(book.total_section || 0) + ' টি অধ্যায়</span>' +
                        '<span>📖 ' + toBanglaNumber(book.total_hadith || 0) + ' টি হাদিস</span>' +
                    '</div>';
                container.appendChild(box);
            });
        }

        async function loadSections(bookId, bookTitle) {
            currentState = { page: 'sections', bookId: bookId, sectionId: null, bookTitle: bookTitle, sectionTitle: '' };
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
                box.onclick = () => loadHadith(currentState.bookId, section.id, safeString(section.title, 'অধ্যায়'));
                let rangeText = '';
                if (section.range_start && section.range_end) {
                    rangeText = '<span>🔢 ব্যাপ্তি: ' + toBanglaNumber(section.range_start) + '-' + toBanglaNumber(section.range_end) + '</span>';
                }
                box.innerHTML =
                    '<div class="book-id-badge">' + toBanglaNumber(section.sequence || 0) + '</div>' +
                    '<div class="book-title-en">' + safeString(section.title, 'অধ্যায়') + '</div>' +
                    '<div class="book-title-ar">' + safeString(section.title_ar, 'الباب') + '</div>' +
                    '<div class="book-meta">' +
                        '<span>📖 মোট ' + toBanglaNumber(section.total_hadith || 0) + ' টি হাদিস</span>' +
                        rangeText +
                    '</div>';
                container.appendChild(box);
            });
        }

        async function loadHadith(bookId, sectionId, sectionTitle) {
            currentState = {
                page: 'hadith', bookId: bookId, sectionId: sectionId,
                bookTitle: currentState.bookTitle, sectionTitle: sectionTitle
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

        function buildText(parts) {
            return parts.filter(p => p && p.trim() !== '').join('\n');
        }

        function copyHadith(hadithNumber) {
            const hadith = findHadithByNumber(hadithNumber);
            if (!hadith) return;
            const text = buildText([
                currentState.bookTitle, currentState.sectionTitle,
                'হাদিস নং: ' + toBanglaNumber(hadith.hadith_number),
                hadith.title || '', hadith.description_ar || '', hadith.description || ''
            ]);
            if (typeof AndroidApp !== 'undefined') {
                AndroidApp.copyToClipboard(text);
                AndroidApp.showToast('কপি করা হয়েছে!');
            }
        }

        function shareHadith(hadithNumber) {
            const hadith = findHadithByNumber(hadithNumber);
            if (!hadith) return;
            const text = buildText([
                currentState.bookTitle, currentState.sectionTitle,
                'হাদিস নং: ' + toBanglaNumber(hadith.hadith_number),
                hadith.title || '', hadith.description_ar || '', hadith.description || '',
                'অ্যাপ: ইসলামী বিশ্বকোষ ও আল হাদিস\nhttps://play.google.com/store/apps/details?id=com.srizwan.islamipedia'
            ]);
            if (typeof AndroidApp !== 'undefined') AndroidApp.shareText(text);
        }

        // ====== Page-level search ======
        let searchTimeout;
        function handleSearch(query) {
            clearTimeout(searchTimeout);
            searchTimeout = setTimeout(() => performSearch(query), 300);
        }

        function performSearch(query) {
            if (!query || query.trim() === '') {
                if (currentState.page === 'books') renderBooks(cachedData.books);
                else if (currentState.page === 'sections') renderSections(cachedData.sections[currentState.bookId]);
                else if (currentState.page === 'hadith') renderHadith(cachedData.hadith[currentState.bookId + '_' + currentState.sectionId]);
                return;
            }
            const searchTerm = query.toLowerCase().trim();
            if (currentState.page === 'books' && cachedData.books) {
                renderBooks(cachedData.books.filter(book =>
                    (book.title_en || '').toLowerCase().includes(searchTerm) ||
                    (book.title_ar || '').includes(searchTerm) ||
                    (book.title || '').toLowerCase().includes(searchTerm)
                ));
            } else if (currentState.page === 'sections') {
                const sections = cachedData.sections[currentState.bookId];
                if (sections) renderSections(sections.filter(section =>
                    (section.title || '').toLowerCase().includes(searchTerm) ||
                    (section.title_ar || '').includes(searchTerm)
                ));
            } else if (currentState.page === 'hadith') {
                const hadithList = cachedData.hadith[currentState.bookId + '_' + currentState.sectionId];
                if (hadithList) renderHadith(hadithList.filter(hadith =>
                    hadith.hadith_number.toString().includes(searchTerm) ||
                    (hadith.title || '').toLowerCase().includes(searchTerm) ||
                    (hadith.description || '').toLowerCase().includes(searchTerm) ||
                    (hadith.description_ar || '').toLowerCase().includes(searchTerm)
                ));
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

        function isSearchOpen() {
            return document.getElementById('searchContainer').classList.contains('active');
        }

        function closeSearch() {
            const searchContainer = document.getElementById('searchContainer');
            const searchInput = document.getElementById('searchInput');
            searchContainer.classList.remove('active');
            searchInput.value = '';
            handleSearch('');
        }

        function handleBack() {
            if (isSearchOpen()) {
                closeSearch();
                return;
            }
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

        // ====== GLOBAL SEARCH POPUP (CACHE ONLY) ======
        function openGlobalSearch() {
            globalSearchActive = true;
            document.getElementById('globalSearchOverlay').classList.add('active');
            setTimeout(() => document.getElementById('globalSearchInput').focus(), 300);
        }

        function closeGlobalSearch() {
            globalSearchActive = false;
            globalSearchAbortFlag = true;
            document.getElementById('globalSearchOverlay').classList.remove('active');
            document.getElementById('globalSearchInput').value = '';
            document.getElementById('globalSearchStatus').textContent = 'সার্চ করতে টাইপ করুন...';
            document.getElementById('globalSearchResults').innerHTML =
                '<div class="global-search-hint">🔍 সমস্ত হাদিস বই থেকে সার্চ করুন<br><br><small>হাদিস নম্বর, বাংলা অনুবাদ বা আরবি টেক্সট দিয়ে সার্চ করা যাবে</small></div>';
        }

        function handleGlobalSearch(query) {
            clearTimeout(globalSearchTimeout);
            if (!query || query.trim().length < 2) {
                document.getElementById('globalSearchStatus').textContent = 'কমপক্ষে ২টি অক্ষর লিখুন...';
                document.getElementById('globalSearchResults').innerHTML =
                    '<div class="global-search-hint">🔍 সমস্ত হাদিস বই থেকে সার্চ করুন<br><br><small>হাদিস নম্বর, বাংলা অনুবাদ বা আরবি টেক্সট দিয়ে সার্চ করা যাবে</small></div>';
                return;
            }
            document.getElementById('globalSearchStatus').textContent = '⏳ ক্যাশে অনুসন্ধান করা হচ্ছে...';
            globalSearchTimeout = setTimeout(() => performGlobalSearchFromCache(query.trim()), 400);
        }

        // Search ONLY from cache - No network requests
        function performGlobalSearchFromCache(query) {
            globalSearchAbortFlag = false;
            const resultsDiv = document.getElementById('globalSearchResults');
            const statusDiv = document.getElementById('globalSearchStatus');

            if (!cachedData.books) {
                statusDiv.textContent = '⚠️ কোনো ক্যাশে ডাটা নেই। প্রথমে অনলাইনে ডাটা লোড করুন।';
                resultsDiv.innerHTML = '<div class="gs-no-result">ক্যাশে কোনো ডাটা পাওয়া যায়নি। ইন্টারনেট সংযোগ দিয়ে অ্যাপটি প্রথমবার চালু করুন।</div>';
                return;
            }

            resultsDiv.innerHTML = '<div class="gs-loading">🔍 ক্যাশে অনুসন্ধান চলছে...</div>';
            const searchTerm = query.toLowerCase();
            const allResults = [];
            let totalHadithCount = 0;
            let booksSearched = 0;

            // Search through cached data only
            for (const book of cachedData.books) {
                if (globalSearchAbortFlag) return;
                
                const sections = cachedData.sections[book.id];
                if (!sections) continue;
                
                let bookHasData = false;
                for (const section of sections) {
                    if (globalSearchAbortFlag) return;
                    
                    const dataKey = book.id + '_' + section.id;
                    const hadithList = cachedData.hadith[dataKey];
                    
                    if (hadithList) {
                        bookHasData = true;
                        totalHadithCount += hadithList.length;
                        
                        const matched = hadithList.filter(hadith =>
                            (hadith.hadith_number || '').toString().includes(searchTerm) ||
                            (hadith.title || '').toLowerCase().includes(searchTerm) ||
                            (hadith.description || '').toLowerCase().includes(searchTerm) ||
                            (hadith.description_ar || '').includes(searchTerm)
                        );

                        matched.forEach(h => {
                            allResults.push({
                                hadith: h,
                                bookTitle: safeString(book.title, book.title_en || 'বই'),
                                bookId: book.id,
                                sectionTitle: safeString(section.title, 'অধ্যায়'),
                                sectionId: section.id
                            });
                        });
                    }
                }
                
                if (bookHasData) {
                    booksSearched++;
                    statusDiv.textContent = '🔍 ' + toBanglaNumber(booksSearched) + ' টি বই ক্যাশে অনুসন্ধান হয়েছে — ' + toBanglaNumber(allResults.length) + ' টি ফলাফল';
                }
            }

            if (globalSearchAbortFlag) return;

            if (allResults.length === 0) {
                if (totalHadithCount === 0) {
                    resultsDiv.innerHTML = '<div class="gs-no-result">😔 ক্যাশে কোনো হাদিস ডাটা নেই। ইন্টারনেট সংযোগ দিয়ে প্রথমে ডাটা ডাউনলোড করুন।</div>';
                    statusDiv.textContent = 'ক্যাশে ডাটা পাওয়া যায়নি';
                } else {
                    resultsDiv.innerHTML = '<div class="gs-no-result">😔 ক্যাশে কোনো হাদিস পাওয়া যায়নি</div>';
                    statusDiv.textContent = 'মোট ' + toBanglaNumber(totalHadithCount) + ' টি হাদিস ক্যাশে আছে — কোনো ফলাফল নেই';
                }
            } else {
                statusDiv.textContent = '✅ ক্যাশে থেকে ' + toBanglaNumber(allResults.length) + ' টি হাদিস পাওয়া গেছে';
                renderGlobalResults(allResults);
            }
        }

        function renderGlobalResults(results) {
            const resultsDiv = document.getElementById('globalSearchResults');
            resultsDiv.innerHTML = '';
            results.forEach(item => {
                const { hadith, bookTitle, bookId, sectionTitle, sectionId } = item;
                const box = document.createElement('div');
                box.className = 'gs-hadith-box';
                box.innerHTML =
                    '<div class="gs-hadith-header">' +
                        '<span class="gs-hadith-number">হাদিস নং: ' + toBanglaNumber(hadith.hadith_number) + '</span>' +
                        '<span class="gs-book-label">' + bookTitle + '</span>' +
                        '<span class="gs-action-buttons">' +
                            '<img src="file:///android_asset/images/copy.png" class="gs-action-icon" onclick="gsCopy(' + hadith.hadith_number + ',\'' + escapeJs(bookTitle) + '\',\'' + escapeJs(sectionTitle) + '\')" alt="Copy">' +
                            '<img src="file:///android_asset/images/share.png" class="gs-action-icon" onclick="gsShare(' + hadith.hadith_number + ',\'' + escapeJs(bookTitle) + '\',\'' + escapeJs(sectionTitle) + '\')" alt="Share">' +
                        '</span>' +
                    '</div>' +
                    (hadith.title ? '<div class="gs-hadith-title">' + hadith.title + '</div>' : '') +
                    (hadith.description_ar ? '<div class="gs-hadith-description-ar">' + hadith.description_ar + '</div>' : '') +
                    (hadith.description ? '<div class="gs-hadith-description">' + hadith.description + '</div>' : '');
                resultsDiv.appendChild(box);
            });
        }

        function escapeJs(str) {
            return (str || '').replace(/\\/g, '\\\\').replace(/'/g, "\\'").replace(/"/g, '\\"');
        }

        function gsFindInAllCache(hadithNumber) {
            for (const key in cachedData.hadith) {
                const found = cachedData.hadith[key].find(h => h.hadith_number == hadithNumber);
                if (found) return found;
            }
            return null;
        }

        function gsGetTextFromResult(hadithNumber, bookTitle, sectionTitle) {
            const hadith = gsFindInAllCache(hadithNumber);
            if (!hadith) return '';
            return buildText([
                bookTitle, sectionTitle,
                'হাদিস নং: ' + toBanglaNumber(hadith.hadith_number),
                hadith.title || '', hadith.description_ar || '', hadith.description || ''
            ]);
        }

        function gsShare(hadithNumber, bookTitle, sectionTitle) {
            const hadith = gsFindInAllCache(hadithNumber);
            if (!hadith) return;
            const text = buildText([
                bookTitle, sectionTitle,
                'হাদিস নং: ' + toBanglaNumber(hadith.hadith_number),
                hadith.title || '', hadith.description_ar || '', hadith.description || '',
                'অ্যাপ: ইসলামী বিশ্বকোষ ও আল হাদিস\nhttps://play.google.com/store/apps/details?id=com.srizwan.islamipedia'
            ]);
            if (typeof AndroidApp !== 'undefined') AndroidApp.shareText(text);
        }

        function gsCopy(hadithNumber, bookTitle, sectionTitle) {
            const text = gsGetTextFromResult(hadithNumber, bookTitle, sectionTitle);
            if (!text) return;
            if (typeof AndroidApp !== 'undefined') {
                AndroidApp.copyToClipboard(text);
                AndroidApp.showToast('কপি করা হয়েছে!');
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
            runOnUiThread { finish() }
        }

        @android.webkit.JavascriptInterface
        fun getCachedData(key: String): String {
            return try {
                val cacheFile = File(filesDir, "$cacheDirName/${getCacheFileName(key)}")
                if (cacheFile.exists()) cacheFile.readText() else ""
            } catch (e: Exception) {
                e.printStackTrace(); ""
            }
        }

        @android.webkit.JavascriptInterface
        fun cacheData(key: String, data: String) {
            try {
                val cacheDir = File(filesDir, cacheDirName)
                if (!cacheDir.exists()) cacheDir.mkdirs()
                File(cacheDir, getCacheFileName(key)).writeText(data)
            } catch (e: Exception) { e.printStackTrace() }
        }

        @android.webkit.JavascriptInterface
        fun showToast(message: String) {
            runOnUiThread { Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show() }
        }

        @android.webkit.JavascriptInterface
        fun copyToClipboard(text: String) {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("হাদিস", text))
        }

        @android.webkit.JavascriptInterface
        fun shareText(text: String) {
            runOnUiThread {
                val sendIntent = android.content.Intent().apply {
                    action = android.content.Intent.ACTION_SEND
                    putExtra(android.content.Intent.EXTRA_TEXT, text)
                    type = "text/plain"
                }
                startActivity(android.content.Intent.createChooser(sendIntent, "শেয়ার করুন"))
            }
        }

        private fun getCacheFileName(key: String): String {
            val md = MessageDigest.getInstance("MD5")
            return md.digest(key.toByteArray()).joinToString("") { "%02x".format(it) } + ".json"
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
               capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun showOfflineMessage() {
        webView.evaluateJavascript(
            "document.getElementById('offlineIndicator').style.display = 'block';", null
        )
    }

    override fun onBackPressed() {
        webView.evaluateJavascript("""
            (function() {
                if (typeof globalSearchActive !== 'undefined' && globalSearchActive) {
                    return JSON.stringify({action: 'closeGlobalSearch'});
                }
                if (typeof isSearchOpen === 'function' && isSearchOpen()) {
                    return JSON.stringify({action: 'closeSearch'});
                }
                if (typeof currentState !== 'undefined') {
                    return JSON.stringify({action: 'navigate', page: currentState.page});
                }
                return JSON.stringify({action: 'finish'});
            })();
        """.trimIndent()) { result ->
            try {
                val clean = result.replace("\\\"", "\"").trim('"')
                when {
                    clean.contains("\"action\":\"closeGlobalSearch\"") ->
                        webView.evaluateJavascript("closeGlobalSearch()", null)
                    clean.contains("\"action\":\"closeSearch\"") ->
                        webView.evaluateJavascript("closeSearch()", null)
                    clean.contains("\"page\":\"books\"") ->
                        super.onBackPressed()
                    clean.contains("\"page\":\"sections\"") || clean.contains("\"page\":\"hadith\"") ->
                        webView.evaluateJavascript("handleBack()", null)
                    else ->
                        super.onBackPressed()
                }
            } catch (e: Exception) {
                super.onBackPressed()
            }
        }
    }
}
