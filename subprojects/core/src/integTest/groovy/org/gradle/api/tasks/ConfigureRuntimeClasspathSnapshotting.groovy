/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.tasks

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.file.TestFile

class ConfigureRuntimeClasspathSnapshotting extends AbstractIntegrationSpec {
    TestFile ignoredResource
    TestFile notIgnoredResource

    def setup() {
        buildFile << """
            apply plugin: 'base'
            
            class CustomTask extends DefaultTask {
                @OutputFile File outputFile = new File(temporaryDir, "output.txt")
                @Classpath FileCollection classpath = project.fileTree("classpath")
                
                @TaskAction void generate() {
                    outputFile.text = "done"
                } 
            }
            
            task customTask(type: CustomTask)
            
            normalization {
                runtimeClasspath {
                    ignore "**/ignored.properties"
                }
            }
        """.stripIndent()

        file('classpath/dirEntry').create {
            ignoredResource = file("ignored.properties") << "This should be ignored"
            notIgnoredResource = file("not-ignored.txt") << "This should not be ignored"
        }
    }

    def "can ignore files on runtime classpath"() {
        when:
        succeeds 'customTask'
        then:
        nonSkippedTasks.contains(':customTask')

        when:
        succeeds 'customTask'
        then:
        skippedTasks.contains(':customTask')

        when:
        ignoredResource << "This change should be ignored"
        succeeds 'customTask'
        then:
        skippedTasks.contains(':customTask')

        when:
        notIgnoredResource << "This change should not be ignored"
        succeeds 'customTask'
        then:
        nonSkippedTasks.contains(':customTask')
    }
}
