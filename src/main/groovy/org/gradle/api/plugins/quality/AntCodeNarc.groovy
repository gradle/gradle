package org.gradle.api.plugins.quality

class AntCodeNarc {
    def execute(def ant, Iterable<File> srcDirs, File reportFile) {
        ant.taskdef(name: 'codenarc', classname: 'org.codenarc.ant.CodeNarcTask')
        ant.codenarc(ruleSetFiles: 'org/gradle/api/plugins/quality/genericRules.xml', maxPriority1Violations: 0, maxPriority2Violations: 0, maxPriority3Violations: 0) {
            report(type: 'html', toFile: reportFile)
            srcDirs.each {
                fileset(dir: it)
            }
        }
    }
}