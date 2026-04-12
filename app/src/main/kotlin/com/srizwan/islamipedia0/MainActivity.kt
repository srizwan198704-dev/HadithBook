package com.srizwan.islamipedia0

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.webkit.*
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.io.*
import java.security.MessageDigest

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private val cacheDirName = "hadith_data"
    
    // অ্যাপের বর্তমান অবস্থা ট্র্যাক করার জন্য
    private var currentPage = "books"
    private var currentBookId: String? = null
    private var currentSectionId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // ফুলস্ক্রিন মোড সেটআপ
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

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
            
            // পারফরম্যান্স অপ্টিমাইজেশন
            setRenderPriority(WebSettings.RenderPriority.HIGH)
        }

        // WebView ক্লায়েন্ট সেটআপ
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // পেজ লোড শেষে জাভাস্ক্রিপ্ট ইনজেক্ট করুন
                injectJavaScriptInterface()
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                // ইন্টারনেট না থাকলে ক্যাশে থেকে লোড
                if (!isNetworkAvailable()) {
                    showOfflineMessage()
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                // প্রোগ্রেস বার দেখানোর জন্য (ঐচ্ছিক)
            }
        }

        // জাভাস্ক্রিপ্ট ইন্টারফেস যোগ করুন
        webView.addJavascriptInterface(AndroidJavaScriptInterface(), "AndroidApp")
    }

    private fun injectJavaScriptInterface() {
        val jsCode = """
            // অ্যান্ড্রয়েড ইন্টারফেস চেক করুন
            if (typeof AndroidApp !== 'undefined') {
                console.log('Android interface connected');
                
                // অ্যান্ড্রয়েড থেকে ডেটা লোড করার ফাংশন
                window.loadFromAndroidCache = function(key) {
                    return AndroidApp.getCachedData(key);
                };
                
                // অ্যান্ড্রয়েডে ডেটা সেভ করার ফাংশন
                window.saveToAndroidCache = function(key, data) {
                    AndroidApp.cacheData(key, data);
                };
            }
        """.trimIndent()
        
        webView.evaluateJavascript(jsCode, null)
    }

    private fun loadAppContent() {
        val htmlContent = generateMainHTML()
        
        // HTML ফাইল ক্যাশে সেভ করুন
        val htmlFile = File(filesDir, "index.html")
        htmlFile.writeText(htmlContent)
        
        // WebView এ লোড করুন
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
        /* বাংলা ফন্ট ইম্পোর্ট */
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
        
        /* রিসেট স্টাইল */
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
        }
        
        /* টুলবার স্টাইল */
        .toolbar {
            background: linear-gradient(135deg, #01837A 0%, #01665E 100%);
            color: white;
            padding: 12px 16px;
            display: flex;
            align-items: center;
            box-shadow: 0 2px 8px rgba(0,0,0,0.15);
            position: sticky;
            top: 0;
            z-index: 1000;
        }
        
        .toolbar-icon {
            width: 24px;
            height: 24px;
            cursor: pointer;
            filter: brightness(0) invert(1);
            transition: transform 0.2s;
        }
        
        .toolbar-icon:active {
            transform: scale(0.9);
        }
        
        .toolbar-title {
            flex: 1;
            text-align: center;
            font-size: 20px;
            font-weight: bold;
            margin: 0 12px;
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
        }
        
        /* সার্চ কন্টেইনার */
        .search-container {
            background: white;
            padding: 12px 16px;
            box-shadow: 0 2px 4px rgba(0,0,0,0.1);
            display: none;
            position: sticky;
            top: 56px;
            z-index: 999;
        }
        
        .search-container.active {
            display: block;
        }
        
        .search-input {
            width: 100%;
            padding: 12px 20px;
            border: 2px solid #01837A;
            border-radius: 30px;
            font-family: 'SolaimanLipi', sans-serif;
            font-size: 16px;
            outline: none;
            background: #f8f8f8;
            transition: all 0.3s;
        }
        
        .search-input:focus {
            border-color: #01665E;
            background: white;
            box-shadow: 0 0 0 3px rgba(1,131,122,0.1);
        }
        
        /* কন্টেন্ট কন্টেইনার */
        .content-container {
            padding: 16px;
            max-width: 800px;
            margin: 0 auto;
        }
        
        /* বুক বক্স */
        .book-box {
            background: white;
            border-radius: 16px;
            padding: 20px;
            margin-bottom: 16px;
            box-shadow: 0 2px 8px rgba(0,0,0,0.08);
            border: 1px solid rgba(1,131,122,0.2);
            position: relative;
            transition: all 0.3s ease;
            cursor: pointer;
        }
        
        .book-box:active {
            transform: translateY(-2px);
            box-shadow: 0 4px 12px rgba(1,131,122,0.2);
        }
        
        .book-id-badge {
            position: absolute;
            top: -12px;
            left: -8px;
            background: linear-gradient(135deg, #01837A, #01665E);
            color: white;
            width: 36px;
            height: 36px;
            border-radius: 50%;
            display: flex;
            align-items: center;
            justify-content: center;
            font-weight: bold;
            font-size: 16px;
            box-shadow: 0 2px 8px rgba(1,131,122,0.3);
        }
        
        .book-title-en {
            font-size: 20px;
            font-weight: bold;
            color: #01837A;
            margin-bottom: 8px;
            padding-left: 24px;
        }
        
        .book-title-ar {
            font-family: 'Noorehuda', sans-serif;
            font-size: 20px;
            text-align: right;
            color: #2c3e50;
            margin: 12px 0;
            line-height: 1.8;
            direction: rtl;
        }
        
        .book-meta {
            display: flex;
            justify-content: space-between;
            align-items: center;
            padding-top: 12px;
            margin-top: 8px;
            border-top: 1px dashed #ddd;
            font-size: 14px;
            color: #666;
        }
        
        /* হাদিস বক্স */
        .hadith-box {
            background: white;
            border-radius: 16px;
            padding: 20px;
            margin-bottom: 20px;
            box-shadow: 0 2px 8px rgba(0,0,0,0.08);
            border-left: 4px solid #01837A;
        }
        
        .hadith-header {
            display: flex;
            justify-content: space-between;
            align-items: center;
            margin-bottom: 16px;
        }
        
        .hadith-number {
            background: linear-gradient(135deg, #01837A, #01665E);
            color: white;
            padding: 6px 16px;
            border-radius: 20px;
            font-weight: bold;
            font-size: 14px;
        }
        
        .action-buttons {
            display: flex;
            gap: 12px;
        }
        
        .action-icon {
            width: 22px;
            height: 22px;
            cursor: pointer;
            opacity: 0.7;
            transition: opacity 0.2s;
        }
        
        .action-icon:active {
            opacity: 1;
        }
        
        .hadith-title {
            font-size: 18px;
            font-weight: bold;
            color: #01837A;
            margin: 12px 0;
            line-height: 1.5;
        }
        
        .hadith-description-ar {
            font-family: 'Noorehuda', sans-serif;
            font-size: 22px;
            color: #2c3e50;
            margin: 16px 0;
            line-height: 2;
            text-align: right;
            direction: rtl;
        }
        
        .hadith-description {
            font-size: 16px;
            color: #444;
            margin: 16px 0;
            line-height: 1.8;
            text-align: justify;
        }
        
        /* লোডিং ও এরর */
        .loading-state {
            text-align: center;
            padding: 48px 20px;
            color: #01837A;
            font-size: 18px;
        }
        
        .loading-spinner {
            width: 40px;
            height: 40px;
            border: 3px solid #f3f3f3;
            border-top: 3px solid #01837A;
            border-radius: 50%;
            animation: spin 1s linear infinite;
            margin: 0 auto 16px;
        }
        
        @keyframes spin {
            0% { transform: rotate(0deg); }
            100% { transform: rotate(360deg); }
        }
        
        .error-state {
            text-align: center;
            padding: 48px 20px;
            color: #e74c3c;
            font-size: 16px;
        }
        
        .retry-button {
            background: #01837A;
            color: white;
            border: none;
            padding: 12px 24px;
            border-radius: 30px;
            font-family: 'SolaimanLipi', sans-serif;
            font-size: 16px;
            margin-top: 16px;
            cursor: pointer;
        }
        
        /* অফলাইন ইনডিকেটর */
        .offline-indicator {
            background: #ff9800;
            color: white;
            text-align: center;
            padding: 4px;
            font-size: 12px;
            position: sticky;
            top: 56px;
            z-index: 998;
        }
        
        /* রেস্পন্সিভ */
        @media (min-width: 768px) {
            .content-container {
                padding: 24px;
            }
            
            .book-box {
                padding: 24px;
            }
        }
    </style>
</head>
<body>
    <!-- টুলবার -->
    <div class="toolbar">
        <img src="file:///android_asset/images/back.png" class="toolbar-icon" id="backButton" onclick="handleBack()" alt="Back">
        <div class="toolbar-title" id="pageTitle">হাদিস সমগ্র</div>
        <img src="file:///android_asset/images/search.png" class="toolbar-icon" id="searchToggle" onclick="toggleSearch()" alt="Search">
    </div>
    
    <!-- সার্চ বার -->
    <div class="search-container" id="searchContainer">
        <input type="text" class="search-input" id="searchInput" placeholder="খুঁজুন..." oninput="handleSearch(this.value)">
    </div>
    
    <!-- অফলাইন ইনডিকেটর (শুরুতে হিডেন) -->
    <div class="offline-indicator" id="offlineIndicator" style="display: none;">
        ⚠️ অফলাইন মোড - ক্যাশে করা ডেটা দেখানো হচ্ছে
    </div>
    
    <!-- মেইন কন্টেন্ট -->
    <div class="content-container" id="contentContainer">
        <div class="loading-state">
            <div class="loading-spinner"></div>
            <div>লোড হচ্ছে...</div>
        </div>
    </div>

    <script>
        // ==================== গ্লোবাল ভ্যারিয়েবল ====================
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
        let isOnline = true;
        
        // ==================== ইউটিলিটি ফাংশন ====================
        const toBanglaNumber = (num) => {
            if (num === null || num === undefined || isNaN(num)) return '০';
            const banglaDigits = ['০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯'];
            return num.toString().replace(/\d/g, digit => banglaDigits[digit]);
        };
        
        const safeString = (str, fallback = 'তথ্য নেই') => str || fallback;
        
        const updateToolbar = (title, showBack = true) => {
            document.getElementById('pageTitle').textContent = title;
            document.getElementById('backButton').style.display = showBack ? 'block' : 'none';
        };
        
        const showLoading = () => {
            document.getElementById('contentContainer').innerHTML = `
                <div class="loading-state">
                    <div class="loading-spinner"></div>
                    <div>লোড হচ্ছে...</div>
                </div>
            `;
        };
        
        const showError = (message, retryCallback = null) => {
            const container = document.getElementById('contentContainer');
            container.innerHTML = `
                <div class="error-state">
                    <div>❌ ত্রুটি</div>
                    <div style="margin-top: 12px;">${message}</div>
                    ${retryCallback ? '<button class="retry-button" onclick="(' + retryCallback.toString() + ')()">আবার চেষ্টা করুন</button>' : ''}
                </div>
            `;
        };
        
        // ==================== ডেটা ফেচিং ====================
        async function fetchData(url, cacheKey) {
            try {
                // প্রথমে অ্যান্ড্রয়েড ক্যাশে চেক করুন
                if (typeof AndroidApp !== 'undefined') {
                    const cached = AndroidApp.getCachedData(cacheKey);
                    if (cached && cached.length > 0) {
                        console.log('Data loaded from cache:', cacheKey);
                        isOnline = false;
                        document.getElementById('offlineIndicator').style.display = 'block';
                        return JSON.parse(cached);
                    }
                }
                
                // অনলাইন থেকে ফেচ করুন
                console.log('Fetching from network:', url);
                const response = await fetch(url);
                
                if (!response.ok) {
                    throw new Error('Network response was not ok');
                }
                
                const data = await response.json();
                
                // অ্যান্ড্রয়েড ক্যাশে সেভ করুন
                if (typeof AndroidApp !== 'undefined') {
                    AndroidApp.cacheData(cacheKey, JSON.stringify(data));
                }
                
                isOnline = true;
                document.getElementById('offlineIndicator').style.display = 'none';
                return data;
                
            } catch (error) {
                console.error('Fetch error:', error);
                
                // ক্যাশে চেক করুন
                if (typeof AndroidApp !== 'undefined') {
                    const cached = AndroidApp.getCachedData(cacheKey);
                    if (cached && cached.length > 0) {
                        console.log('Fallback to cache:', cacheKey);
                        isOnline = false;
                        document.getElementById('offlineIndicator').style.display = 'block';
                        return JSON.parse(cached);
                    }
                }
                
                throw error;
            }
        }
        
        // ==================== বুকস লোড ====================
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
                box.innerHTML = `
                    <div class="book-id-badge">${toBanglaNumber(book.sequence || 0)}</div>
                    <div class="book-title-en">${safeString(book.title_en, 'Title')}</div>
                    <div class="book-title-ar">${safeString(book.title_ar, 'العنوان')}</div>
                    <div class="book-meta">
                        <span>📚 ${toBanglaNumber(book.total_section || 0)} অধ্যায়</span>
                        <span>📖 ${toBanglaNumber(book.total_hadith || 0)} হাদিস</span>
                    </div>
                `;
                container.appendChild(box);
            });
        }
        
        // ==================== সেকশনস লোড ====================
        async function loadSections(bookId, bookTitle) {
            currentState = { 
                page: 'sections', 
                bookId: bookId, 
                sectionId: null, 
                bookTitle: bookTitle, 
                sectionTitle: '' 
            };
            
            updateToolbar(bookTitle);
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
                    rangeText = toBanglaNumber(section.range_start) + '-' + toBanglaNumber(section.range_end);
                }
                
                box.innerHTML = `
                    <div class="book-id-badge">${toBanglaNumber(section.sequence || 0)}</div>
                    <div class="book-title-en">${safeString(section.title, 'অধ্যায়')}</div>
                    <div class="book-title-ar">${safeString(section.title_ar, 'الباب')}</div>
                    <div class="book-meta">
                        <span>📖 ${toBanglaNumber(section.total_hadith || 0)} হাদিস</span>
                        ${rangeText ? '<span>🔢 ' + rangeText + '</span>' : ''}
                    </div>
                `;
                container.appendChild(box);
            });
        }
        
        // ==================== হাদিস লোড ====================
        async function loadHadith(bookId, sectionId, sectionTitle) {
            currentState = { 
                page: 'hadith', 
                bookId: bookId, 
                sectionId: sectionId, 
                bookTitle: currentState.bookTitle, 
                sectionTitle: sectionTitle 
            };
            
            updateToolbar(sectionTitle);
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
                box.innerHTML = `
                    <div class="hadith-header">
                        <span class="hadith-number">হাদিস নং: ${toBanglaNumber(hadith.hadith_number)}</span>
                        <span class="action-buttons">
                            <img src="file:///android_asset/images/copy.png" class="action-icon" onclick="event.stopPropagation(); copyHadith(${hadith.hadith_number})" alt="Copy">
                            <img src="file:///android_asset/images/share.png" class="action-icon" onclick="event.stopPropagation(); shareHadith(${hadith.hadith_number})" alt="Share">
                        </span>
                    </div>
                    <div class="hadith-title">${safeString(hadith.title, '')}</div>
                    <div class="hadith-description-ar">${safeString(hadith.description_ar, '')}</div>
                    <div class="hadith-description">${safeString(hadith.description, '')}</div>
                `;
                container.appendChild(box);
            });
        }
        
        // ==================== কপি ও শেয়ার ====================
        function findHadithByNumber(number) {
            return currentDisplayData.find(h => h.hadith_number == number);
        }
        
        function copyHadith(hadithNumber) {
            const hadith = findHadithByNumber(hadithNumber);
            if (!hadith) return;
            
            const text = currentState.bookTitle + '\\n' +
                        'হাদিস নং: ' + toBanglaNumber(hadith.hadith_number) + '\\n' +
                        (hadith.title || '') + '\\n' +
                        (hadith.description_ar || '') + '\\n' +
                        (hadith.description || '');
            
            if (typeof AndroidApp !== 'undefined') {
                AndroidApp.copyToClipboard(text);
                AndroidApp.showToast('কপি করা হয়েছে!');
            } else {
                navigator.clipboard?.writeText(text).then(() => {
                    alert('কপি করা হয়েছে!');
                });
            }
        }
        
        function shareHadith(hadithNumber) {
            const hadith = findHadithByNumber(hadithNumber);
            if (!hadith) return;
            
            const text = currentState.bookTitle + '\\n\\n' +
                        'হাদিস নং: ' + toBanglaNumber(hadith.hadith_number) + '\\n' +
                        (hadith.title || '') + '\\n' +
                        (hadith.description_ar || '') + '\\n' +
                        (hadith.description || '') + '\\n\\n' +
                        'অ্যাপ: ইসলামী বিশ্বকোষ ও আল হাদিস';
            
            if (typeof AndroidApp !== 'undefined') {
                AndroidApp.shareText(text);
            }
        }
        
        // ==================== সার্চ ====================
        let searchTimeout;
        function handleSearch(query) {
            clearTimeout(searchTimeout);
            searchTimeout = setTimeout(() => {
                performSearch(query);
            }, 300);
        }
        
        function performSearch(query) {
            if (!query || query.trim() === '') {
                // সার্চ ক্লিয়ার হলে সব ডেটা দেখান
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
            
            if (currentState.page === 'books') {
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
        
        // ==================== নেভিগেশন ====================
        function handleBack() {
            if (currentState.page === 'hadith') {
                loadSections(currentState.bookId, currentState.bookTitle);
            } else if (currentState.page === 'sections') {
                loadBooks();
            } else {
                if (typeof AndroidApp !== 'undefined') {
                    AndroidApp.backButtonClicked();
                }
            }
        }
        
        // ==================== ইনিশিয়ালাইজেশন ====================
        document.addEventListener('DOMContentLoaded', () => {
            console.log('App initialized');
            loadBooks();
        });
        
        // অ্যান্ড্রয়েড ব্যাক বাটন হ্যান্ডেল
        window.onpopstate = function(event) {
            handleBack();
        };
    </script>
</body>
</html>
        """.trimIndent()
    }

    // জাভাস্ক্রিপ্ট ইন্টারফেস ক্লাস
    inner class AndroidJavaScriptInterface {
        
        @JavascriptInterface
        fun backButtonClicked() {
            runOnUiThread {
                finish()
            }
        }
        
        @JavascriptInterface
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
        
        @JavascriptInterface
        fun cacheData(key: String, data: String) {
            try {
                val cacheDir = File(filesDir, cacheDirName)
                if (!cacheDir.exists()) {
                    cacheDir.mkdirs()
                }
                
                val cacheFile = File(cacheDir, getCacheFileName(key))
                cacheFile.writeText(data)
                
                println("Data cached: $key (${data.length} bytes)")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        @JavascriptInterface
        fun showToast(message: String) {
            runOnUiThread {
                Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
            }
        }
        
        @JavascriptInterface
        fun copyToClipboard(text: String) {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("হাদিস", text)
            clipboard.setPrimaryClip(clip)
        }
        
        @JavascriptInterface
        fun shareText(text: String) {
            val sendIntent = android.content.Intent().apply {
                action = android.content.Intent.ACTION_SEND
                putExtra(android.content.Intent.EXTRA_TEXT, text)
                type = "text/plain"
            }
            val shareIntent = android.content.Intent.createChooser(sendIntent, "শেয়ার করুন")
            startActivity(shareIntent)
        }
        
        @JavascriptInterface
        fun isOnline(): Boolean {
            return isNetworkAvailable()
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
            document.getElementById('offlineIndicator').innerHTML = '⚠️ অফলাইন মোড - ক্যাশে করা ডেটা দেখানো হচ্ছে';
        """.trimIndent(), null)
    }

    override fun onBackPressed() {
        webView.evaluateJavascript("javascript:handleBack()") { result ->
            // জাভাস্ক্রিপ্ট থেকে কোন রেজাল্ট না এলে বা false এলে অ্যাপ বন্ধ করুন
            if (result == "null" || result == "false") {
                super.onBackPressed()
            }
        }
    }
}