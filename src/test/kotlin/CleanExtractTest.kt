import kotlin.test.Test
import kotlin.test.assertEquals

class CleanExtractTest {

    // Replicate the cleanup patterns from the main code for testing
    private val cleanupPatterns = listOf(
        """\{\\displaystyle[^}]*[}]""" to "",
        """\{\\[a-z]+[^}]*[}]""" to "",
        """\\[a-zA-Z]+""" to "",
        """[{][}]|[{]|[}]""" to "",
        """\s{2,}""" to " ",
        """\s+([.,;:])""" to "$1",
        """[\u2060\u200B\u00A0]+""" to " "
    )

    private fun cleanExtract(text: String): String {
        var result = text
        for ((pattern, replacement) in cleanupPatterns) {
            result = result.replace(Regex(pattern), replacement)
        }
        return result.trim()
    }

    @Test
    fun testCleanExtract_removesLatexDisplaystyle() {
        val input = "The formula {\\displaystyle x=5} is important"
        val expected = "The formula is important"
        assertEquals(expected, cleanExtract(input))
    }

    @Test
    fun testCleanExtract_removesLatexCommands() {
        val input = "Some text {\\textbf bold} more text"
        val expected = "Some text more text"
        assertEquals(expected, cleanExtract(input))
    }

    @Test
    fun testCleanExtract_removesBackslashCommands() {
        val input = "Text with \\alpha and \\beta symbols"
        val expected = "Text with and symbols"
        assertEquals(expected, cleanExtract(input))
    }

    @Test
    fun testCleanExtract_removesCurlyBraces() {
        val input = "Text with {} empty braces and { single"
        val expected = "Text with empty braces and single"
        assertEquals(expected, cleanExtract(input))
    }

    @Test
    fun testCleanExtract_normalizesMultipleSpaces() {
        val input = "Text   with    multiple     spaces"
        val expected = "Text with multiple spaces"
        assertEquals(expected, cleanExtract(input))
    }

    @Test
    fun testCleanExtract_fixesSpaceBeforePunctuation() {
        val input = "Hello , world . How are you"
        val expected = "Hello, world. How are you"
        assertEquals(expected, cleanExtract(input))
    }

    @Test
    fun testCleanExtract_handlesUnicodeSpaces() {
        val input = "Text\u00A0with\u200Bspecial\u2060spaces"
        val expected = "Text with special spaces"
        assertEquals(expected, cleanExtract(input))
    }

    @Test
    fun testCleanExtract_trimsWhitespace() {
        val input = "   Some text with leading and trailing spaces   "
        val expected = "Some text with leading and trailing spaces"
        assertEquals(expected, cleanExtract(input))
    }

    @Test
    fun testCleanExtract_handlesComplexLatex() {
        val input = "Einstein's equation {\\displaystyle E=mc^{2}} changed physics"
        val expected = "Einstein's equation changed physics"
        assertEquals(expected, cleanExtract(input))
    }

    @Test
    fun testCleanExtract_preservesNormalText() {
        val input = "This is a normal sentence without any special characters."
        val expected = "This is a normal sentence without any special characters."
        assertEquals(expected, cleanExtract(input))
    }
}
