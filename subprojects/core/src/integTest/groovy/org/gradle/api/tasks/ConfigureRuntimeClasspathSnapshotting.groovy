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
    def "can ignore files on runtime classpath in directories"() {
        def project = new TestProject()
        project.ignoreFiles()

        when:
        succeeds project.customTask
        then:
        nonSkippedTasks.contains(project.customTask)

        when:
        succeeds project.customTask
        then:
        skippedTasks.contains(project.customTask)

        when:
        project.ignoredResourceInDirectory << "This change should be ignored"
        succeeds project.customTask
        then:
        skippedTasks.contains(project.customTask)

        when:
        project.notIgnoredResourceInDirectory << "This change should not be ignored"
        succeeds project.customTask
        then:
        nonSkippedTasks.contains(project.customTask)
    }

    def "can ignore files on runtime classpath in jars"() {
        def project = new TestProject()
        project.ignoreFiles()

        when:
        succeeds project.customTask
        then:
        nonSkippedTasks.contains(project.customTask)

        when:
        project.createJar()
        succeeds project.customTask
        then:
        skippedTasks.contains(project.customTask)

        when:
        project.ignoredResourceInJar << "This change should be ignored"
        project.createJar()
        succeeds project.customTask
        then:
        skippedTasks.contains(project.customTask)

        when:
        project.notIgnoredResourceInJar << "This change should not be ignored"
        project.createJar()
        succeeds project.customTask
        then:
        nonSkippedTasks.contains(project.customTask)
    }

    @NotYetImplemented
    def "can configure ignore rules per project"() {
        def projectWithIgnores = new TestProject('a')
        projectWithIgnores.ignoreFiles()
        def projectWithoutIgnores = new TestProject('b')
        def allProjects = [projectWithIgnores, projectWithoutIgnores]
        settingsFile << "include 'a', 'b'"

        when:
        succeeds(*allProjects*.customTask)
        then:
        nonSkippedTasks.containsAll(allProjects*.customTask)

        when:
        projectWithIgnores.ignoredResourceInJar << "Should be ignored"
        projectWithoutIgnores.ignoredResourceInJar << "Should not be ignored"
        succeeds(*allProjects*.customTask)
        then:
        skippedTasks.contains(projectWithIgnores.customTask)
        nonSkippedTasks.contains(projectWithoutIgnores.customTask)
    }

    class TestProject {
        final TestFile root
        TestFile ignoredResourceInDirectory
        TestFile notIgnoredResourceInDirectory
        TestFile ignoredResourceInJar
        TestFile notIgnoredResourceInJar
        TestFile libraryJar
        private TestFile libraryJarContents
        private final String projectName

        TestProject(String projectName = null) {
            this.projectName = projectName
            this.root = projectName ? file(projectName) : temporaryFolder.testDirectory

            root.file('build.gradle') << """
                apply plugin: 'base'
                
                class CustomTask extends DefaultTask {
                    @OutputFile File outputFile = new File(temporaryDir, "output.txt")
                    @Classpath FileCollection classpath = project.files("classpath/dirEntry", "library.jar")
                    
                    @TaskAction void generate() {
                        outputFile.text = "done"
                    } 
                }
                
                task customTask(type: CustomTask)
            """.stripIndent()

            root.file('classpath/dirEntry').create {
                ignoredResourceInDirectory = file("ignored.properties") << "This should be ignored"
                notIgnoredResourceInDirectory = file("not-ignored.txt") << "This should not be ignored"
            }

            libraryJarContents = root.file('libraryContents').create {
                ignoredResourceInJar = file('some/package/ignored.properties') << "This should be ignored"
                notIgnoredResourceInJar = file('some/package/not-ignored.properties') << "This should not be ignored"
            }
            libraryJar = root.file('library.jar')
            createJar()
        }

        void createJar() {
            if (libraryJar.exists()) {
                libraryJar.delete()
            }
            libraryJarContents.zipTo(libraryJar)
        }

        void ignoreFiles(String ignores = 'ignore "**/ignored.properties"') {
            root.file('build.gradle') << """
                normalization {
                    runtimeClasspath {
                        ${ignores}
                    }
                }
            """.stripIndent()
        }

        String getCustomTask() {
            return "${projectName ? ":${projectName}" : ''}:customTask"
        }
    }
}
