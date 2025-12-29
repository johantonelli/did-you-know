import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLAnchorElement
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLElement
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

private var currentPage: WikiPage? = null
private var currentCategory: String? = null

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
        copyToClipboard()
        null
    }

    updateCategoryDisplay()
}

private fun setupCategoryLinks() {
    val container = document.getElementById("category-links") as? HTMLElement ?: return

    // Add "All" link
    createCategoryLink(container, "All", null)

    // Add predefined category links
    predefinedCategories.forEach { category ->
        createCategoryLink(container, category.displayName, category.name)
    }
}

private fun createCategoryLink(container: HTMLElement, displayName: String, categoryName: String?) {
    val link = (document.createElement("a") as? HTMLAnchorElement) ?: return
    link.href = if (categoryName != null) "#$categoryName" else "#"
    link.textContent = displayName

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

        val linkText = link.textContent?.lowercase() ?: continue
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

private fun copyToClipboard() {
    val page = currentPage ?: return

    val textToCopy = buildString {
        appendLine("Did you know...")
        appendLine()
        appendLine(page.extract)
        appendLine()
        append(page.url)
    }

    val copyButton = document.getElementById("copy-btn") as? HTMLButtonElement

    window.navigator.clipboard.writeText(textToCopy)
        .then {
            val originalText = copyButton?.textContent ?: "Copy to Clipboard"
            copyButton?.textContent = "Copied!"
            window.setTimeout({
                copyButton?.textContent = originalText
            }, 2000)
            null
        }
        .catch { error ->
            console.error("Failed to copy:", error)
            null
        }
}

@Suppress("UNUSED_PARAMETER")
private fun encodeURIComponent(value: String): String =
    js("encodeURIComponent(value)") as String
