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

package org.gradle.normalization

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.ToBeFixedForInstantExecution
import org.gradle.integtests.fixtures.UnsupportedWithInstantExecution
import org.gradle.test.fixtures.file.TestFile
import spock.lang.Unroll

@Unroll
class ConfigureRuntimeClasspathNormalizationIntegrationTest extends AbstractIntegrationSpec {

    @ToBeFixedForInstantExecution(because = "classpath normalization")
    def "can ignore files on runtime classpath in #tree (using runtime API: #useRuntimeApi)"() {
        def project = new ProjectWithRuntimeClasspathNormalization(useRuntimeApi).withFilesIgnored()

        def ignoredResource = project[ignoredResourceName]
        def notIgnoredResource = project[notIgnoredResourceName]

        when:
        succeeds project.customTask
        then:
        executedAndNotSkipped(project.customTask)

        when:
        succeeds project.customTask
        then:
        skipped(project.customTask)

        when:
        ignoredResource.changeContents()
        succeeds project.customTask
        then:
        skipped(project.customTask)

        when:
        notIgnoredResource.changeContents()
        succeeds project.customTask
        then:
        executedAndNotSkipped(project.customTask)

        when:
        ignoredResource.remove()
        succeeds project.customTask

        then:
        skipped(project.customTask)

        when:
        ignoredResource.add()
        succeeds project.customTask

        then:
        skipped(project.customTask)

        where:
        tree                 | ignoredResourceName               | notIgnoredResourceName               | useRuntimeApi
        'directories'        | 'ignoredResourceInDirectory'      | 'notIgnoredResourceInDirectory'      | true
        'jars'               | 'ignoredResourceInJar'            | 'notIgnoredResourceInJar'            | true
        'nested jars'        | 'ignoredResourceInNestedJar'      | 'notIgnoredResourceInNestedJar'      | true
        'nested in dir jars' | 'ignoredResourceInNestedInDirJar' | 'notIgnoredResourceInNestedInDirJar' | true
        'directories'        | 'ignoredResourceInDirectory'      | 'notIgnoredResourceInDirectory'      | false
        'jars'               | 'ignoredResourceInJar'            | 'notIgnoredResourceInJar'            | false
        'nested jars'        | 'ignoredResourceInNestedJar'      | 'notIgnoredResourceInNestedJar'      | false
        'nested in dir jars' | 'ignoredResourceInNestedInDirJar' | 'notIgnoredResourceInNestedInDirJar' | false
    }

    def "can ignore manifest attributes on runtime classpath"() {
        def project = new ProjectWithRuntimeClasspathNormalization(true).withManifestAttributesIgnored()

        when:
        succeeds project.customTask
        then:
        executedAndNotSkipped(project.customTask)

        when:
        succeeds project.customTask
        then:
        skipped(project.customTask)

        when:
        project.jarManifest.replaceContents("Manifest-Version: 1.0\nImplementation-Version: 1.0.1")
        succeeds project.customTask
        then:
        skipped(project.customTask)
    }

    @ToBeFixedForInstantExecution(because = "classpath normalization")
    def "can ignore entire manifest on runtime classpath"() {
        def project = new ProjectWithRuntimeClasspathNormalization(true).withManifestIgnored()

        when:
        succeeds project.customTask
        then:
        executedAndNotSkipped(project.customTask)

        when:
        succeeds project.customTask
        then:
        skipped(project.customTask)

        when:
        project.jarManifest.replaceContents("Manifest-Version: 1.0\nImplementation-Version: 1.0.1")
        succeeds project.customTask
        then:
        skipped(project.customTask)
    }

    @ToBeFixedForInstantExecution(because = "classpath normalization")
    def "can ignore all meta-inf files on runtime classpath"() {
        def project = new ProjectWithRuntimeClasspathNormalization(true).withAllMetaInfIgnored()

        when:
        succeeds project.customTask
        then:
        executedAndNotSkipped(project.customTask)

        when:
        succeeds project.customTask
        then:
        skipped(project.customTask)

        when:
        project.jarManifest.replaceContents("Manifest-Version: 1.0\nImplementation-Version: 1.0.1")
        project.jarManifestProperties.replaceContents("implementation-version=1.0.1")
        succeeds project.customTask
        then:
        skipped(project.customTask)
    }

    @ToBeFixedForInstantExecution(because = "classpath normalization")
    def "can ignore manifest properties on runtime classpath"() {
        def project = new ProjectWithRuntimeClasspathNormalization(true).withManifestPropertiesIgnored()

        when:
        succeeds project.customTask
        then:
        executedAndNotSkipped(project.customTask)

        when:
        succeeds project.customTask
        then:
        skipped(project.customTask)

        when:
        project.jarManifestProperties.replaceContents("implementation-version=1.0.1")
        succeeds project.customTask
        then:
        skipped(project.customTask)
    }

    @ToBeFixedForInstantExecution(because = "classpath normalization")
    def "can configure ignore rules per project (using runtime API: #useRuntimeApi)"() {
        def projectWithIgnores = new ProjectWithRuntimeClasspathNormalization('a', useRuntimeApi).withFilesIgnored()
        def projectWithoutIgnores = new ProjectWithRuntimeClasspathNormalization('b', useRuntimeApi)
        def allProjects = [projectWithoutIgnores, projectWithIgnores]
        settingsFile << "include 'a', 'b'"

        when:
        succeeds(*allProjects*.customTask)
        then:
        executedAndNotSkipped(*allProjects*.customTask)

        when:
        projectWithIgnores.ignoredResourceInJar.changeContents()
        projectWithoutIgnores.ignoredResourceInJar.changeContents()
        succeeds(*allProjects*.customTask)
        then:
        skipped(projectWithIgnores.customTask)
        executedAndNotSkipped(projectWithoutIgnores.customTask)

        where:
        useRuntimeApi << [true, false]
    }

    @UnsupportedWithInstantExecution(because = "Task.getProject() during execution")
    def "runtime classpath normalization cannot be changed after first usage (using runtime API: #useRuntimeApi)"() {
        def project = new ProjectWithRuntimeClasspathNormalization(useRuntimeApi)
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

        where:
        useRuntimeApi << [true, false]
    }

    class ProjectWithRuntimeClasspathNormalization {
        final TestFile root
        TestResource ignoredResourceInDirectory
        TestResource notIgnoredResourceInDirectory
        TestResource ignoredResourceInJar
        TestResource ignoredResourceInNestedJar
        TestResource ignoredResourceInNestedInDirJar
        TestResource notIgnoredResourceInJar
        TestResource notIgnoredResourceInNestedJar
        TestResource notIgnoredResourceInNestedInDirJar
        TestResource jarManifest
        TestResource jarManifestProperties
        TestFile libraryJar
        TestFile nestedJar
        TestFile nestedInDirJar
        private TestFile libraryJarContents
        private TestFile nestedJarContents
        private TestFile nestedInDirJarContents
        private final String projectName
        final TestFile buildFile

        ProjectWithRuntimeClasspathNormalization(String projectName = null, boolean useRuntimeApi) {
            this.projectName = projectName
            this.root = projectName ? file(projectName) : temporaryFolder.testDirectory

            buildFile = root.file('build.gradle') << """
                apply plugin: 'base'
            """

            buildFile << declareCustomTask(useRuntimeApi)

            nestedInDirJarContents = root.file('nestedInDirJarContents').create {
                ignoredResourceInNestedInDirJar = new TestResource(file('another/package/ignored.txt') << "This should be ignored", this.&createNestedInDirJar)
                notIgnoredResourceInNestedInDirJar = new TestResource(file('another/package/not-ignored.txt') << "This should not be ignored", this.&createNestedInDirJar)
            }
            root.file('classpath/dirEntry').create {
                ignoredResourceInDirectory = new TestResource(file("ignored.txt") << "This should be ignored")
                notIgnoredResourceInDirectory = new TestResource(file("not-ignored.txt") << "This should not be ignored")
                nestedInDirJar = file('nestedInDir.jar')
            }
            nestedJarContents = root.file('libraryContents').create {
                ignoredResourceInNestedJar = new TestResource(file('some/package/ignored.txt') << "This should be ignored", this.&createJar)
                notIgnoredResourceInNestedJar = new TestResource(file('some/package/not-ignored.txt') << "This should not be ignored", this.&createJar)
            }
            libraryJarContents = root.file('libraryContents').create {
                jarManifest = new TestResource(file('META-INF/MANIFEST.MF') << "Manifest-Version: 1.0\nImplementation-Version: 1.0.0", this.&createJar)
                jarManifestProperties = new TestResource(file('META-INF/build-info.properties') << "implementation-version=1.0.0", this.&createJar)
                ignoredResourceInJar = new TestResource(file('some/package/ignored.txt') << "This should be ignored", this.&createJar)
                notIgnoredResourceInJar = new TestResource(file('some/package/not-ignored.txt') << "This should not be ignored", this.&createJar)
                nestedJar = file('nested.jar')
            }
            libraryJar = root.file('library.jar')
            createJar()
            createNestedInDirJar()
        }

        String declareCustomTask(boolean useRuntimeApi) {
            if (useRuntimeApi) {
                return """
                    task customTask {
                        def outputFile = file("\$temporaryDir/output.txt")
                        inputs.files("classpath/dirEntry", "library.jar")
                            .withPropertyName("classpath")
                            .withNormalizer(ClasspathNormalizer)
                        outputs.file(outputFile)
                            .withPropertyName("outputFile")

                        doLast {
                            outputFile.text = "done"
                        }
                    }
                """
            } else {
                return """
                    class CustomTask extends DefaultTask {
                        @OutputFile File outputFile = new File(temporaryDir, "output.txt")
                        @Classpath FileCollection classpath = project.layout.files("classpath/dirEntry", "library.jar")

                        @TaskAction void generate() {
                            outputFile.text = "done"
                        }
                    }

                    task customTask(type: CustomTask)
                """
            }
        }

        void createJar() {
            if (nestedJar.exists()) {
                nestedJar.delete()
            }
            nestedJarContents.zipTo(nestedJar)
            if (libraryJar.exists()) {
                libraryJar.delete()
            }
            libraryJarContents.zipTo(libraryJar)
        }

        void createNestedInDirJar() {
            if (nestedInDirJar.exists()) {
                nestedInDirJar.delete()
            }
            nestedInDirJarContents.zipTo(nestedInDirJar)
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

        ProjectWithRuntimeClasspathNormalization withAllMetaInfIgnored() {
            root.file('build.gradle') << """
                normalization {
                    runtimeClasspath {
                        metaInf {
                            ignoreCompletely()
                        }
                    }
                }
            """.stripIndent()
            return this
        }

        ProjectWithRuntimeClasspathNormalization withManifestIgnored() {
            root.file('build.gradle') << """
                normalization {
                    runtimeClasspath {
                        metaInf {
                            ignoreManifest()
                        }
                    }
                }
            """.stripIndent()
            return this
        }

        ProjectWithRuntimeClasspathNormalization withManifestAttributesIgnored() {
            root.file('build.gradle') << """
                normalization {
                    runtimeClasspath {
                        metaInf {
                            ignoreAttribute "Implementation-Version"
                        }
                    }
                }
            """.stripIndent()
            return this
        }

        ProjectWithRuntimeClasspathNormalization withManifestPropertiesIgnored() {
            root.file('build.gradle') << """
                normalization {
                    runtimeClasspath {
                        metaInf {
                            ignoreProperty "implementation-version"
                        }
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

        void replaceContents(String contents) {
            backingFile.withWriter { w ->
                w << contents
            }
            finalizedBy()
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
