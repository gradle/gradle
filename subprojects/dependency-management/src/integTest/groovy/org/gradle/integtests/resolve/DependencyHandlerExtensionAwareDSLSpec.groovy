/*
 * Copyright 2018 the original author or authors.
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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache

class DependencyHandlerExtensionAwareDSLSpec extends AbstractIntegrationSpec {
    @ToBeFixedForConfigurationCache(because = "task uses DependencyHandler API")
    def "can type-safely use DependencyHandler ExtensionAware with the Groovy DSL"() {
        buildFile << """
            dependencies {
                extensions["theAnswer"] = {
                    42
                }
            }
            tasks.register("assertValue") {
                doLast {
                    assert dependencies.extensions["theAnswer"]() == 42
                }
            }
        """
        expect:
        succeeds("assertValue")
    }

    @ToBeFixedForConfigurationCache(because = "Task.getProject() during execution")
    def "can type-safely use DependencyHandler ExtensionAware with the Kotlin DSL"() {
        buildKotlinFile << """
            dependencies {
                val theAnswer: () -> Int by extra {
                    { 42 }
                }
            }

            tasks {
                register("assertValue") {
                    doLast {
                        val theAnswer: () -> Int by project.dependencies.extra
                        assert(theAnswer() == 42)
                    }
                }
            }
        """
        expect:
        succeeds("assertValue")
    }
}
