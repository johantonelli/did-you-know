import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLAnchorElement
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.HTMLElement
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
    Category("computer-science", "Computer Science", "Category:Computer science", "fa-solid fa-laptop-code"),
    Category("animals", "Animals", "Category:Animals", "fa-solid fa-paw"),
    Category("art", "Art", "Category:Art", "fa-solid fa-palette"),
    Category(
        "historic-buildings",
        "Historic Buildings",
        "Category:Historic buildings and structures",
        "fa-solid fa-building-columns"
    ),
    Category("entropy", "Entropy", "", "fa-solid fa-shuffle")
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

internal fun displayFact(page: WikiPage) {
    document.getElementById("fact-title")?.textContent = page.title
    document.getElementById("fact-text")?.textContent = page.extract
    (document.getElementById("fact-link") as? HTMLAnchorElement)?.href = page.url
}

@Suppress("UNUSED_PARAMETER")
internal fun encodeURIComponent(value: String): String =
    js("encodeURIComponent(value)") as String