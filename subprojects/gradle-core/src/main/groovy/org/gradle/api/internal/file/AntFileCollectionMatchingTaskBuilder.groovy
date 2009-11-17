package org.gradle.api.internal.file

import org.gradle.api.tasks.AntBuilderAware

class AntFileCollectionMatchingTaskBuilder implements AntBuilderAware {
    private final Iterable<FileSet> fileSets

    def AntFileCollectionMatchingTaskBuilder(Iterable<FileSet> fileSets) {
        this.fileSets = fileSets
    }

    def addToAntBuilder(Object node, String childNodeName) {
        fileSets.each {FileSet fileSet ->
            node."$childNodeName"(location: fileSet.dir)
        }
        node.or {
            fileSets.each {FileSet fileSet ->
                and {
                    gradleBaseDirSelector(baseDir: fileSet.dir)
                    fileSet.patternSet.addToAntBuilder(node, null)
                }
            }
        }
    }
}
