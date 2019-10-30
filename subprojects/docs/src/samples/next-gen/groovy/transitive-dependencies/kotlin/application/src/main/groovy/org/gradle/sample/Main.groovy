package org.gradle.sample

import org.gradle.sample.LinkedList

import static org.gradle.sample.StringUtils.join
import static org.gradle.sample.StringUtils.split
import static org.gradle.sample.MessageUtils.getMessage

class Main {
    static void main(String[] args) {
        LinkedList tokens
        tokens = split(getMessage())
        println(join(tokens))
    }
}
