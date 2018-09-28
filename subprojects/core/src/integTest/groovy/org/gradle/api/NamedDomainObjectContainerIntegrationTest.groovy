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

package org.gradle.api

class NamedDomainObjectContainerIntegrationTest extends AbstractDomainObjectContainerIntegrationTest {
    @Override
    String getContainerStringRepresentation() {
        return "SomeType container"
    }

    @Override
    String makeContainer() {
        return "project.container(SomeType)"
    }

    static String getContainerType() {
        return "NamedDomainObjectContainer"
    }

    def setup() {
        settingsFile << """
            class SomeType implements Named {
                final String name

                SomeType(String name) {
                    this.name = name
                }
            } 
        """
    }

    def "can mutate the task container from named container"() {
        buildFile << """
            testContainer.configureEach {
                tasks.create(it.name)
            }
            toBeRealized.get()

            task verify {
                doLast {
                    assert tasks.findByName("realized") != null
                    assert tasks.findByName("toBeRealized") != null
                }
            }
        """

        expect:
        succeeds "verify"
    }
}
