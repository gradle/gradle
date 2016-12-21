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

import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import org.gradle.integtests.fixtures.FluidDependenciesResolveRunner
import org.junit.runner.RunWith

@RunWith(FluidDependenciesResolveRunner)
class ArtifactSelectionIntegrationTest extends AbstractHttpDependencyResolutionTest {
    def setup() {
        settingsFile << """
            rootProject.name = 'root'
            include 'lib'
            include 'ui'
            include 'app'
        """

        buildFile << """
allprojects {
    allprojects {
        repositories {
            ivy { url '${ivyHttpRepo.uri}' }
        }
        dependencies {
            attributesSchema {
               attribute(Attribute.of('usage', String))
            }
        }
    }
    configurations {
        compile {
            attributes usage: 'api'
        }
    }
    task utilJar {
        outputs.file("\${project.name}-util.jar")
    }
    task jar {
        outputs.file("\${project.name}.jar")
    }
    task utilClasses {
        outputs.file("\${project.name}-util.classes")
    }
    task classes {
        outputs.file("\${project.name}.classes")
    }
    task dir {
        outputs.file("\${project.name}")
    }
    task utilDir {
        outputs.file("\${project.name}-util")
    }
}
"""
    }

    def "selects artifacts and files whose format matches the requested"() {
        given:
        def m1 = ivyHttpRepo.module('org', 'test', '1.0')
                    .artifact(name: 'some-jar', type: 'jar')
                    .publish()
        def m2 = ivyHttpRepo.module('org', 'test2', '1.0')
                    .artifact(name: 'some-classes', type: 'classes')
                    .publish()

        buildFile << """
            project(':lib') {
                dependencies {
                    compile utilJar.outputs.files
                    compile utilClasses.outputs.files
                    compile utilDir.outputs.files
                    compile 'org:test:1.0'
                    compile 'org:test2:1.0'
                }
                configurations {
                    compile {
                        outgoing {
                            variants {
                                jarFormat {
                                    artifact file: file('lib.jar'), builtBy: tasks.jar
                                }
                                classesFormat {
                                    artifact file: file('lib.classes'), builtBy: tasks.classes
                                }
                                dirFormat {
                                    artifact file: file('lib'), builtBy: tasks.dir
                                }
                            }
                        }
                    }
                }
            }
            project(':ui') {
                artifacts {
                    compile file: file('ui.jar'), builtBy: jar
                }
            }

            project(':app') {
                configurations {
                    compile {
                        attributes artifactType: 'jar'
                    }
                }

                dependencies {
                    compile project(':lib'), project(':ui')
                }

                task resolve {
                    inputs.files configurations.compile
                    doLast {
                        assert configurations.compile.incoming.artifacts.collect { it.file.name } == ['lib-util.jar', 'lib.jar', 'ui.jar', 'some-jar-1.0.jar']

                        // These do not include files from file dependencies
                        assert configurations.compile.resolvedConfiguration.resolvedArtifacts.collect { it.file.name } == ['lib.jar', 'ui.jar', 'some-jar-1.0.jar']
                        assert configurations.compile.resolvedConfiguration.lenientConfiguration.artifacts.collect { it.file.name } == ['lib.jar', 'ui.jar', 'some-jar-1.0.jar']

                        assert configurations.compile.incoming.files.collect { it.name } == ['lib-util.jar', 'lib.jar', 'ui.jar', 'some-jar-1.0.jar']
                        assert configurations.compile.collect { it.name } == ['lib-util.jar', 'lib.jar', 'ui.jar', 'some-jar-1.0.jar']
                        assert configurations.compile.files.collect { it.name } == ['lib-util.jar', 'lib.jar', 'ui.jar', 'some-jar-1.0.jar']
                        assert configurations.compile.resolve().collect { it.name } == ['lib-util.jar', 'lib.jar', 'ui.jar', 'some-jar-1.0.jar']
                        assert configurations.compile.resolvedConfiguration.files.collect { it.name } == ['lib-util.jar', 'lib.jar', 'ui.jar', 'some-jar-1.0.jar']
                        assert configurations.compile.resolvedConfiguration.getFiles { true }.collect { it.name } == ['lib-util.jar', 'lib.jar', 'ui.jar', 'some-jar-1.0.jar']
                        assert configurations.compile.resolvedConfiguration.lenientConfiguration.files.collect { it.name } == ['lib-util.jar', 'lib.jar', 'ui.jar', 'some-jar-1.0.jar']
                        assert configurations.compile.resolvedConfiguration.lenientConfiguration.getFiles { true }.collect { it.name } == ['lib-util.jar', 'lib.jar', 'ui.jar', 'some-jar-1.0.jar']

                        // Get a view specifying the default type
                        assert configurations.compile.incoming.getFiles(artifactType: 'jar').collect { it.name } == ['lib-util.jar', 'lib.jar', 'ui.jar', 'some-jar-1.0.jar']

                        // Get a view without overriding the type
                        assert configurations.compile.incoming.getFiles(otherAttribute: 'anything').collect { it.name } == ['lib-util.jar', 'lib.jar', 'ui.jar', 'some-jar-1.0.jar']
                    }
                }
            }
        """

        m1.ivy.expectGet()
        m2.ivy.expectGet()
        m1.getArtifact(name: 'some-jar', type: 'jar').expectGet()

        expect:
        succeeds "resolve"
        // Currently builds all file dependencies
        result.assertTasksExecuted(":lib:jar", ":lib:utilClasses", ":lib:utilDir", ":lib:utilJar", ":ui:jar", ":app:resolve")
    }

    def "can create a view that selects different artifacts from the same dependency graph"() {
        given:
        def m1 = ivyHttpRepo.module('org', 'test', '1.0')
                    .artifact(name: 'some-jar', type: 'jar')
                    .publish()
        def m2 = ivyHttpRepo.module('org', 'test2', '1.0')
                    .artifact(name: 'some-classes', type: 'classes')
                    .publish()

        buildFile << """
            project(':lib') {
                dependencies {
                    compile utilJar.outputs.files
                    compile utilClasses.outputs.files
                    compile utilDir.outputs.files
                    compile 'org:test:1.0'
                    compile 'org:test2:1.0'
                }
                configurations {
                    compile {
                        outgoing {
                            variants {
                                jarFormat {
                                    artifact file: file('lib.jar'), builtBy: tasks.jar
                                }
                                classesFormat {
                                    artifact file: file('lib.classes'), builtBy: tasks.classes
                                }
                                dirFormat {
                                    artifact file: file('lib'), builtBy: tasks.dir
                                }
                            }
                        }
                    }
                }
            }
            project(':ui') {
                artifacts {
                    compile file: file('ui.classes'), builtBy: classes
                }
            }

            project(':app') {
                configurations {
                    compile {
                        attributes artifactType: 'jar'
                    }
                }

                dependencies {
                    compile project(':lib'), project(':ui')
                }

                task resolve {
                    def files = configurations.compile.incoming.getFiles(artifactType: 'classes')
                    inputs.files files
                    doLast {
                        assert files.collect { it.name } == ['lib-util.classes', 'lib.classes', 'ui.classes', 'some-classes-1.0.classes']
                    }
                }
            }
        """

        m1.ivy.expectGet()
        m2.ivy.expectGet()
        m2.getArtifact(name: 'some-classes', type: 'classes').expectGet()

        expect:
        succeeds "resolve"
        // Currently builds all file dependencies
        result.assertTasksExecuted(":lib:classes", ":lib:utilClasses", ":lib:utilDir", ":lib:utilJar", ":ui:classes", ":app:resolve")
    }

    def "fails when default variant contains artifacts whose format does not match the requested format"() {
        given:
        buildFile << """
            project(':ui') {
                artifacts {
                    compile file: file('ui.classes'), builtBy: classes
                    compile file: file('ui.jar'), builtBy: jar
                }
            }

            project(':app') {
                configurations {
                    compile {
                        attributes artifactType: 'jar'
                    }
                }

                dependencies {
                    compile project(':ui')
                }

                task resolve {
                    inputs.files configurations.compile
                    doLast {
                        configurations.compile.collect { it.name }
                    }
                }
            }
        """

        expect:
        fails "resolve"
        failure.assertHasCause("Artifact ui.classes (project :ui) is not compatible with requested attributes {artifactType=jar, usage=api}")
        // Currently builds the default variant
        result.assertTasksExecuted(":ui:classes", ":ui:jar", ":app:resolve")
    }

    def "can query the content of view before task graph is calculated"() {
        given:
        def m1 = ivyHttpRepo.module('org', 'test', '1.0')
                    .artifact(name: 'some-jar', type: 'jar')
                    .publish()
        def m2 = ivyHttpRepo.module('org', 'test2', '1.0')
                    .artifact(name: 'some-classes', type: 'classes')
                    .publish()

        buildFile << """
            project(':lib') {
                dependencies {
                    compile utilJar.outputs.files
                    compile utilClasses.outputs.files
                    compile utilDir.outputs.files
                    compile 'org:test:1.0'
                    compile 'org:test2:1.0'
                }
                configurations {
                    compile {
                        outgoing {
                            variants {
                                jarFormat {
                                    artifact file: file('lib.jar'), builtBy: tasks.jar
                                }
                                classesFormat {
                                    artifact file: file('lib.classes'), builtBy: tasks.classes
                                }
                                dirFormat {
                                    artifact file: file('lib'), builtBy: tasks.dir
                                }
                            }
                        }
                    }
                }
            }
            project(':ui') {
                artifacts {
                    compile file: file('ui.classes'), builtBy: classes
                }
            }

            project(':app') {
                configurations {
                    compile {
                        attributes artifactType: 'jar'
                    }
                }

                dependencies {
                    compile project(':lib'), project(':ui')
                }

                task resolve {
                    def files = configurations.compile.incoming.getFiles(artifactType: 'classes')
                    files.each { println it.name }
                    inputs.files files
                    doLast {
                        assert files.collect { it.name } == ['lib-util.classes', 'lib.classes', 'ui.classes', 'some-classes-1.0.classes']
                    }
                }
            }
        """

        m1.ivy.expectGet()
        m2.ivy.expectGet()
        m2.getArtifact(name: 'some-classes', type: 'classes').expectGet()

        expect:
        succeeds "resolve"
        // Currently builds all file dependencies
        result.assertTasksExecuted(":lib:classes", ":lib:utilClasses", ":lib:utilDir", ":lib:utilJar", ":ui:classes", ":app:resolve")
    }

    def "can create a view for configuration that has no attributes"() {
        given:
        buildFile << """
            project(':lib') {
                configurations {
                    compile {
                        outgoing {
                            variants {
                                jarFormat {
                                    artifact file: file('lib.jar'), builtBy: tasks.jar
                                }
                                classesFormat {
                                    artifact file: file('lib.classes'), builtBy: tasks.classes
                                }
                                dirFormat {
                                    artifact file: file('lib'), builtBy: tasks.dir
                                }
                            }
                        }
                    }
                }
            }

            project(':app') {
                configurations {
                    noAttributes
                }

                dependencies {
                    noAttributes project(path: ':lib', configuration: 'compile')
                }

                task resolve {
                    def files = configurations.noAttributes.incoming.getFiles(artifactType: 'classes')
                    inputs.files files
                    doLast {
                        assert files.collect { it.name } == ['lib.classes']
                    }
                }
            }
        """

        expect:
        succeeds "resolve"
        result.assertTasksExecuted(":lib:classes", ":app:resolve")
    }
}
