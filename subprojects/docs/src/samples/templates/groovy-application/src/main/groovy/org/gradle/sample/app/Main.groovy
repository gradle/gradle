package org.gradle.sample.app

import org.gradle.sample.list.LinkedList

import static org.gradle.sample.utilities.StringUtils.join
import static org.gradle.sample.utilities.StringUtils.split
import static org.gradle.sample.app.MessageUtils.getMessage

class Main {
    static void main(String[] args) {
        LinkedList tokens
        tokens = split(getMessage())
        println(join(tokens))
    }
}
