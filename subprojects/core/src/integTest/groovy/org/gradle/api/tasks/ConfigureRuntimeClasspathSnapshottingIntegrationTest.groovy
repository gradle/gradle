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
import spock.lang.Unroll

@Unroll
class ConfigureRuntimeClasspathSnapshottingIntegrationTest extends AbstractIntegrationSpec {
    def "can ignore files on runtime classpath in #tree"() {
        def project = new ProjectWithRuntimeClasspathNormalization().withFilesIgnored()

        def ignoredResource = project[ignoredResourceName]
        def notIgnoredResource = project[notIgnoredResourceName]

        when:
        succeeds project.customTask
        then:
        nonSkippedTasks.contains(project.customTask)

        when:
        succeeds project.customTask
        then:
        skippedTasks.contains(project.customTask)

        when:
        ignoredResource.changeContents()
        succeeds project.customTask
        then:
        skippedTasks.contains(project.customTask)

        when:
        notIgnoredResource.changeContents()
        succeeds project.customTask
        then:
        nonSkippedTasks.contains(project.customTask)

        when:
        ignoredResource.remove()
        succeeds project.customTask

        then:
        skippedTasks.contains(project.customTask)

        when:
        ignoredResource.add()
        succeeds project.customTask

        then:
        skippedTasks.contains(project.customTask)

        where:
        tree          | ignoredResourceName          | notIgnoredResourceName
        'directories' | 'ignoredResourceInDirectory' | 'notIgnoredResourceInDirectory'
        'jars'        | 'ignoredResourceInJar'       | 'notIgnoredResourceInJar'
    }

    def "can configure ignore rules per project"() {
        def projectWithIgnores = new ProjectWithRuntimeClasspathNormalization('a').withFilesIgnored()
        def projectWithoutIgnores = new ProjectWithRuntimeClasspathNormalization('b')
        def allProjects = [projectWithoutIgnores, projectWithIgnores]
        settingsFile << "include 'a', 'b'"

        when:
        succeeds(*allProjects*.customTask)
        then:
        nonSkippedTasks.containsAll(allProjects*.customTask)

        when:
        projectWithIgnores.ignoredResourceInJar.changeContents()
        projectWithoutIgnores.ignoredResourceInJar.changeContents()
        succeeds(*allProjects*.customTask)
        then:
        skippedTasks.contains(projectWithIgnores.customTask)
        nonSkippedTasks.contains(projectWithoutIgnores.customTask)
    }

    def "runtime classpath normalization cannot be changed after first usage"() {
        def project = new ProjectWithRuntimeClasspathNormalization()
        project.buildFile << """
            task configureNormalization() {
                dependsOn '${project.customTask}'
                doLast {
                    project.normalization {
                        runtimeClasspath {
                            ignore '**/some-other-file.txt'
                        }
                    }
                }
            }
        """.stripIndent()

        when:
        fails 'configureNormalization'

        then:
        failureHasCause 'Cannot configure runtime classpath normalization after execution started.'
    }

    class ProjectWithRuntimeClasspathNormalization {
        final TestFile root
        TestResource ignoredResourceInDirectory
        TestResource notIgnoredResourceInDirectory
        TestResource ignoredResourceInJar
        TestResource notIgnoredResourceInJar
        TestFile libraryJar
        private TestFile libraryJarContents
        private final String projectName
        final TestFile buildFile

        ProjectWithRuntimeClasspathNormalization(String projectName = null) {
            this.projectName = projectName
            this.root = projectName ? file(projectName) : temporaryFolder.testDirectory

            buildFile = root.file('build.gradle') << """
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
                ignoredResourceInDirectory = new TestResource(file("ignored.txt") << "This should be ignored")
                notIgnoredResourceInDirectory = new TestResource(file("not-ignored.txt") << "This should not be ignored")
            }

            libraryJarContents = root.file('libraryContents').create {
                ignoredResourceInJar = new TestResource(file('some/package/ignored.txt') << "This should be ignored", this.&createJar)
                notIgnoredResourceInJar = new TestResource(file('some/package/not-ignored.txt') << "This should not be ignored", this.&createJar)
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

        ProjectWithRuntimeClasspathNormalization withFilesIgnored() {
            root.file('build.gradle') << """
                normalization {
                    runtimeClasspath {
                        ignore "**/ignored.txt"
                    }
                }
            """.stripIndent()
            return this
        }

        String getCustomTask() {
            return "${projectName ? ":${projectName}" : ''}:customTask"
        }
    }

    class TestResource {
        final TestFile backingFile
        private final Closure finalizedBy

        TestResource(TestFile backingFile, Closure finalizedBy = {}) {
            this.backingFile = backingFile
            this.finalizedBy = finalizedBy
        }

        void changeContents() {
            backingFile << "More changes"
            finalizedBy()
        }

        void remove() {
            assert backingFile.delete()
            finalizedBy()
        }

        void add() {
            backingFile << "First creation of file"
            finalizedBy()
        }
    }
}
