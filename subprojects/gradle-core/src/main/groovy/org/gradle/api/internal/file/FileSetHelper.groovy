package org.gradle.api.internal.file

import org.gradle.api.tasks.util.PatternSet

class FileSetHelper {
    def addToAntBuilder(def node, File dir, PatternSet patternSet, String nodeName) {
        node."${nodeName ?: 'fileset'}"(dir: dir) {
            patternSet.addToAntBuilder(node, null)
        }
    }
}