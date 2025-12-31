import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLElement
import kotlin.js.Promise
import kotlin.random.Random

internal fun getCurrentCategoryDisplayName(): String? {
    val cat = currentCategory ?: return null

    // Handle entropy mode specially
    if (cat == "~entropy") {
        return null // Will be handled separately in updateCategoryDisplay
    }

    // Handle custom categories
    if (cat.startsWith("custom:")) {
        return customCategoryDisplay ?: cat.removePrefix("custom:")
            .let { decodeURIComponent(it) }
            .removePrefix("Category:")
    }

    val predefined = predefinedCategories.find { it.name == cat.lowercase() }
    return predefined?.displayName ?: cat.replaceFirstChar { it.uppercase() }
}

@Suppress("UNUSED_PARAMETER")
private fun decodeURIComponent(value: String): String =
    js("decodeURIComponent(value)") as String

internal fun loadRandomFact() {
    val factContainer = document.getElementById("fact-container") as? HTMLDivElement
    val loadingDiv = document.getElementById("loading") as? HTMLDivElement
    val copyButton = document.getElementById("copy-btn") as? HTMLButtonElement
    val imgButton = document.getElementById("img-btn") as? HTMLButtonElement
    val reloadButton = document.getElementById("reload-btn") as? HTMLButtonElement
    val buttonContainer = document.querySelector(".button-container") as? HTMLElement

    factContainer?.style?.display = "none"
    loadingDiv?.style?.display = "block"
    copyButton?.style?.display = "none"
    buttonContainer?.style?.display = "none"

    // Check if entropy mode
    if (currentCategory == "~entropy") {
        loadingDiv?.style?.display = "none"
        startEntropyCollection()
        return
    }

    fetchRandomWikipediaArticle()
        .then { page ->
            currentPage = page
            displayFact(page)
            loadingDiv?.style?.display = "none"
            factContainer?.style?.display = "flex"
            buttonContainer?.style?.display = "block"
            copyButton?.style?.display = "inline-block"
            imgButton?.style?.display = "inline-block"
            reloadButton?.innerHTML = "<i class=\"fa-solid fa-shuffle\"></i> Another Fact, Please"
        }
        .catch { error ->
            console.error("Error fetching article:", error)
            val errorMessage = error.toString()
            val displayMessage = if (errorMessage.contains("No articles found")) {
                "No articles found in this category. Try a different one!"
            } else {
                null
            }
            displayError(displayMessage)
            loadingDiv?.style?.display = "none"
            factContainer?.style?.display = "flex"
            buttonContainer?.style?.display = "block"
            imgButton?.style?.display = "none"
            reloadButton?.innerHTML = "<i class=\"fa-solid fa-rotate-right\"></i> Try Again"
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
    // Handle custom categories (format: custom:Category%3ACategoryName)
    val wikiCategory = if (categoryName.startsWith("custom:")) {
        customCategoryWiki ?: decodeURIComponent(categoryName.removePrefix("custom:"))
    } else {
        val predefined = predefinedCategories.find { it.name == categoryName.lowercase() }
        predefined?.wikiCategory ?: "Category:${categoryName.replaceFirstChar { it.uppercase() }}"
    }
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

internal fun displayError(message: String? = null) {
    document.getElementById("fact-title")?.textContent = "Oops!"
    val errorMessage = message ?: "Failed to load a random fact. Please try again!"
    document.getElementById("fact-text")?.textContent = errorMessage
}

data class WikiCategory(
    val name: String,
    val displayName: String
)

fun searchWikipediaCategories(searchTerm: String): Promise<List<WikiCategory>> {
    if (searchTerm.isBlank()) {
        return Promise.resolve(emptyList())
    }

    val encodedQuery = encodeURIComponent(searchTerm)
    val url = buildString {
        append("https://en.wikipedia.org/w/api.php?")
        append("action=query&")
        append("format=json&")
        append("list=allcategories&")
        append("acprefix=$encodedQuery&")
        append("aclimit=8&")
        append("origin=*")
    }

    return window.fetch(url)
        .then { response -> response.json() }
        .then { data -> parseCategoryResults(data) }
}

private fun parseCategoryResults(data: dynamic): List<WikiCategory> {
    val categories = mutableListOf<WikiCategory>()
    try {
        val queryResult = data.query
        val allcategories = queryResult?.allcategories

        if (allcategories != null && allcategories != undefined) {
            val length = allcategories.length as? Int ?: 0
            for (i in 0 until length) {
                val item = allcategories[i]
                val categoryName = item["*"] as? String
                if (categoryName != null) {
                    categories.add(WikiCategory(
                        name = "Category:$categoryName",
                        displayName = categoryName
                    ))
                }
            }
        }
    } catch (e: Exception) {
        console.error("Error parsing category results:", e)
    }
    return categories.toList()
}

fun fetchMultipleRandomArticles(count: Int): Promise<List<WikiPage>> {
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