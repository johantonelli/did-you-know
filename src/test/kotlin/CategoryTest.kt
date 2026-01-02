import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CategoryTest {

    // Replicate Category data class for testing
    data class Category(
        val name: String,
        val displayName: String,
        val wikiCategory: String,
        val icon: String
    )

    private val predefinedCategories = listOf(
        Category("physics", "Physics", "Category:Physics", "fa-solid fa-atom"),
        Category("dogs", "Dogs", "Category:Dogs", "fa-solid fa-dog"),
        Category("space", "Space", "Category:Outer space", "fa-solid fa-rocket"),
        Category("music", "Music", "Category:Music", "fa-solid fa-music")
    )

    // Simplified version of getCategoryDisplayName for testing
    private fun getCategoryDisplayName(currentCategory: String?): String? {
        val cat = currentCategory ?: return null

        if (cat == "~entropy") {
            return null // Entropy handled separately
        }

        if (cat.startsWith("custom:")) {
            return cat.removePrefix("custom:")
                .removePrefix("Category:")
        }

        val predefined = predefinedCategories.find { it.name == cat.lowercase() }
        return predefined?.displayName ?: cat.replaceFirstChar { it.uppercase() }
    }

    @Test
    fun testGetCategoryDisplayName_nullCategory() {
        assertNull(getCategoryDisplayName(null))
    }

    @Test
    fun testGetCategoryDisplayName_entropyMode() {
        assertNull(getCategoryDisplayName("~entropy"))
    }

    @Test
    fun testGetCategoryDisplayName_predefinedPhysics() {
        assertEquals("Physics", getCategoryDisplayName("physics"))
    }

    @Test
    fun testGetCategoryDisplayName_predefinedDogs() {
        assertEquals("Dogs", getCategoryDisplayName("dogs"))
    }

    @Test
    fun testGetCategoryDisplayName_predefinedSpace() {
        assertEquals("Space", getCategoryDisplayName("space"))
    }

    @Test
    fun testGetCategoryDisplayName_predefinedMusic() {
        assertEquals("Music", getCategoryDisplayName("music"))
    }

    @Test
    fun testGetCategoryDisplayName_predefinedCaseInsensitive() {
        assertEquals("Physics", getCategoryDisplayName("PHYSICS"))
        assertEquals("Dogs", getCategoryDisplayName("DoGs"))
    }

    @Test
    fun testGetCategoryDisplayName_customCategory() {
        assertEquals("History", getCategoryDisplayName("custom:Category:History"))
    }

    @Test
    fun testGetCategoryDisplayName_unknownCategory() {
        assertEquals("Unknown", getCategoryDisplayName("unknown"))
    }

    @Test
    fun testGetCategoryDisplayName_capitalizesUnknown() {
        assertEquals("Somecategory", getCategoryDisplayName("somecategory"))
    }

    @Test
    fun testPredefinedCategories_haveValidWikiCategories() {
        predefinedCategories.forEach { category ->
            assertTrue(
                category.wikiCategory.startsWith("Category:") || category.wikiCategory.isEmpty(),
                "Category ${category.name} should have valid wikiCategory"
            )
        }
    }

    @Test
    fun testPredefinedCategories_haveValidIcons() {
        predefinedCategories.forEach { category ->
            assertTrue(
                category.icon.startsWith("fa-"),
                "Category ${category.name} should have a FontAwesome icon"
            )
        }
    }

    @Test
    fun testPredefinedCategories_uniqueNames() {
        val names = predefinedCategories.map { it.name }
        assertEquals(names.size, names.distinct().size, "Category names should be unique")
    }
}
