package org.gradle.sample

import groovy.transform.PackageScope

@PackageScope
class JoinUtils {
    static String join(LinkedList source) {
        StringBuilder result = new StringBuilder()
        for (int i = 0; i < source.size(); ++i) {
            if (result.length() > 0) {
                result.append(' ')
            }
            result.append(source.get(i))
        }

        return result.toString()
    }
}