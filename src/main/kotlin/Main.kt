import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLDivElement
import kotlin.js.Json
import kotlin.js.Promise

data class WikiPage(
    val title: String,
    val extract: String,
    val url: String
)

var currentPage: WikiPage? = null

fun main() {
    window.onload = {
        setupUI()
        loadRandomFact()
    }
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
            val pages = (data.asDynamic().query.pages) as Json
            val pageId = js("Object.keys(pages)[0]") as String
            val page = pages[pageId].asDynamic()

            val title = page.title as String
            val extract = page.extract as String
            val articleUrl = "https://en.wikipedia.org/wiki/${title.replace(" ", "_")}"
            WikiPage(title, extract, articleUrl)
        }
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
