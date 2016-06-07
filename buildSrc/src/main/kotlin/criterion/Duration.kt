package criterion

import java.lang.System.nanoTime

data class Duration(val ns: Double) {
    val ms: Double
        get() = ns / 1e+6
}

inline fun durationOf(block: () -> Unit): Duration =
    nanoTimeOf(block).let {
        Duration(it.toDouble())
    }

inline fun nanoTimeOf(block: () -> Unit): Long =
    nanoTime().let { start ->
        block()
        nanoTime() - start
    }
