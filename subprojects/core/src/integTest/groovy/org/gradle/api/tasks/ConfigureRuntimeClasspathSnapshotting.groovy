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

import groovy.transform.NotYetImplemented
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.file.TestFile

class ConfigureRuntimeClasspathSnapshotting extends AbstractIntegrationSpec {
    TestFile ignoredResourceInDirectory
    TestFile notIgnoredResourceInDirectory
    TestFile ignoredResourceInJar
    TestFile notIgnoredResourceInJar
    TestFile libraryJarContents
    TestFile libraryJar

    def setup() {
        buildFile << """
            apply plugin: 'base'
            
            class CustomTask extends DefaultTask {
                @OutputFile File outputFile = new File(temporaryDir, "output.txt")
                @Classpath FileCollection classpath = project.files("classpath/dirEntry", "library.jar")
                
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
            ignoredResourceInDirectory = file("ignored.properties") << "This should be ignored"
            notIgnoredResourceInDirectory = file("not-ignored.txt") << "This should not be ignored"
        }

        libraryJarContents = file('libraryContents').create {
            ignoredResourceInJar = file('some/package/ignored.properties') << "This should be ignored"
            notIgnoredResourceInJar = file('some/package/not-ignored.properties') << "This should not be ignored"
        }
        libraryJar = file('library.jar')
        createJar()
    }

    def "can ignore files on runtime classpath in directories"() {
        when:
        succeeds 'customTask'
        then:
        nonSkippedTasks.contains(':customTask')

        when:
        succeeds 'customTask'
        then:
        skippedTasks.contains(':customTask')

        when:
        ignoredResourceInDirectory << "This change should be ignored"
        succeeds 'customTask'
        then:
        skippedTasks.contains(':customTask')

        when:
        notIgnoredResourceInDirectory << "This change should not be ignored"
        succeeds 'customTask'
        then:
        nonSkippedTasks.contains(':customTask')
    }

    @NotYetImplemented
    def "can ignore files on runtime classpath in jars"() {
        when:
        succeeds 'customTask'
        then:
        nonSkippedTasks.contains(':customTask')

        when:
        createJar()
        succeeds 'customTask'
        then:
        skippedTasks.contains(':customTask')

        when:
        ignoredResourceInJar << "This change should be ignored"
        createJar()
        succeeds 'customTask'
        then:
        skippedTasks.contains(':customTask')

        when:
        notIgnoredResourceInJar << "This change should not be ignored"
        createJar()
        succeeds 'customTask'
        then:
        nonSkippedTasks.contains(':customTask')
    }

    private TestFile createJar() {
        if (libraryJar.exists()) {
            libraryJar.delete()
        }
        libraryJarContents.zipTo(libraryJar)
    }
}
