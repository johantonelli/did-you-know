import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLAnchorElement
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLElement
import kotlin.js.Json
import kotlin.js.Promise
import kotlin.random.Random

data class WikiPage(
    val title: String,
    val extract: String,
    val url: String
)

data class Category(
    val name: String,
    val displayName: String,
    val wikiCategory: String
)

val predefinedCategories = listOf(
    Category("science", "Science", "Category:Science"),
    Category("history", "History", "Category:History"),
    Category("technology", "Technology", "Category:Technology"),
    Category("animals", "Animals", "Category:Animals"),
    Category("geography", "Geography", "Category:Geography"),
    Category("music", "Music", "Category:Music"),
    Category("art", "Art", "Category:Art"),
    Category("sports", "Sports", "Category:Sports")
)

var currentPage: WikiPage? = null
var currentCategory: String? = null

fun main() {
    window.onload = {
        currentCategory = getCategoryFromUrl()
        setupUI()
        setupCategoryLinks()
        loadRandomFact()
    }
}

fun getCategoryFromUrl(): String? {
    // Use hash-based routing (works everywhere): e.g., #science
    val hash = window.location.hash.trimStart('#').takeIf { it.isNotEmpty() }
    return hash
}

fun setupUI() {
    val reloadButton = document.getElementById("reload-btn") as? HTMLButtonElement
    reloadButton?.onclick = {
        loadRandomFact()
        null
    }

    val copyButton = document.getElementById("copy-btn") as? HTMLButtonElement
    copyButton?.onclick = {
        copyToClipboard()
        null
    }

    updateCategoryDisplay()
}

fun setupCategoryLinks() {
    val categoryLinksContainer = document.getElementById("category-links") as? HTMLElement ?: return

    // Add "All" link
    val allLink = document.createElement("a") as HTMLAnchorElement
    allLink.href = "#"
    allLink.textContent = "All"
    if (currentCategory == null) {
        allLink.classList.add("active")
    }
    allLink.onclick = { e ->
        e.preventDefault()
        window.location.hash = ""
        currentCategory = null
        updateCategoryDisplay()
        loadRandomFact()
        updateActiveLink(categoryLinksContainer, null)
        null
    }
    categoryLinksContainer.appendChild(allLink)

    // Add predefined category links
    predefinedCategories.forEach { category ->
        val link = document.createElement("a") as HTMLAnchorElement
        link.href = "#${category.name}"
        link.textContent = category.displayName
        if (currentCategory?.lowercase() == category.name) {
            link.classList.add("active")
        }
        link.onclick = { e ->
            e.preventDefault()
            window.location.hash = category.name
            currentCategory = category.name
            updateCategoryDisplay()
            loadRandomFact()
            updateActiveLink(categoryLinksContainer, category.name)
            null
        }
        categoryLinksContainer.appendChild(link)
    }
}

fun updateActiveLink(container: HTMLElement, activeCategoryName: String?) {
    val links = container.querySelectorAll("a")
    for (i in 0 until links.length) {
        val link = links.item(i) as? HTMLElement ?: continue
        link.classList.remove("active")
        val linkText = link.textContent?.lowercase()
        val isActive = if (activeCategoryName == null) {
            linkText == "all"
        } else {
            linkText == predefinedCategories.find { it.name == activeCategoryName }?.displayName?.lowercase()
        }
        if (isActive) {
            link.classList.add("active")
        }
    }
}

fun updateCategoryDisplay() {
    val categoryDisplay = document.getElementById("current-category") as? HTMLElement
    val displayName = getCurrentCategoryDisplayName()
    categoryDisplay?.textContent = if (displayName != null) "Category: $displayName" else "All Categories"
}

fun getCurrentCategoryDisplayName(): String? {
    val cat = currentCategory ?: return null
    val predefined = predefinedCategories.find { it.name == cat.lowercase() }
    return predefined?.displayName ?: cat.replaceFirstChar { it.uppercase() }
}

fun loadRandomFact() {
    val factContainer = document.getElementById("fact-container") as? HTMLDivElement
    val loadingDiv = document.getElementById("loading") as? HTMLDivElement
    val copyButton = document.getElementById("copy-btn") as? HTMLButtonElement

    factContainer?.style?.display = "none"
    loadingDiv?.style?.display = "block"
    copyButton?.style?.display = "none"

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

fun fetchRandomWikipediaArticle(): Promise<WikiPage> {
    val category = currentCategory

    return if (category != null) {
        fetchRandomFromCategory(category)
    } else {
        fetchCompletelyRandom()
    }
}

fun fetchCompletelyRandom(): Promise<WikiPage> {
    val url = "https://en.wikipedia.org/w/api.php?" +
            "action=query&" +
            "format=json&" +
            "prop=extracts&" +
            "exintro=true&" +
            "explaintext=true&" +
            "generator=random&" +
            "grnnamespace=0&" +
            "origin=*"

    return window.fetch(url)
        .then { response -> response.json() }
        .then { data ->
            parseWikiPage(data)
        }
}

// Store collected page IDs for random selection
var collectedPageIds = mutableListOf<Int>()

fun fetchRandomFromCategory(categoryName: String): Promise<WikiPage> {
    // Find if it's a predefined category or use as custom
    val predefined = predefinedCategories.find { it.name == categoryName.lowercase() }
    val wikiCategory = predefined?.wikiCategory ?: "Category:${categoryName.replaceFirstChar { it.uppercase() }}"
    val encodedCategory = js("encodeURIComponent")(wikiCategory) as String

    // Clear previous collection
    collectedPageIds = mutableListOf()

    // Fetch multiple pages to build a larger pool (up to 10,000 articles)
    return fetchCategoryPages(encodedCategory, null, 0)
        .then {
            if (collectedPageIds.isEmpty()) {
                throw Exception("No articles found in this category")
            }
            // Pick a random article from our collected pool
            val randomIndex = Random.nextInt(collectedPageIds.size)
            val pageId = collectedPageIds[randomIndex]
            fetchArticleById(pageId)
        }
        .then { page: WikiPage -> page }
}

fun fetchCategoryPages(encodedCategory: String, continueToken: String?, pagesFetched: Int): Promise<Unit> {
    // Stop after collecting ~10,000 articles or 20 requests
    if (pagesFetched >= 10000) {
        return Promise.resolve(Unit)
    }

    var url = "https://en.wikipedia.org/w/api.php?" +
            "action=query&" +
            "format=json&" +
            "list=categorymembers&" +
            "cmtitle=$encodedCategory&" +
            "cmlimit=500&" +
            "cmnamespace=0&" +
            "cmtype=page&" +
            "origin=*"

    if (continueToken != null) {
        url += "&cmcontinue=$continueToken"
    }

    return window.fetch(url)
        .then { response -> response.json() }
        .then { data ->
            val members = data.asDynamic().query?.categorymembers
            if (members != null) {
                val length = members.length as Int
                for (i in 0 until length) {
                    val pageId = members[i].pageid as Int
                    collectedPageIds.add(pageId)
                }
            }

            // Check if there are more pages
            val continueData = data.asDynamic().`continue`
            val nextToken = continueData?.cmcontinue as? String

            if (nextToken != null && collectedPageIds.size < 10000) {
                fetchCategoryPages(encodedCategory, nextToken, collectedPageIds.size)
            } else {
                Promise.resolve(Unit)
            }
        }
        .then { Unit }
}

fun fetchArticleById(pageId: Int): Promise<WikiPage> {
    val url = "https://en.wikipedia.org/w/api.php?" +
            "action=query&" +
            "format=json&" +
            "prop=extracts&" +
            "exintro=true&" +
            "explaintext=true&" +
            "pageids=$pageId&" +
            "origin=*"

    return window.fetch(url)
        .then { response -> response.json() }
        .then { data ->
            parseWikiPage(data)
        }
}

fun parseWikiPage(data: dynamic): WikiPage {
    val pages = data.query.pages as Json
    val pageId = js("Object.keys(pages)[0]") as String
    val page = pages[pageId].asDynamic()

    val title = page.title as String
    val extract = (page.extract as? String) ?: "No description available."
    val articleUrl = "https://en.wikipedia.org/wiki/${js("encodeURIComponent")(title.replace(" ", "_"))}"
    return WikiPage(title, extract, articleUrl)
}

fun displayFact(page: WikiPage) {
    val titleElement = document.getElementById("fact-title")
    val textElement = document.getElementById("fact-text")
    val linkElement = document.getElementById("fact-link")

    titleElement?.textContent = page.title
    textElement?.textContent = page.extract
    (linkElement as? org.w3c.dom.HTMLAnchorElement)?.href = page.url
}

fun displayError() {
    val titleElement = document.getElementById("fact-title")
    val textElement = document.getElementById("fact-text")

    titleElement?.textContent = "Oops!"
    textElement?.textContent = "Failed to load a random fact. Please try again!"
}

fun copyToClipboard() {
    val page = currentPage ?: return

    val textToCopy = """
        Did you know...

        ${page.extract}

        ${page.url}
    """.trimIndent()

    window.navigator.clipboard.writeText(textToCopy)
        .then {
            val copyButton = document.getElementById("copy-btn") as? HTMLButtonElement
            val originalText = copyButton?.textContent
            copyButton?.textContent = "Copied!"
            window.setTimeout({
                copyButton?.textContent = originalText
            }, 2000)
        }
        .catch { error ->
            console.error("Failed to copy:", error)
        }
}
