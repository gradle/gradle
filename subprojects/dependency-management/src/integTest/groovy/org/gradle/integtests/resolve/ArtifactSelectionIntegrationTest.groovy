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

class ArtifactSelectionIntegrationTest extends AbstractHttpDependencyResolutionTest {
    def setup() {
        settingsFile << """
            rootProject.name = 'root'
            include 'lib'
            include 'app'
        """

        buildFile << """
allprojects {
    configurations {
        compile {
            attributes usage: 'api'
        }
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
                    compile files('lib-util.jar', 'lib-util.classes', 'lib-util')
                    compile 'org:test:1.0'
                }
                artifacts {
                    compile file('lib.jar')
                    compile file('lib.classes')
                    compile file('lib')
                }
            }

            project(':app') {
                configurations {
                    compile {
                        format = 'jar'
                    }
                }

                dependencies {
                    compile project(':lib')
                }

                task resolve {
                    doLast {
                        assert configurations.compile.incoming.artifacts.collect { it.file.name } == ['lib-util.jar', 'lib.jar', 'some-jar-1.0.jar']
                        assert configurations.compile.collect { it.name } == ['lib-util.jar', 'lib.jar', 'some-jar-1.0.jar']
                    }
                }
            }
        """

        m1.ivy.expectGet()
        m1.getArtifact(name: 'some-jar', type: 'jar').expectGet()

        expect:
        succeeds "resolve"
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
                    compile files('lib-util.jar', 'lib-util.classes', 'lib-util')
                    compile 'org:test:1.0'
                }
                artifacts {
                    compile file('lib.jar')
                    compile file('lib.classes')
                    compile file('lib')
                }
            }

            project(':app') {
                configurations {
                    compile {
                        format = 'jar'
                    }
                }

                dependencies {
                    compile project(':lib')
                }

                task resolve {
                    doLast {
                        def result = configurations.compile.incoming.getFiles('classes')
                        assert result.collect { it.name } == ['lib-util.jar', 'lib.jar', 'some-jar-1.0.jar']
                    }
                }
            }
        """

        m1.ivy.expectGet()
        m1.getArtifact(name: 'some-jar', type: 'jar').expectGet()

        expect:
        succeeds "resolve"
    }
}
