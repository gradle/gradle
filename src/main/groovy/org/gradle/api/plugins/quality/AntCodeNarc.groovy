package org.gradle.api.plugins.quality

import org.gradle.util.ClasspathUtil
import org.gradle.util.HashUtil
import org.gradle.api.tasks.AntBuilderAware
import org.apache.tools.ant.BuildException
import org.gradle.api.GradleException

class AntCodeNarc {
    def execute(def ant, AntBuilderAware source, File configFile, File reportFile) {
        // This approach to getting the config file on the CodeNarc classpath may not be the greatest idea in the world

        String suffix = HashUtil.createHash(configFile.text)
        String resourceName = "${configFile.name}-$suffix"

        reportFile.parentFile.mkdirs()

        ant.copy(file: configFile, toFile: new File(reportFile.parentFile, resourceName))

        File zipFile = new File(reportFile.parentFile, "codenarc-config-${suffix}.jar")
        ant.zip(destfile: zipFile) {
            fileset(dir: reportFile.parentFile, includes: resourceName)
        }

        ClasspathUtil.addUrl(Class.forName('org.codenarc.ant.CodeNarcTask').classLoader, [zipFile])
        
        ant.taskdef(name: 'codenarc', classname: 'org.codenarc.ant.CodeNarcTask')
        try {
            ant.codenarc(ruleSetFiles: resourceName, maxPriority1Violations: 0, maxPriority2Violations: 0, maxPriority3Violations: 0) {
                report(type: 'html', toFile: reportFile)
                source.addToAntBuilder(ant, null)
            }
        } catch (BuildException e) {
            if (e.message.matches('Exceeded maximum number of priority \\d* violations.*')) {
                throw new GradleException("CodeNarc check violations were found in $source. See the report at $reportFile.", e)
            }
            throw e
        }
    }
}