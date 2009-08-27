package org.gradle.api.plugins.quality

import org.gradle.api.GradleException
import org.gradle.api.tasks.AntBuilderAware

class AntCheckstyle {
    def checkstyle(def ant, AntBuilderAware source, File configFile, File resultFile, AntBuilderAware classpath, Map<String, ?> properties) {
        String propertyName = "org.gradle.checkstyle.violations"

        ant.typedef(resource: 'checkstyletask.properties')
        ant.checkstyle(config: configFile, failOnViolation: false, failureProperty: propertyName) {
            source.addToAntBuilder(ant, 'fileset')
            classpath.addToAntBuilder(ant, 'classpath')
            formatter(type: 'plain', useFile: false)
            formatter(type: 'xml', toFile: resultFile)
            properties.each {key, value ->
                property(key: key, value: value.toString())
            }
        }

        if (ant.properties[propertyName]) {
            throw new GradleException("Checkstyle check violations were found in $source. See the report at $resultFile.")
        }
    }
}
