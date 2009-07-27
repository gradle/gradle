package org.gradle.api.plugins.quality

import org.gradle.api.tasks.AntBuilderAware

class AntCheckstyle {
    def checkstyle(def ant, AntBuilderAware source, File configFile, File resultFile, Iterable<File> classpath) {
        ant.typedef(resource: 'checkstyletask.properties')
        ant.checkstyle(config: configFile) {
            source.addToAntBuilder(ant, 'fileset')
            classpath.each {
                classpath(location: it)
            }
            formatter(type: 'plain', useFile: false)
            formatter(type: 'xml', toFile: resultFile)
        }
    }
}
