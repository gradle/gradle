/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.integtests.resolve.transform

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class ArtifactTransformIntegrationTest extends AbstractIntegrationSpec {

    def "Can resolve transformed configuration with external dependency"() {
        given:
        buildFile << """
            import org.gradle.api.artifacts.transform.*

            apply plugin: 'java'
            repositories {
                mavenCentral()
            }
            dependencies {
                compile 'com.google.guava:guava:19.0'
            }

            ${fileHashConfigurationAndTransform()}
        """

        when:
        succeeds "resolve"

        then:
        file("build/libs").assertContainsDescendants("guava-19.0.jar.md5")
        file("build/libs/guava-19.0.jar.md5").text == "43bfc49bdc7324f6daaa60c1ee9f3972"
    }

    def "Can resolve transformed configuration with file dependency"() {
        when:
        buildFile << """
            import org.gradle.api.artifacts.transform.*

            apply plugin: 'java'
            dependencies {
                compile gradleApi()
            }

            ${fileHashConfigurationAndTransform()}
        """

        succeeds "resolve"

        then:
        file("build/libs").listFiles().count {it.name.contains('gradle') && it.name.endsWith('.jar.md5')} >= 1
    }

    def "Can filter configuration from dependency"() {
        given:
        settingsFile << """
            rootProject.name = 'root'
            include 'lib'
            include 'app'
        """
        buildFile << """
            project(':lib') {
                apply plugin: 'java'
            }

            project(':app') {
                configurations {
                    filter {
                        format = 'noArtifactOrTransformAvailable'
                    }
                }

                dependencies {
                    filter project(':lib')
                }

                task resolve(type: Copy) {
                    dependsOn configurations.filter
                    from configurations.filter.incoming.artifacts*.file
                    into "\${buildDir}/libs"
                }
            }
        """

        file("lib/src/main/java/Foo.java") << "public class Foo {}"

        when:
        succeeds "resolve"

        then:
        !file("app/build/libs").exists()
    }

    def "Can transform configuration from dependency"() {
        given:
        settingsFile << """
            rootProject.name = 'root'
            include 'lib'
            include 'app'
        """
        buildFile << """
            import org.gradle.api.artifacts.transform.*

            project(':lib') {
                apply plugin: 'java'
            }

            project(':app') {
                configurations {
                    transform {
                        format = 'classpath'
                        resolutionStrategy.registerTransform(JarTransform) {
                            outputDirectory = project.file("\${buildDir}/transformed")
                        }
                    }
                }

                dependencies {
                    transform project(':lib')
                }

                task resolve(type: Copy) {
                    dependsOn configurations.transform
                    from configurations.transform.incoming.artifacts*.file
                    into "\${buildDir}/libs"
                }
            }

            @TransformInput(format = 'jar')
            class JarTransform extends ArtifactTransform {
                private File jar

                @TransformOutput(format = 'classpath')
                File getClasspathElement() {
                    jar
                }

                void transform(File input) {
                    jar = input
                }
            }
        """

        file("lib/src/main/java/Foo.java") << "public class Foo {}"

        when:
        succeeds "resolve"

        then:
        file("app/build/libs/lib.jar").exists()
        file("app/build/libs").listFiles().size() == 1
    }

    def fileHashConfigurationAndTransform() {
        """
        buildscript {
            repositories {
                mavenCentral()
            }
            dependencies {
                classpath 'com.google.guava:guava:19.0'
            }
        }

        configurations {
            hash {
                extendsFrom(configurations.compile)
                format = 'md5'
                resolutionStrategy.registerTransform(FileHasher) {
                    outputDirectory = project.file("\${buildDir}/transformed")
                }
            }
        }

        task resolve(type: Copy) {
            from configurations.hash.incoming.artifacts*.file
            into "\${buildDir}/libs"
        }

        @TransformInput(format = 'jar')
        class FileHasher extends ArtifactTransform {
            private File output

            @TransformOutput(format = 'md5')
            File getOutput() {
                return output
            }

            void transform(File input) {
                output = new File(outputDirectory, input.name + ".md5")
                println "Transforming \${input} to \${output}"

                if (!output.exists()) {
                    def inputHash = com.google.common.io.Files.hash(input, com.google.common.hash.Hashing.md5())
                    output << inputHash
                }
            }
        }
        """
    }
}
