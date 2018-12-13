@file:JvmName("Main")
package cli

import core.*

fun main(vararg args: String) {
    val answer = DeepThought.compute()
    println("The answer to the ultimate question of Life, the Universe and Everything is $answer.")
}
