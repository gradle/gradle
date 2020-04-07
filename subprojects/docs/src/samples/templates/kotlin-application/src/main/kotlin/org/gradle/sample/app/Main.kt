package org.gradle.sample.app

import org.gradle.sample.utilities.StringUtils

fun main() {
    val tokens = StringUtils.split(MessageUtils.getMessage())
    println(StringUtils.join(tokens))
}
