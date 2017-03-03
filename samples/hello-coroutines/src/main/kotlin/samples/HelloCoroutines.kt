package samples

import kotlin.coroutines.experimental.*

val fibonacci = buildSequence {

    var a = 0
    var b = 1

    while (true) {
        yield(b)

        val next = a + b
        a = b; b = next
    }
}

fun main(args: Array<String>) {
    fibonacci.take(5).forEach(::println)
}

