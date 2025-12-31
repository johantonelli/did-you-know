import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLAnchorElement
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.events.KeyboardEvent
import kotlin.js.Promise

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
    Category("dogs", "Dogs", "Category:Dogs", "fa-solid fa-dog"),
    Category("space", "Space", "Category:Outer space", "fa-solid fa-rocket"),
    Category("music", "Music", "Category:Music", "fa-solid fa-music")
)

// Text cleaning patterns - applied in cleanExtract()
internal val CLEANUP_PATTERNS = listOf(
    """\{\\displaystyle[^}]*[}]""" to "",      // LaTeX displaystyle
    """\{\\[a-z]+[^}]*[}]""" to "",            // Other LaTeX commands
    """\\[a-zA-Z]+""" to "",                   // Backslash commands
    """[{][}]|[{]|[}]""" to "",                // Curly braces
    """\s{2,}""" to " ",                       // Multiple spaces
    """\s+([.,;:])""" to "$1",                 // Space before punctuation
    """[\u2060\u200B\u00A0]+""" to " "         // Unicode spaces
)
var currentPage: WikiPage? = null
internal var currentCategory: String? = null
internal fun getCategoryFromUrl(): String? {
    val hash = window.location.hash.trimStart('#')
    return hash.takeIf { it.isNotEmpty() }
}

internal fun setupUI() {
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

internal fun setupCategoryLinks() {
    val container = document.getElementById("category-links") as? HTMLElement ?: return

    // Add "All" link
    createCategoryLink(container, "All", null, "fa-solid fa-layer-group")

    // Add predefined category links
    predefinedCategories.forEach { category ->
        createCategoryLink(container, category.displayName, category.name, category.icon)
    }
}

internal fun setupEntropyLink() {
    val footer = document.querySelector(".footer") as? HTMLElement ?: return

    // Check if entropy is currently active
    if (currentCategory == "~entropy") {
        footer.classList.add("active")
    }

    footer.onclick = { event ->
        event.preventDefault()
        window.location.hash = "~entropy"
        currentCategory = "~entropy"
        updateCategoryDisplay()
        loadRandomFact()

        // Remove active from category links
        val container = document.getElementById("category-links") as? HTMLElement
        container?.querySelectorAll("a")?.let { links ->
            for (i in 0 until links.length) {
                (links.item(i) as? HTMLElement)?.classList?.remove("active")
            }
        }

        // Add active to footer
        footer.classList.add("active")
        null
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

        // Remove active from footer (entropy)
        (document.querySelector(".footer") as? HTMLElement)?.classList?.remove("active")
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

    categoryDisplay.textContent = when {
        currentCategory == "~entropy" -> "Entropy mode active"
        else -> {
            val displayName = getCurrentCategoryDisplayName()
            if (displayName != null) "Category: $displayName" else "All Categories"
        }
    }
}

internal fun displayFact(page: WikiPage) {
    document.getElementById("fact-title")?.textContent = page.title
    document.getElementById("fact-text")?.textContent = page.extract
    (document.getElementById("fact-link") as? HTMLAnchorElement)?.href = page.url
}

internal fun setupCategorySearch() {
    val searchInput = document.getElementById("category-search-input") as? HTMLInputElement ?: return
    val searchBtn = document.getElementById("category-search-btn") as? HTMLButtonElement ?: return
    val resultsContainer = document.getElementById("category-search-results") as? HTMLElement ?: return

    var searchTimeout: Int? = null

    fun performSearch() {
        val query = searchInput.value.trim()
        if (query.isEmpty()) {
            resultsContainer.innerHTML = ""
            resultsContainer.style.display = "none"
            return
        }

        resultsContainer.innerHTML = "<div class=\"search-loading\">Searching...</div>"
        resultsContainer.style.display = "block"

        searchWikipediaCategories(query).then { categories ->
            if (categories.isEmpty()) {
                resultsContainer.innerHTML = "<div class=\"search-no-results\">No categories found</div>"
            } else {
                resultsContainer.innerHTML = ""
                categories.forEach { category ->
                    val resultItem = document.createElement("div")
                    resultItem.className = "search-result-item"
                    resultItem.textContent = category.displayName
                    resultItem.addEventListener("click", {
                        selectCustomCategory(category.name, category.displayName)
                        searchInput.value = ""
                        resultsContainer.innerHTML = ""
                        resultsContainer.style.display = "none"
                    })
                    resultsContainer.appendChild(resultItem)
                }
            }
        }.catch { error ->
            console.error("Category search failed:", error)
            resultsContainer.innerHTML = "<div class=\"search-error\">Search failed</div>"
        }
    }

    // Debounced search on input
    searchInput.addEventListener("input", {
        searchTimeout?.let { window.clearTimeout(it) }
        searchTimeout = window.setTimeout({ performSearch() }, 300)
    })

    // Search on Enter key
    searchInput.addEventListener("keydown", { event ->
        if ((event as KeyboardEvent).key == "Enter") {
            searchTimeout?.let { window.clearTimeout(it) }
            performSearch()
        }
    })

    // Search on button click
    searchBtn.onclick = {
        searchTimeout?.let { window.clearTimeout(it) }
        performSearch()
        null
    }

    // Hide results when clicking outside
    document.addEventListener("click", { event ->
        val target = event.target as? HTMLElement
        val searchWrapper = document.querySelector(".category-search") as? HTMLElement
        if (searchWrapper != null && target != null && !searchWrapper.contains(target)) {
            resultsContainer.innerHTML = ""
            resultsContainer.style.display = "none"
        }
    })
}

private fun selectCustomCategory(wikiCategory: String, displayName: String) {
    // Create a URL-friendly category name
    val categorySlug = "custom:${encodeURIComponent(wikiCategory)}"

    window.location.hash = categorySlug
    currentCategory = categorySlug

    // Store the custom category info for later use
    customCategoryWiki = wikiCategory
    customCategoryDisplay = displayName

    updateCategoryDisplay()
    loadRandomFact()

    // Update active link styling
    val container = document.getElementById("category-links") as? HTMLElement ?: return
    val links = container.querySelectorAll("a")
    for (i in 0 until links.length) {
        val link = links.item(i) as? HTMLElement ?: continue
        link.classList.remove("active")
    }
}

// Store custom category info
internal var customCategoryWiki: String? = null
internal var customCategoryDisplay: String? = null

@Suppress("UNUSED_PARAMETER")
internal fun encodeURIComponent(value: String): String =
    js("encodeURIComponent(value)") as String