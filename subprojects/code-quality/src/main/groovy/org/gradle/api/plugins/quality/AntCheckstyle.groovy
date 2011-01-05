/*
 * Copyright 2009 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.api.plugins.quality

import org.gradle.api.AntBuilder
import org.gradle.api.GradleException
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.AntBuilderAware

class AntCheckstyle {
    def checkstyle(AntBuilder ant, FileCollection source, File configFile, File resultFile, AntBuilderAware classpath, Map<String, ?> properties, boolean ignoreFailures) {
        String propertyName = "org.gradle.checkstyle.violations"

        ant.project.addTaskDefinition('checkstyle', getClass().classLoader.loadClass('com.puppycrawl.tools.checkstyle.CheckStyleTask'))
        ant.checkstyle(config: configFile, failOnViolation: false, failureProperty: propertyName) {
            source.addToAntBuilder(ant, 'fileset', FileCollection.AntType.FileSet)
            classpath.addToAntBuilder(ant, 'classpath')
            formatter(type: 'plain', useFile: false)
            formatter(type: 'xml', toFile: resultFile)
            properties.each {key, value ->
                property(key: key, value: value.toString())
            }
        }

        if (!ignoreFailures && ant.properties[propertyName]) {
            throw new GradleException("Checkstyle check violations were found in $source. See the report at $resultFile.")
        }
    }
}
