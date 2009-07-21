package org.gradle.api.plugins.quality

class AntCheckstyle {
    def checkstyle(def ant, Iterable<File> srcDirs, File configFile, File resultFile, Iterable<File> classpath) {
        ant.typedef(resource: 'checkstyletask.properties')
        ant.checkstyle(config: configFile) {
            srcDirs.each {
                fileset(dir: it)
            }
            classpath.each {
                classpath(location: it)
            }
            formatter(type: 'plain', useFile: false)
            formatter(type: 'xml', toFile: resultFile)
        }
    }
}
