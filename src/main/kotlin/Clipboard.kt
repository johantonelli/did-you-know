import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLElement
import kotlin.js.Promise

fun copyImgToClipboard() {
    val factContainer = document.getElementById("container") as? HTMLElement ?: return
    val textWrapper = document.querySelector(".fact-text-wrapper") as? HTMLElement ?: return
    val imgButton = document.getElementById("img-btn") as? HTMLButtonElement

    // Expand the text wrapper to show full content
    textWrapper.classList.add("expanded")
    factContainer.classList.add("clipboard")

    // Small delay to let the layout settle
    window.setTimeout({
        val options = js("{ backgroundColor: '#FDF6E3', scale: 2, useCORS: true }")

        val html2canvasFunc = window.asDynamic().html2canvas
        if (html2canvasFunc == null || html2canvasFunc == undefined) {
            console.error("html2canvas library not loaded")
            textWrapper.classList.remove("expanded")
            return@setTimeout
        }

        (html2canvasFunc(factContainer, options).unsafeCast<Promise<dynamic>>())
            .then { canvas: dynamic ->
                // Restore the original layout
                textWrapper.classList.remove("expanded")

                // Convert canvas to blob and copy to clipboard
                Promise { resolve: (Unit) -> Unit, _: (Throwable) -> Unit ->
                    canvas.toBlob({ blob: dynamic ->
                        if (blob != null) {
                            copyBlobToClipboard(blob, imgButton)
                        } else {
                            console.error("Failed to create blob from canvas")
                        }
                        resolve(Unit)
                    }, "image/png")
                }
            }
            .catch { error ->
                console.error("Failed to capture image:", error)
                textWrapper.classList.remove("expanded")
            }
    }, 100)
}

private fun copyBlobToClipboard(blob: dynamic, copyButton: HTMLButtonElement?) {
    val clipboardItem = js("new ClipboardItem({ 'image/png': blob })")
    val items = js("[]")
    items.push(clipboardItem)

    window.navigator.clipboard.asDynamic().write(items)
        .then {
            showCopySuccess(copyButton, "fa fa-camera-retro", "Copy image")
            null
        }
        .catch { error: dynamic ->
            console.error("Failed to copy image:", error)
            null
        }
}

fun copyText() {
    val copyButton = document.getElementById("copy-btn") as? HTMLButtonElement
    val page = currentPage ?: return

    val textToCopy = buildString {
        appendLine("Did you know...")
        appendLine()
        appendLine(page.extract)
        appendLine()
        append(page.url)
    }

    window.navigator.clipboard.writeText(textToCopy)
        .then {
            showCopySuccess(copyButton, "fa-regular fa-clipboard", "Copy to Clipboard")
            null
        }
        .catch { error ->
            console.error("Failed to copy:", error)
            null
        }
}

private fun showCopySuccess(button: HTMLButtonElement?, originalIcon: String, originalText: String) {
    val originalHTML = button?.innerHTML ?: "<i class=\"$originalIcon\"></i> $originalText"
    button?.innerHTML = "<i class=\"fa-solid fa-check\"></i> Copied!"
    window.setTimeout({
        button?.innerHTML = originalHTML
    }, 2000)
}