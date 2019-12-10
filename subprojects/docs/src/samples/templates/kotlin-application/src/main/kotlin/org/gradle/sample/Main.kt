package org.gradle.sample

fun main() {
    val tokens = StringUtils.split(MessageUtils.getMessage())
    println(StringUtils.join(tokens))
}
