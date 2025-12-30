

import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.*
import org.w3c.dom.events.MouseEvent
import kotlin.js.Promise
import kotlin.random.Random

/* External declaration for html2canvas */
@JsName("html2canvas")
external fun html2canvas(element: HTMLElement, options: dynamic = definedExternally): Promise<HTMLCanvasElement>

data class WikiPage(
    val title: String,
    val extract: String,
    val url: String
)

data class Category(
    val name: String,
    val displayName: String,
    val wikiCategory: String,
    val icon: String
)

val predefinedCategories = listOf(
    Category("physics", "Physics", "Category:Physics", "fa-solid fa-atom"),
    Category("computer-science", "Computer Science", "Category:Computer science", "fa-solid fa-laptop-code"),
    Category("animals", "Animals", "Category:Animals", "fa-solid fa-paw"),
    Category("art", "Art", "Category:Art", "fa-solid fa-palette"),
    Category("historic-buildings", "Historic Buildings", "Category:Historic buildings and structures", "fa-solid fa-building-columns"),
    Category("entropy", "Entropy", "", "fa-solid fa-shuffle")
)

// Text cleaning patterns - applied in cleanExtract()
private val CLEANUP_PATTERNS = listOf(
    """\{\\displaystyle[^}]*[}]""" to "",      // LaTeX displaystyle
    """\{\\[a-z]+[^}]*[}]""" to "",            // Other LaTeX commands
    """\\[a-zA-Z]+""" to "",                   // Backslash commands
    """[{][}]|[{]|[}]""" to "",                // Curly braces
    """\s{2,}""" to " ",                       // Multiple spaces
    """\s+([.,;:])""" to "$1",                 // Space before punctuation
    """[\u2060\u200B\u00A0]+""" to " "         // Unicode spaces
)

var currentPage: WikiPage? = null
private var currentCategory: String? = null
private var entropyData = mutableListOf<Long>()
private const val ENTROPY_COLLECTION_TIME = 5000 // 5 seconds

fun main() {
    window.onload = {
        currentCategory = getCategoryFromUrl()
        setupUI()
        setupCategoryLinks()
        loadRandomFact()
    }
}

private fun getCategoryFromUrl(): String? {
    val hash = window.location.hash.trimStart('#')
    return hash.takeIf { it.isNotEmpty() }
}

private fun setupUI() {
    (document.getElementById("reload-btn") as? HTMLButtonElement)?.onclick = {
        loadRandomFact()
        null
    }

    (document.getElementById("copy-btn") as? HTMLButtonElement)?.onclick = {
        copyText()
        null
    }

    (document.getElementById("img-btn") as? HTMLButtonElement)?.onclick = {
        copyImgToClipboard()
        null
    }

    updateCategoryDisplay()
}

private fun setupCategoryLinks() {
    val container = document.getElementById("category-links") as? HTMLElement ?: return

    // Add "All" link
    createCategoryLink(container, "All", null, "fa-solid fa-layer-group")

    // Add predefined category links
    predefinedCategories.forEach { category ->
        createCategoryLink(container, category.displayName, category.name, category.icon)
    }
}

private fun createCategoryLink(container: HTMLElement, displayName: String, categoryName: String?, icon: String) {
    val link = (document.createElement("a") as? HTMLAnchorElement) ?: return
    link.href = if (categoryName != null) "#$categoryName" else "#"

    // Add icon
    val iconElement = document.createElement("i")
    icon.split(" ").forEach { cls -> iconElement.classList.add(cls) }
    link.appendChild(iconElement)

    // Add text
    val textNode = document.createTextNode(" $displayName")
    link.appendChild(textNode)

    val isActive = if (categoryName == null) {
        currentCategory == null
    } else {
        currentCategory?.lowercase() == categoryName
    }

    if (isActive) {
        link.classList.add("active")
    }

    link.onclick = { event ->
        event.preventDefault()
        window.location.hash = categoryName ?: ""
        currentCategory = categoryName
        updateCategoryDisplay()
        loadRandomFact()
        updateActiveLink(container, categoryName)
        null
    }

    container.appendChild(link)
}

private fun updateActiveLink(container: HTMLElement, activeCategoryName: String?) {
    val links = container.querySelectorAll("a")
    for (i in 0 until links.length) {
        val link = links.item(i) as? HTMLElement ?: continue
        link.classList.remove("active")

        val linkText = link.textContent?.trim()?.lowercase() ?: continue
        val isActive = if (activeCategoryName == null) {
            linkText == "all"
        } else {
            val predefined = predefinedCategories.find { it.name == activeCategoryName }
            linkText == predefined?.displayName?.lowercase()
        }

        if (isActive) {
            link.classList.add("active")
        }
    }
}

private fun updateCategoryDisplay() {
    val categoryDisplay = document.getElementById("current-category") as? HTMLElement ?: return
    val displayName = getCurrentCategoryDisplayName()
    categoryDisplay.textContent = if (displayName != null) "Category: $displayName" else "All Categories"
}

private fun getCurrentCategoryDisplayName(): String? {
    val cat = currentCategory ?: return null
    val predefined = predefinedCategories.find { it.name == cat.lowercase() }
    return predefined?.displayName ?: cat.replaceFirstChar { it.uppercase() }
}

private fun loadRandomFact() {
    val factContainer = document.getElementById("fact-container") as? HTMLDivElement
    val loadingDiv = document.getElementById("loading") as? HTMLDivElement
    val copyButton = document.getElementById("copy-btn") as? HTMLButtonElement

    factContainer?.style?.display = "none"
    loadingDiv?.style?.display = "block"
    copyButton?.style?.display = "none"

    // Check if entropy mode
    if (currentCategory == "entropy") {
        loadingDiv?.style?.display = "none"
        startEntropyCollection()
        return
    }

    fetchRandomWikipediaArticle()
        .then { page ->
            currentPage = page
            displayFact(page)
            loadingDiv?.style?.display = "none"
            factContainer?.style?.display = "block"
            copyButton?.style?.display = "inline-block"
        }
        .catch { error ->
            console.error("Error fetching article:", error)
            displayError()
            loadingDiv?.style?.display = "none"
            factContainer?.style?.display = "block"
        }
}

private fun fetchRandomWikipediaArticle(): Promise<WikiPage> {
    val category = currentCategory
    return if (category != null) {
        fetchRandomFromCategory(category)
    } else {
        fetchCompletelyRandom()
    }
}

private fun fetchCompletelyRandom(): Promise<WikiPage> {
    val url = buildString {
        append("https://en.wikipedia.org/w/api.php?")
        append("action=query&")
        append("format=json&")
        append("prop=extracts&")
        append("exintro=true&")
        append("explaintext=true&")
        append("generator=random&")
        append("grnnamespace=0&")
        append("origin=*")
    }

    return window.fetch(url)
        .then { response -> response.json() }
        .then { data -> parseWikiPage(data) }
}

private fun fetchRandomFromCategory(categoryName: String): Promise<WikiPage> {
    val predefined = predefinedCategories.find { it.name == categoryName.lowercase() }
    val wikiCategory = predefined?.wikiCategory ?: "Category:${categoryName.replaceFirstChar { it.uppercase() }}"
    val encodedCategory = encodeURIComponent(wikiCategory)

    val collectedPageIds = mutableListOf<Int>()

    return fetchCategoryPages(encodedCategory, null, collectedPageIds)
        .then {
            if (collectedPageIds.isEmpty()) {
                throw Exception("No articles found in this category")
            }
            val randomIndex = Random.nextInt(collectedPageIds.size)
            val pageId = collectedPageIds[randomIndex]
            fetchArticleById(pageId)
        }
        .then { page: WikiPage -> page }
}

private fun fetchCategoryPages(
    encodedCategory: String,
    continueToken: String?,
    pageIds: MutableList<Int>
): Promise<Unit> {
    if (pageIds.size >= 10000) {
        return Promise.resolve(Unit)
    }

    val url = buildString {
        append("https://en.wikipedia.org/w/api.php?")
        append("action=query&")
        append("format=json&")
        append("list=categorymembers&")
        append("cmtitle=$encodedCategory&")
        append("cmlimit=500&")
        append("cmnamespace=0&")
        append("cmtype=page&")
        append("origin=*")
        if (continueToken != null) {
            append("&cmcontinue=$continueToken")
        }
    }

    return window.fetch(url)
        .then { response -> response.json() }
        .then { data ->
            val query = data.asDynamic().query
            val members = query?.categorymembers

            if (members != null && members != undefined) {
                val length = members.length as? Int ?: 0
                for (i in 0 until length) {
                    val pageId = members[i].pageid as? Int
                    if (pageId != null) {
                        pageIds.add(pageId)
                    }
                }
            }

            val continueData = data.asDynamic().`continue`
            val nextToken = continueData?.cmcontinue as? String

            if (nextToken != null && pageIds.size < 10000) {
                fetchCategoryPages(encodedCategory, nextToken, pageIds)
            } else {
                Promise.resolve(Unit)
            }
        }
}

private fun fetchArticleById(pageId: Int): Promise<WikiPage> {
    val url = buildString {
        append("https://en.wikipedia.org/w/api.php?")
        append("action=query&")
        append("format=json&")
        append("prop=extracts&")
        append("exintro=true&")
        append("explaintext=true&")
        append("pageids=$pageId&")
        append("origin=*")
    }

    return window.fetch(url)
        .then { response -> response.json() }
        .then { data -> parseWikiPage(data) }
}

private fun parseWikiPage(data: dynamic): WikiPage {
    val query = data.query ?: throw Exception("Invalid API response: missing query")
    val pages = query.pages ?: throw Exception("Invalid API response: missing pages")

    val pageIds = js("Object.keys(pages)") as Array<String>
    if (pageIds.isEmpty()) {
        throw Exception("No pages in response")
    }

    val pageId = pageIds[0]
    val page = pages[pageId]

    val title = (page.title as? String) ?: "Unknown Title"
    val rawExtract = (page.extract as? String) ?: "No description available."
    val extract = cleanExtract(rawExtract)
    val encodedTitle = encodeURIComponent(title.replace(" ", "_"))
    val articleUrl = "https://en.wikipedia.org/wiki/$encodedTitle"

    return WikiPage(title, extract, articleUrl)
}

private fun cleanExtract(text: String): String {
    var result = text
    for ((pattern, replacement) in CLEANUP_PATTERNS) {
        result = result.replace(Regex(pattern), replacement)
    }
    return result.trim()
}

private fun displayFact(page: WikiPage) {
    document.getElementById("fact-title")?.textContent = page.title
    document.getElementById("fact-text")?.textContent = page.extract
    (document.getElementById("fact-link") as? HTMLAnchorElement)?.href = page.url
}

private fun displayError() {
    document.getElementById("fact-title")?.textContent = "Oops!"
    document.getElementById("fact-text")?.textContent = "Failed to load a random fact. Please try again!"
}

// ============== Entropy Collection ==============

private fun startEntropyCollection() {
    entropyData.clear()

    // Create and show overlay
    val overlay = document.createElement("div")
    overlay.id = "entropy-overlay"
    overlay.innerHTML = """
        <div class="entropy-content">
            <div class="entropy-icon"><i class="fa-solid fa-hand-pointer"></i></div>
            <h2>Generating Entropy</h2>
            <p>Move your cursor or touch the screen randomly</p>
            <div class="entropy-progress">
                <div class="entropy-progress-bar" id="entropy-bar"></div>
            </div>
            <div class="entropy-timer" id="entropy-timer">5</div>
            <div class="entropy-particles" id="entropy-particles"></div>
        </div>
    """.trimIndent()
    document.body?.appendChild(overlay)

    val startTime = js("Date.now()") as Double
    var lastParticleTime = 0.0

    // Mouse move handler
    val mouseMoveHandler: (dynamic) -> Unit = { event ->
        val e = event as MouseEvent
        val timestamp = js("Date.now()") as Double
        entropyData.add((e.clientX * 10000 + e.clientY + timestamp.toLong()) xor (entropyData.size.toLong() * 31))

        // Add visual particle (throttle to every 50ms)
        if (timestamp - lastParticleTime > 50) {
            addEntropyParticle(e.clientX.toDouble(), e.clientY.toDouble())
            lastParticleTime = timestamp
        }
    }

    // Touch move handler
    val touchMoveHandler: (dynamic) -> Unit = handler@{ event ->
        val touches = event.touches
        if (touches == null || touches.length == 0) return@handler
        val touch = touches[0]
        val timestamp = js("Date.now()") as Double
        val x = (touch.clientX as Number).toInt()
        val y = (touch.clientY as Number).toInt()
        entropyData.add((x * 10000 + y + timestamp.toLong()) xor (entropyData.size.toLong() * 31))

        // Add visual particle (throttle to every 50ms)
        if (timestamp - lastParticleTime > 50) {
            addEntropyParticle(x.toDouble(), y.toDouble())
            lastParticleTime = timestamp
        }
    }

    document.addEventListener("mousemove", mouseMoveHandler)
    document.addEventListener("touchmove", touchMoveHandler)

    // Update progress bar and timer
    val updateInterval = window.setInterval({
        val elapsed = (js("Date.now()") as Double) - startTime
        val progress = (elapsed / ENTROPY_COLLECTION_TIME * 100).coerceAtMost(100.0)
        val remaining = ((ENTROPY_COLLECTION_TIME - elapsed) / 1000).coerceAtLeast(0.0).toInt()

        (document.getElementById("entropy-bar") as? HTMLElement)?.style?.width = "$progress%"
        (document.getElementById("entropy-timer") as? HTMLElement)?.textContent = remaining.toString()
    }, 100)

    // End collection after 5 seconds
    window.setTimeout({
        window.clearInterval(updateInterval)
        document.removeEventListener("mousemove", mouseMoveHandler)
        document.removeEventListener("touchmove", touchMoveHandler)

        // Remove overlay
        document.getElementById("entropy-overlay")?.remove()

        // Process entropy and fetch article
        processEntropyAndFetch()
    }, ENTROPY_COLLECTION_TIME)
}

private fun addEntropyParticle(x: Double, y: Double) {
    val particles = document.getElementById("entropy-particles") ?: return
    val particle = document.createElement("div")
    particle.className = "entropy-particle"
    particle.asDynamic().style.left = "${x}px"
    particle.asDynamic().style.top = "${y}px"
    particles.appendChild(particle)

    // Remove particle after animation (matches longest CSS duration)
    window.setTimeout({
        particle.remove()
    }, 2500)
}

private fun processEntropyAndFetch() {
    val factContainer = document.getElementById("fact-container") as? HTMLDivElement
    val loadingDiv = document.getElementById("loading") as? HTMLDivElement
    val copyButton = document.getElementById("copy-btn") as? HTMLButtonElement

    loadingDiv?.style?.display = "block"

    // Generate entropy hash
    val entropyHash = generateEntropyHash()

    // Fetch multiple random articles and pick one based on entropy
    val numArticles = 10
    fetchMultipleRandomArticles(numArticles)
        .then { articles ->
            if (articles.isEmpty()) {
                throw Exception("No articles fetched")
            }
            // Use entropy to pick one
            val index = ((entropyHash % articles.size).toInt()).let { if (it < 0) -it else it }
            val selectedPage = articles[index]

            // Avoid same page as last time
            if (selectedPage.title == currentPage?.title && articles.size > 1) {
                articles[(index + 1) % articles.size]
            } else {
                selectedPage
            }
        }
        .then { page ->
            currentPage = page
            displayFact(page)
            loadingDiv?.style?.display = "none"
            factContainer?.style?.display = "block"
            copyButton?.style?.display = "inline-block"
        }
        .catch { error ->
            console.error("Error fetching entropy article:", error)
            displayError()
            loadingDiv?.style?.display = "none"
            factContainer?.style?.display = "block"
        }
}

private fun generateEntropyHash(): Long {
    if (entropyData.isEmpty()) {
        // Fallback to time-based entropy if no movement
        val now = (js("Date.now()") as Double).toLong()
        return now xor Random.nextLong()
    }

    var hash = 0L
    for (value in entropyData) {
        hash = hash * 31 + value
        hash = hash xor (hash shr 16)
    }
    return hash
}

private fun fetchMultipleRandomArticles(count: Int): Promise<List<WikiPage>> {
    // Fetch multiple random articles sequentially to avoid type issues
    val articles = mutableListOf<WikiPage>()

    fun fetchNext(remaining: Int): Promise<List<WikiPage>> {
        if (remaining <= 0) {
            return Promise.resolve(articles.toList())
        }

        val url = buildString {
            append("https://en.wikipedia.org/w/api.php?")
            append("action=query&")
            append("format=json&")
            append("prop=extracts&")
            append("exintro=true&")
            append("explaintext=true&")
            append("generator=random&")
            append("grnnamespace=0&")
            append("origin=*")
        }

        return window.fetch(url)
            .then { response -> response.json() }
            .then { data ->
                try {
                    val page = parseWikiPage(data)
                    articles.add(page)
                } catch (e: Exception) {
                    // Skip failed fetches
                }
                fetchNext(remaining - 1)
            }
            .catch {
                fetchNext(remaining - 1)
            }
            .then { result: List<WikiPage> -> result }
    }

    return fetchNext(count)
}

@Suppress("UNUSED_PARAMETER")
private fun encodeURIComponent(value: String): String =
    js("encodeURIComponent(value)") as String
