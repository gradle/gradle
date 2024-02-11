/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.api

abstract class AbstractMutatingDomainObjectContainerInHookIntegrationTest extends AbstractDomainObjectContainerIntegrationTest {
    def "can mutate containers inside Project hooks"() {
        createDirs("nested")
        settingsFile << """
            include 'nested'
        """
        buildFile << """
            project(':nested').afterEvaluate {
                testContainer.create("afterEvaluate")
            }

            project(':nested').beforeEvaluate {
                testContainer.create("beforeEvaluate")
            }

            task verify {
                doLast {
                    assert testContainer.findByName("afterEvaluate") != null
                    assert testContainer.findByName("beforeEvaluate") != null
                }
            }
        """

        expect:
        succeeds "verify"
    }

    def "can mutate containers inside Gradle hooks"() {
        createDirs("nested")
        settingsFile << """
            include 'nested'
            gradle.projectsLoaded {
                it.rootProject {
                    testContainer.create("projectsLoaded")
                }
            }
        """
        buildFile << """
            gradle.beforeProject {
                if (it.name == 'nested') {
                    testContainer.create("beforeProject")
                }
            }

            gradle.afterProject {
                if (it.name == 'nested') {
                    testContainer.create("afterProject")
                }
            }

            gradle.projectsEvaluated {
                testContainer.create("projectsEvaluated")
            }

            task verify {
                doLast {
                    assert testContainer.findByName("beforeProject") != null
                    assert testContainer.findByName("afterProject") != null
                    assert testContainer.findByName("projectsLoaded") != null
                    assert testContainer.findByName("projectsEvaluated") != null
                }
            }
        """

        expect:
        succeeds "verify"
    }

    def "can mutate other containers"() {
        buildFile << """
            class SomeOtherType implements Named {
                final String name
                SomeOtherType(String name) {
                    this.name = name
                }
            }

            def otherContainer = project.container(SomeOtherType)
            testContainer.configureEach {
                if (it.name != "verify") {
                    otherContainer.create(it.name)
                }
            }
            toBeRealized.get()

            task verify {
                doLast {
                    assert otherContainer.findByName("realized") != null
                    assert otherContainer.findByName("toBeRealized") != null
                }
            }
        """

        expect:
        succeeds "verify"
    }
}

class MutatingNamedDomainObjectContainerInHookIntegrationTest extends AbstractMutatingDomainObjectContainerInHookIntegrationTest implements AbstractNamedDomainObjectContainerIntegrationTest {
}

class MutatingTaskContainerInHookIntegrationTest extends AbstractMutatingDomainObjectContainerInHookIntegrationTest implements AbstractTaskContainerIntegrationTest {
}
