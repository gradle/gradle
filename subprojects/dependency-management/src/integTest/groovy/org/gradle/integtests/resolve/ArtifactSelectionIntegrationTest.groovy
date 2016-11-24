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

    // Documents existing matching behaviour, not desired behaviour
    def "excludes artifacts and files with format that does not match requested from the result"() {
        given:
        def m1 = ivyHttpRepo.module('org', 'test', '1.0')
                    .artifact(name: 'some-jar', type: 'jar')
                    .artifact(name: 'some-classes', type: 'classes')
                    .artifact(name: 'some-lib', type: 'lib')
                    .publish()

        buildFile << """
            allprojects {
                repositories {
                    ivy { url '${ivyHttpRepo.uri}' }
                }
            }
            project(':lib') {
                dependencies {
                    compile utilJar.outputs.files
                    compile utilClasses.outputs.files
                    compile utilDir.outputs.files
                    compile 'org:test:1.0'
                }
                artifacts {
                    compile file: file('lib.jar'), builtBy: jar
                    compile file: file('lib.classes'), builtBy: classes
                    compile file: file('lib'), builtBy: dir
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
                    inputs.files configurations.compile
                    doLast {
                        assert configurations.compile.incoming.artifacts.collect { it.file.name } == ['lib-util.jar', 'lib.jar', 'some-jar-1.0.jar']
                        
                        // These do not include files from file dependencies
                        assert configurations.compile.resolvedConfiguration.resolvedArtifacts.collect { it.file.name } == ['lib.jar', 'some-jar-1.0.jar']
                        assert configurations.compile.resolvedConfiguration.lenientConfiguration.artifacts.collect { it.file.name } == ['lib.jar', 'some-jar-1.0.jar']

                        assert configurations.compile.incoming.files.collect { it.name } == ['lib-util.jar', 'lib.jar', 'some-jar-1.0.jar']
                        assert configurations.compile.collect { it.name } == ['lib-util.jar', 'lib.jar', 'some-jar-1.0.jar']
                        assert configurations.compile.files.collect { it.name } == ['lib-util.jar', 'lib.jar', 'some-jar-1.0.jar']
                        assert configurations.compile.resolve().collect { it.name } == ['lib-util.jar', 'lib.jar', 'some-jar-1.0.jar']
                        assert configurations.compile.resolvedConfiguration.files.collect { it.name } == ['lib-util.jar', 'lib.jar', 'some-jar-1.0.jar']
                        assert configurations.compile.resolvedConfiguration.getFiles { true }.collect { it.name } == ['lib-util.jar', 'lib.jar', 'some-jar-1.0.jar']
                        assert configurations.compile.resolvedConfiguration.lenientConfiguration.files.collect { it.name } == ['lib-util.jar', 'lib.jar', 'some-jar-1.0.jar']
                        assert configurations.compile.resolvedConfiguration.lenientConfiguration.getFiles { true }.collect { it.name } == ['lib-util.jar', 'lib.jar', 'some-jar-1.0.jar']
                        
                        // Get a view specifying the default type
                        assert configurations.compile.incoming.getFiles(artifactType: 'jar').collect { it.name } == ['lib-util.jar', 'lib.jar', 'some-jar-1.0.jar']
                        
                        // Get a view without overriding the type
                        assert configurations.compile.incoming.getFiles(otherAttribute: 'anything').collect { it.name } == ['lib-util.jar', 'lib.jar', 'some-jar-1.0.jar']
                    }
                }
            }
        """

        m1.ivy.expectGet()
        m1.getArtifact(name: 'some-jar', type: 'jar').expectGet()

        expect:
        succeeds "resolve"
        // Currently builds all file dependencies
        result.assertTasksExecuted(":lib:jar", ":lib:utilClasses", ":lib:utilDir", ":lib:utilJar", ":app:resolve")
    }

    // Documents existing matching behaviour, not desired behaviour
    def "can create a view that selects different artifacts from the same dependency graph"() {
        given:
        def m1 = ivyHttpRepo.module('org', 'test', '1.0')
                    .artifact(name: 'some-jar', type: 'jar')
                    .artifact(name: 'some-classes', type: 'classes')
                    .artifact(name: 'some-lib', type: 'lib')
                    .publish()

        buildFile << """
            allprojects {
                repositories {
                    ivy { url '${ivyHttpRepo.uri}' }
                }
            }
            project(':lib') {
                dependencies {
                    compile utilJar.outputs.files
                    compile utilClasses.outputs.files
                    compile utilDir.outputs.files
                    compile 'org:test:1.0'
                }
                artifacts {
                    compile file: file('lib.jar'), builtBy: jar
                    compile file: file('lib.classes'), builtBy: classes
                    compile file: file('lib'), builtBy: dir
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
                    def files = configurations.compile.incoming.getFiles(artifactType: 'classes')
                    inputs.files files
                    doLast {
                        assert files.collect { it.name } == ['lib-util.classes', 'lib.classes', 'some-classes-1.0.classes']
                    }
                }
            }
        """

        m1.ivy.expectGet()
        m1.getArtifact(name: 'some-classes', type: 'classes').expectGet()

        expect:
        succeeds "resolve"
        // Currently builds all file dependencies
        result.assertTasksExecuted(":lib:classes", ":lib:utilClasses", ":lib:utilDir", ":lib:utilJar", ":app:resolve")
    }

    def "can create a view for configuration that has no attributes"() {
        given:
        buildFile << """
            project(':lib') {
                artifacts {
                    compile file: file('lib.jar'), builtBy: jar
                    compile file: file('lib.classes'), builtBy: classes
                    compile file: file('lib'), builtBy: dir
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
        // Currently builds all file dependencies
        result.assertTasksExecuted(":lib:classes", ":app:resolve")
    }
}
