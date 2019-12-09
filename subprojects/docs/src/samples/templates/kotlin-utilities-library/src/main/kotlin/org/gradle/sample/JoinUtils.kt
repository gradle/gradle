package org.gradle.sample

class JoinUtils {
    companion object {
        fun join(source: LinkedList): String {
            val result = StringBuilder()
            for (i in 0..source.size()) {
                if (result.length > 0) {
                    result.append(" ")
                }
                result.append(source.get(i))
            }

            return result.toString()
        }
    }
}