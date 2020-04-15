package org.gradle.sample.utilities

import groovy.transform.PackageScope
import org.gradle.sample.list.LinkedList

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
