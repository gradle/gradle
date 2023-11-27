package org.gradle.sample.utilities

import org.gradle.sample.list.LinkedList

class StringUtils {
    static String join(LinkedList source) {
        return JoinUtils.join(source)
    }

    static LinkedList split(String source) {
        return SplitUtils.split(source)
    }
}
