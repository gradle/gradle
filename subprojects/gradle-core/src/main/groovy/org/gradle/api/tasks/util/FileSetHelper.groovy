package org.gradle.api.tasks.util

class FileSetHelper {
    def addToAntBuilder(def node, File dir, PatternSet patternSet, String nodeName) {
        node."${nodeName ?: 'fileset'}"(dir: dir) {
            patternSet.addToAntBuilder(node, null)
        }
    }
}