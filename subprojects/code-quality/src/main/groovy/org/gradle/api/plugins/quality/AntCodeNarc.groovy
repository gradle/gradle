/*
 * Copyright 2010 the original author or authors.
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

import org.apache.tools.ant.BuildException
import org.codenarc.ant.CodeNarcTask
import org.gradle.api.AntBuilder
import org.gradle.api.GradleException
import org.gradle.api.file.FileCollection

class AntCodeNarc {
    def execute(AntBuilder ant, FileCollection source, File configFile, String reportFormat, File reportFile, boolean ignoreFailures) {
        ant.project.addTaskDefinition('codenarc', CodeNarcTask)
        try {
            ant.codenarc(ruleSetFiles: "file:$configFile", maxPriority1Violations: 0, maxPriority2Violations: 0, maxPriority3Violations: 0) {
                report(type: reportFormat, toFile: reportFile)
                source.addToAntBuilder(ant, 'fileset', FileCollection.AntType.FileSet)
            }
        } catch (BuildException e) {
            if (e.message.matches('Exceeded maximum number of priority \\d* violations.*')) {
                if (ignoreFailures) {
                    return
                }
                throw new GradleException("CodeNarc check violations were found in $source. See the report at $reportFile.", e)
            }
            throw e
        }
    }
}
