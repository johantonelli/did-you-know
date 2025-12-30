import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.MouseEvent
import kotlin.random.Random

private var entropyData = mutableListOf<Long>()
private const val ENTROPY_COLLECTION_TIME = 5000 // 5 seconds

internal fun startEntropyCollection() {
    entropyData.clear()

    // Create and show overlay
    val overlay = document.createElement("div")
    overlay.id = "entropy-overlay"
    overlay.innerHTML = """
        <div class="entropy-content">
            <div class="entropy-icon"><i class="fa-solid fa-hand-pointer"></i></div>
            <h2>Generating Entropy</h2>
            <p>Move your cursor or touch the screen randomly</p>
            <div class="entropy-progress">
                <div class="entropy-progress-bar" id="entropy-bar"></div>
            </div>
            <div class="entropy-timer" id="entropy-timer">5</div>
            <div class="entropy-particles" id="entropy-particles"></div>
        </div>
    """.trimIndent()
    document.body?.appendChild(overlay)

    val startTime = js("Date.now()") as Double
    var lastParticleTime = 0.0

    // Mouse move handler
    val mouseMoveHandler: (dynamic) -> Unit = { event ->
        val e = event as MouseEvent
        val timestamp = js("Date.now()") as Double
        entropyData.add((e.clientX * 10000 + e.clientY + timestamp.toLong()) xor (entropyData.size.toLong() * 31))

        // Add visual particle (throttle to every 50ms)
        if (timestamp - lastParticleTime > 50) {
            addEntropyParticle(e.clientX.toDouble(), e.clientY.toDouble())
            lastParticleTime = timestamp
        }
    }

    // Touch move handler
    val touchMoveHandler: (dynamic) -> Unit = handler@{ event ->
        val touches = event.touches
        if (touches == null || touches.length == 0) return@handler
        val touch = touches[0]
        val timestamp = js("Date.now()") as Double
        val x = (touch.clientX as Number).toInt()
        val y = (touch.clientY as Number).toInt()
        entropyData.add((x * 10000 + y + timestamp.toLong()) xor (entropyData.size.toLong() * 31))

        // Add visual particle (throttle to every 50ms)
        if (timestamp - lastParticleTime > 50) {
            addEntropyParticle(x.toDouble(), y.toDouble())
            lastParticleTime = timestamp
        }
    }

    document.addEventListener("mousemove", mouseMoveHandler)
    document.addEventListener("touchmove", touchMoveHandler)

    // Update progress bar and timer
    val updateInterval = window.setInterval({
        val elapsed = (js("Date.now()") as Double) - startTime
        val progress = (elapsed / ENTROPY_COLLECTION_TIME * 100).coerceAtMost(100.0)
        val remaining = ((ENTROPY_COLLECTION_TIME - elapsed) / 1000).coerceAtLeast(0.0).toInt()

        (document.getElementById("entropy-bar") as? HTMLElement)?.style?.width = "$progress%"
        (document.getElementById("entropy-timer") as? HTMLElement)?.textContent = remaining.toString()
    }, 100)

    // End collection after 5 seconds
    window.setTimeout({
        window.clearInterval(updateInterval)
        document.removeEventListener("mousemove", mouseMoveHandler)
        document.removeEventListener("touchmove", touchMoveHandler)

        // Remove overlay
        document.getElementById("entropy-overlay")?.remove()

        // Process entropy and fetch article
        processEntropyAndFetch()
    }, ENTROPY_COLLECTION_TIME)
}

private fun addEntropyParticle(x: Double, y: Double) {
    val particles = document.getElementById("entropy-particles") ?: return
    val particle = document.createElement("div")
    particle.className = "entropy-particle"
    particle.asDynamic().style.left = "${x}px"
    particle.asDynamic().style.top = "${y}px"
    particles.appendChild(particle)

    // Remove particle after animation (matches longest CSS duration)
    window.setTimeout({
        particle.remove()
    }, 2500)
}

private fun processEntropyAndFetch() {
    val factContainer = document.getElementById("fact-container") as? HTMLDivElement
    val loadingDiv = document.getElementById("loading") as? HTMLDivElement
    val copyButton = document.getElementById("copy-btn") as? HTMLButtonElement

    loadingDiv?.style?.display = "block"

    // Generate entropy hash
    val entropyHash = generateEntropyHash()

    // Fetch multiple random articles and pick one based on entropy
    val numArticles = 10
    fetchMultipleRandomArticles(numArticles)
        .then { articles ->
            if (articles.isEmpty()) {
                throw Exception("No articles fetched")
            }
            // Use entropy to pick one
            val index = ((entropyHash % articles.size).toInt()).let { if (it < 0) -it else it }
            val selectedPage = articles[index]

            // Avoid same page as last time
            if (selectedPage.title == currentPage?.title && articles.size > 1) {
                articles[(index + 1) % articles.size]
            } else {
                selectedPage
            }
        }
        .then { page ->
            currentPage = page
            displayFact(page)
            loadingDiv?.style?.display = "none"
            factContainer?.style?.display = "block"
            copyButton?.style?.display = "inline-block"
        }
        .catch { error ->
            console.error("Error fetching entropy article:", error)
            displayError()
            loadingDiv?.style?.display = "none"
            factContainer?.style?.display = "block"
        }
}

private fun generateEntropyHash(): Long {
    if (entropyData.isEmpty()) {
        // Fallback to time-based entropy if no movement
        val now = (js("Date.now()") as Double).toLong()
        return now xor Random.nextLong()
    }

    var hash = 0L
    for (value in entropyData) {
        hash = hash * 31 + value
        hash = hash xor (hash shr 16)
    }
    return hash
}