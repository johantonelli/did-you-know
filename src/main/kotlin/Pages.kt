import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLDivElement
import kotlin.js.Promise
import kotlin.random.Random

internal fun getCurrentCategoryDisplayName(): String? {
    val cat = currentCategory ?: return null
    val predefined = predefinedCategories.find { it.name == cat.lowercase() }
    return predefined?.displayName ?: cat.replaceFirstChar { it.uppercase() }
}

internal fun loadRandomFact() {
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

internal fun displayError() {
    document.getElementById("fact-title")?.textContent = "Oops!"
    document.getElementById("fact-text")?.textContent = "Failed to load a random fact. Please try again!"
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