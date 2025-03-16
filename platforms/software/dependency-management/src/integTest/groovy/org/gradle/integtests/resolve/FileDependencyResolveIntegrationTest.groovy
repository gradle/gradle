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

package org.gradle.integtests.resolve

import org.gradle.integtests.fixtures.AbstractDependencyResolutionTest
import org.gradle.integtests.fixtures.extensions.FluidDependenciesResolveTest
import org.gradle.integtests.fixtures.resolve.ResolveTestFixture

@FluidDependenciesResolveTest
class FileDependencyResolveIntegrationTest extends AbstractDependencyResolutionTest {
    def resolve = new ResolveTestFixture(buildFile, "compile")

    def setup() {
        resolve.prepare()
    }

    def "can specify producer task for file dependency"() {
        settingsFile << "include 'sub'; rootProject.name='main'"
        def common = """
            configurations { compile }
            task jar {
                def jarFile = file("\${project.name}.jar")
                doLast {
                    jarFile.text = 'content'
                }
            }
        """
        buildFile << """
            $common
            dependencies {
                compile project(path: ':sub', configuration: 'compile')
                compile files('main.jar') { builtBy jar }
            }
        """

        file("sub/build.gradle") << """
            $common
            dependencies {
                compile files('sub.jar') { builtBy jar }
            }
        """

        when:
        run ":checkDeps"

        then:
        executed ":jar", ":sub:jar", ":checkDeps"
        resolve.expectGraph {
            root(":", ":main:") {
                files << "main.jar"
                files << "sub.jar"
                project(":sub", "main:sub:") {
                    configuration = "compile"
                    noArtifacts()
                }
            }
        }
    }

    def "result includes files that match pattern at the time queried"() {
        settingsFile << "include 'sub'; rootProject.name='main'"
        def common = """
            configurations { compile }
            task jar {
                def jar1 = file("\${project.name}-1.jar")
                def jar2 = file("\${project.name}-2.jar")
                doLast {
                    jar1.text = 'content'
                    jar2.text = 'content'
                }
            }
        """
        buildFile << """
            $common
            dependencies {
                compile project(path: ':sub', configuration: 'compile')
                compile fileTree(dir: projectDir, include: '*.jar', builtBy: [jar])
            }

            // Nothing built yet, result should be empty
            assert configurations.compile.files.empty
        """

        file("sub/build.gradle") << """
            $common
            dependencies {
                compile fileTree(dir: projectDir, include: '*.jar', builtBy: [jar])
            }
        """

        when:
        run ":checkDeps"

        then:
        executed ":jar", ":sub:jar", ":checkDeps"
        resolve.expectGraph {
            root(":", ":main:") {
                files << "main-1.jar"
                files << "main-2.jar"
                files << "sub-1.jar"
                files << "sub-2.jar"
                project(":sub", "main:sub:") {
                    configuration = "compile"
                    noArtifacts()
                }
            }
        }
    }

    def "files are requested once only when dependency is resolved"() {
        buildFile << '''
            def jarFile = file("jar-1.jar")
            jarFile << 'content'
            def libFiles = {
                println "FILES REQUESTED"
                [jarFile]
            }

            configurations { compile }
            dependencies {
                compile files(libFiles)
            }

            task checkFiles {
                def files = configurations.compile
                doLast {
                    assert files.files == [jarFile] as Set
                }
            }
        '''

        when:
        run ":help"

        then:
        outputDoesNotContain("FILES REQUESTED")

        when:
        run ":checkFiles"

        then:
        output.count("FILES REQUESTED") == 1
    }

    def "files referenced by file dependency are included when there is a cycle in the dependency graph"() {
        settingsFile << "include 'sub'; rootProject.name='main'"
        def common = """
            configurations { conf }
            task jar {
                def outputFile = file("\${project.name}.jar")
                outputs.file outputFile
                doLast {
                    outputFile.text = 'content'
                }
            }
        """
        buildFile << """
            $common
            configurations {
                compile
            }
            dependencies {
                compile project(path: ':sub', configuration: 'conf')
                conf project(path: ':sub', configuration: 'conf')
                conf jar.outputs.files
            }
        """

        file("sub/build.gradle") << """
            $common
            dependencies {
                conf jar.outputs.files
                conf project(path: ':', configuration: 'conf')
            }
        """

        when:
        run ":checkDeps"

        then:
        executed ":jar", ":sub:jar", ":checkDeps"
        resolve.expectGraph {
            root(":", ":main:") {
                files << "main.jar"
                files << "sub.jar"
                project(":sub", "main:sub:") {
                    configuration = "compile"
                    noArtifacts()
                    project(":", ":main:") {
                        configuration = "compile"
                        noArtifacts()
                    }
                }
            }
        }
    }

    def "files referenced by file dependency are not included or built when referenced by a non-transitive dependency"() {
        settingsFile << "include 'sub'; rootProject.name='main'"
        def common = """
            configurations { compile }
            task jar {
                def outputFile = file("\${project.name}.jar")
                outputs.file outputFile
                doLast {
                    outputFile.text = 'content'
                }
            }
        """
        buildFile << """
            $common
            dependencies {
                compile project(path: ':sub', configuration: 'compile', transitive: false)
                compile jar.outputs.files
            }
        """

        file("sub/build.gradle") << """
            $common
            dependencies {
                compile jar.outputs.files
            }
        """

        when:
        run ":checkDeps"

        then:
        executed ":jar", ":checkDeps"
        resolve.expectGraph {
            root(":", ":main:") {
                files << "main.jar"
                project(":sub", "main:sub:") {
                    configuration = "compile"
                    noArtifacts()
                }
            }
        }
    }

    def "can select directory using artifact type and compatibility rule"() {
        settingsFile << "rootProject.name='main'"
        file("someDir").createDir()
        buildFile << '''
            configurations {
                compile {
                    attributes {
                        attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, "thing")
                    }
                }
            }
            dependencies {
                attributesSchema {
                    attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE) {
                        compatibilityRules.add(DirectoryIsOk)
                    }
                }
            }
            dependencies {
                compile files("someDir")
            }
            class DirectoryIsOk implements AttributeCompatibilityRule {
                void execute(CompatibilityCheckDetails<String> details) {
                    if (details.producerValue == ArtifactTypeDefinition.DIRECTORY_TYPE) {
                        details.compatible()
                    }
                }
            }
        '''

        when:
        run ":checkDeps"

        then:
        resolve.expectGraph {
            root(":", ":main:") {
                files << "someDir"
            }
        }
    }
}
