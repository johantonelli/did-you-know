import kotlinx.browser.window

fun main() {
    window.onload = {
        currentCategory = getCategoryFromUrl()
        setupUI()
        setupCategoryLinks()
        loadRandomFact()
    }
}

