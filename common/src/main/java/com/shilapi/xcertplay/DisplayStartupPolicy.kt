package com.shilapi.xcertplay

/** A size belongs to one foreground revision and must settle before a new handshake uses it. */
internal class DisplayLayoutStability(private val settleMillis: Long = 500) {
    private var candidate: Pair<Int, Int>? = null
    private var revision: Int? = null
    private var observedAt = 0L

    fun observe(width: Int, height: Int, ticket: Int, now: Long) {
        val size = width to height
        if (candidate != size || revision != ticket || now < observedAt) {
            candidate = size; revision = ticket; observedAt = now
        }
    }
    fun remaining(width: Int, height: Int, ticket: Int?, now: Long): Long {
        if (ticket == null || candidate != (width to height) || revision != ticket || now < observedAt) return settleMillis
        return (settleMillis - (now - observedAt)).coerceAtLeast(0)
    }
    fun ready(width: Int, height: Int, ticket: Int?, now: Long) =
        ticket != null && candidate == (width to height) && revision == ticket && now >= observedAt &&
            remaining(width, height, ticket, now) == 0L
    fun invalidate() { candidate = null; revision = null }
}

internal object DisplaySizingPolicy {
    fun canKeepSingleWindow(width: Int, height: Int, sessionWidth: Int, sessionHeight: Int,
                            splitScreen: Boolean): Boolean = !splitScreen && width > 0 && height > 0 &&
        width <= sessionWidth && height <= sessionHeight &&
            (width > height) == (sessionWidth > sessionHeight)

    fun physicalSize(currentWidth: Int, currentHeight: Int, referenceWidth: Int, referenceHeight: Int,
                     millimeters: Int, basis: com.shilapi.xcertplay.airplay.AirPlayPhysicalSizeBasis)
        : com.shilapi.xcertplay.airplay.AirPlayPhysicalSizeMm {
        val reference = reference(currentWidth, currentHeight, referenceWidth, referenceHeight)
        // The physical reference stays on the device's long/short edge through rotation.
        return com.shilapi.xcertplay.airplay.AirPlayDisplaySettings.resolvePhysicalSizeMm(
            currentWidth, currentHeight, maxOf(reference.first, reference.second),
            minOf(reference.first, reference.second), millimeters, basis)
    }

    /** Preserve a real width/height pair; never combine independent maxima from two orientations. */
    fun reference(currentWidth: Int, currentHeight: Int, reportedWidth: Int, reportedHeight: Int): Pair<Int, Int> {
        require(currentWidth > 0 && currentHeight > 0)
        if (reportedWidth <= 0 || reportedHeight <= 0) return currentWidth to currentHeight
        // A narrow split window can look portrait while the physical screen remains landscape.
        if (reportedWidth >= currentWidth && reportedHeight >= currentHeight) return reportedWidth to reportedHeight
        // Allow stale rotation metrics only when the swapped whole pair actually contains the window.
        if (reportedHeight >= currentWidth && reportedWidth >= currentHeight) return reportedHeight to reportedWidth
        return currentWidth to currentHeight
    }

    fun compatibleSavedSizes(saved: List<Pair<Int, Int>>, reference: Pair<Int, Int>): List<Pair<Int, Int>> {
        val long = maxOf(reference.first, reference.second)
        val short = minOf(reference.first, reference.second)
        return saved.filter { (w, h) -> w > 0 && h > 0 && maxOf(w, h) <= long && minOf(w, h) <= short }
    }
}
