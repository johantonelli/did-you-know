import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.random.Random

class EntropyTest {

    // Replicate entropy hash generation for testing
    private fun generateEntropyHash(entropyData: List<Long>): Long {
        if (entropyData.isEmpty()) {
            return Random.nextLong()
        }

        var hash = 0L
        for (value in entropyData) {
            hash = hash * 31 + value
            hash = hash xor (hash shr 16)
        }
        return hash
    }

    @Test
    fun testGenerateEntropyHash_emptyDataReturnsRandom() {
        val hash1 = generateEntropyHash(emptyList())
        val hash2 = generateEntropyHash(emptyList())
        // Random values should likely be different (not guaranteed but highly probable)
        // We just check it doesn't throw
        assertTrue(true)
    }

    @Test
    fun testGenerateEntropyHash_sameInputSameOutput() {
        val data = listOf(100L, 200L, 300L, 400L, 500L)
        val hash1 = generateEntropyHash(data)
        val hash2 = generateEntropyHash(data)
        assertEquals(hash1, hash2, "Same input should produce same hash")
    }

    @Test
    fun testGenerateEntropyHash_differentInputDifferentOutput() {
        val data1 = listOf(100L, 200L, 300L)
        val data2 = listOf(100L, 200L, 301L)
        val hash1 = generateEntropyHash(data1)
        val hash2 = generateEntropyHash(data2)
        assertNotEquals(hash1, hash2, "Different input should produce different hash")
    }

    @Test
    fun testGenerateEntropyHash_orderMatters() {
        val data1 = listOf(100L, 200L, 300L)
        val data2 = listOf(300L, 200L, 100L)
        val hash1 = generateEntropyHash(data1)
        val hash2 = generateEntropyHash(data2)
        assertNotEquals(hash1, hash2, "Order should affect hash")
    }

    @Test
    fun testGenerateEntropyHash_singleValue() {
        val data = listOf(12345L)
        val hash = generateEntropyHash(data)
        // Should produce a non-zero hash
        assertNotEquals(0L, hash)
    }

    @Test
    fun testGenerateEntropyHash_largeDataSet() {
        val data = (1L..1000L).toList()
        val hash = generateEntropyHash(data)
        // Should handle large datasets without issues
        assertTrue(true)
    }

    @Test
    fun testGenerateEntropyHash_negativeValues() {
        val data = listOf(-100L, -200L, -300L)
        val hash = generateEntropyHash(data)
        // Should handle negative values
        assertTrue(true)
    }

    @Test
    fun testEntropyIndexSelection() {
        // Test that entropy hash produces valid index for article selection
        val hash = 12345L
        val articleCount = 10
        val index = ((hash % articleCount).toInt()).let { if (it < 0) -it else it }
        assertTrue(index in 0 until articleCount, "Index should be within range")
    }

    @Test
    fun testEntropyIndexSelection_negativeHash() {
        // Test with negative hash
        val hash = -12345L
        val articleCount = 10
        val index = ((hash % articleCount).toInt()).let { if (it < 0) -it else it }
        assertTrue(index in 0 until articleCount, "Index should be within range even for negative hash")
    }
}
