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

class ComponentSelectionIntegrationTest extends AbstractDependencyResolutionTest {
    def "selects component from candidates backed by configurations"() {
        mavenRepo.module("test", "test1", "1.0").publish()
        mavenRepo.module("test", "test2", "1.0").publish()

        settingsFile << "include 'a', 'b'"
        buildFile << """
            allprojects {
                repositories { maven { url '$mavenRepo.uri' } }
            }
            project(':a') {
                configurations {
                    api
                    implementation
                }
                dependencies {
                    api 'test:test1:1.0'
                    implementation 'test:test2:1.0'
                }
                task apiJar(type: Jar) {
                    archiveName = 'a-api.jar'
                }
                task runtimeJar(type: Jar) {
                    archiveName = 'a-runtime.jar'
                }
                artifacts {
                    api apiJar
                    implementation runtimeJar
                }
                components {
                    main {
                        fromConfiguration('api', configurations.api) {
                            attributes.attribute('usage', 'for compile')
                        }
                        fromConfiguration('runtime', configurations.implementation) {
                            attributes.attribute('usage', 'for runtime')
                        }
                    }
                }
            }
            project(':b') {
                configurations {
                    compile {
                        attributes usage: 'for compile'
                    }
                    runtime {
                        attributes usage: 'for runtime'
                        extendsFrom compile
                    }
                }
                dependencies {
                    compile project(':a')
                }
                task resolveCompile {
                    inputs.files configurations.compile
                    doLast {
                        assert configurations.compile.collect { it.name } == ['a-api.jar', 'test1-1.0.jar']
                    }
                }
                task resolveRuntime {
                    inputs.files configurations.runtime
                    doLast {
                        assert configurations.runtime.collect { it.name } == ['a-runtime.jar', 'test2-1.0.jar']
                    }
                }
            }
"""

        expect:
        succeeds("resolveCompile")
        result.assertTasksExecuted(":a:apiJar", ":b:resolveCompile")

        succeeds("resolveRuntime")
        result.assertTasksExecuted(":a:runtimeJar", ":b:resolveRuntime")
    }

    def "selects component from nested candidates backed by configurations"() {
        mavenRepo.module("test", "test1", "1.0").publish()
        mavenRepo.module("test", "test2", "1.0").publish()

        settingsFile << "include 'a', 'b'"
        buildFile << """
            allprojects {
                repositories { maven { url '$mavenRepo.uri' } }
            }
            project(':a') {
                configurations {
                    api
                    
                    apiFreeDebug { extendsFrom api }
                    implementationFreeDebug { extendsFrom apiFreeDebug }
                    
                    apiPaidRelease { extendsFrom api }
                    implementationPaidRelease { extendsFrom apiPaidRelease }
                }
                dependencies {
                    api 'test:test1:1.0'
                    apiFreeDebug 'test:unknown:1.0'
                    implementationPaidRelease 'test:test2:1.0'
                }
                task apiJar(type: Jar) {
                    archiveName = 'a-api.jar'
                }
                task debugRuntimeJar(type: Jar) {
                    archiveName = 'a-free-debug-runtime.jar'
                }
                task releaseRuntimeJar(type: Jar) {
                    archiveName = 'a-paid-release-runtime.jar'
                }
                artifacts {
                    api apiJar
                    implementationFreeDebug debugRuntimeJar
                    implementationPaidRelease releaseRuntimeJar
                }
                components {
                    main {
                        composite('freeDebug') {
                            attributes.attribute('buildType', 'debug')
                            attributes.attribute('flavor', 'free')
                            fromConfiguration('api', configurations.apiFreeDebug) {
                                attributes.attribute('usage', 'for compile')
                            }
                            fromConfiguration('runtime', configurations.implementationFreeDebug) {
                                attributes.attribute('usage', 'for runtime')
                            }
                        }
                        composite('paidRelease') {
                            attributes.attribute('buildType', 'paid')
                            attributes.attribute('flavor', 'release')
                            fromConfiguration('api', configurations.apiPaidRelease) {
                                attributes.attribute('usage', 'for compile')
                            }
                            fromConfiguration('runtime', configurations.implementationPaidRelease) {
                                attributes.attribute('usage', 'for runtime')
                            }
                        }
                    }
                }
            }
            project(':b') {
                configurations {
                    compile {
                        attributes usage: 'for compile', buildType: 'paid', flavor: 'release'
                    }
                    runtime {
                        attributes usage: 'for runtime', buildType: 'paid', flavor: 'release'
                        extendsFrom compile
                    }
                }
                dependencies {
                    compile project(':a')
                }
                task resolveCompile {
                    inputs.files configurations.compile
                    doLast {
                        assert configurations.compile.collect { it.name } == ['a-api.jar', 'test1-1.0.jar']
                    }
                }
                task resolveRuntime {
                    inputs.files configurations.runtime
                    doLast {
                        assert configurations.runtime.collect { it.name } == ['a-paid-release-runtime.jar', 'a-api.jar', 'test2-1.0.jar', 'test1-1.0.jar']
                    }
                }
            }
"""

        expect:
        succeeds("resolveCompile")
        result.assertTasksExecuted(":a:apiJar", ":b:resolveCompile")

        succeeds("resolveRuntime")
        result.assertTasksExecuted(":a:apiJar", ":a:releaseRuntimeJar", ":b:resolveRuntime")
    }

    def "selects component from candidates not backed by configurations"() {
        mavenRepo.module("test", "test1", "1.0").publish()
        mavenRepo.module("test", "test2", "1.0").publish()

        settingsFile << "include 'a', 'b'"
        buildFile << """
            allprojects {
                repositories { maven { url '$mavenRepo.uri' } }
            }
            project(':a') {
                task apiJar(type: Jar) {
                    archiveName = 'a-api.jar'
                }
                task runtimeJar(type: Jar) {
                    archiveName = 'a-runtime.jar'
                }
                components {
                    main {
                        child('api') {
                            attributes.attribute('usage', 'for compile')
                            dependency 'test:test1:1.0'
                            artifact apiJar
                        }
                        child('runtime') {
                            attributes.attribute('usage', 'for runtime')
                            dependency 'test:test2:1.0'
                            artifact runtimeJar
                        }
                    }
                }
            }
            project(':b') {
                configurations {
                    compile {
                        attributes usage: 'for compile'
                    }
                    runtime {
                        attributes usage: 'for runtime'
                        extendsFrom compile
                    }
                }
                dependencies {
                    compile project(':a')
                }
                task resolveCompile {
                    inputs.files configurations.compile
                    doLast {
                        assert configurations.compile.collect { it.name } == ['a-api.jar', 'test1-1.0.jar']
                    }
                }
                task resolveRuntime {
                    inputs.files configurations.runtime
                    doLast {
                        assert configurations.runtime.collect { it.name } == ['a-runtime.jar', 'test2-1.0.jar']
                    }
                }
            }
"""

        expect:
        succeeds("resolveCompile")
        result.assertTasksExecuted(":a:apiJar", ":b:resolveCompile")

        succeeds("resolveRuntime")
        result.assertTasksExecuted(":a:runtimeJar", ":b:resolveRuntime")
    }

}
