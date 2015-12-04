/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.model

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.language.base.FunctionalSourceSet
import org.gradle.platform.base.ComponentSpec

class ModelMapIntegrationTest extends AbstractIntegrationSpec {
    def "cannot add unregistered type to specialized model map"() {
        buildFile << """
        @Managed interface SampleComponent extends ComponentSpec {}
        interface NonRegisteredComponent extends ComponentSpec {}

        class Rules extends RuleSource {
            @ComponentType
            void registerComponent(ComponentTypeBuilder<SampleComponent> builder) {}
        }
        apply plugin: Rules

        model {
            components {
                main(NonRegisteredComponent) {}
            }
        }
        """

        expect:
        fails "components"
        failure.assertHasCause("Cannot create a 'NonRegisteredComponent' because this type is not known to components. Known types are: SampleComponent")
    }

    def "cannot add invalid type to specialized model map"() {
        buildFile << """
        apply plugin: ComponentModelBasePlugin

        model {
            components {
                main(FunctionalSourceSet) {}
            }
        }
        """

        expect:
        fails "components"
        failure.assertHasCause("$FunctionalSourceSet.name is not a subtype of $ComponentSpec.name")
    }

    def "withType() returns empty collection for type not implementing ModelMap's base interface"() {
        buildFile << """
        apply plugin: ComponentModelBasePlugin

        class Rules extends RuleSource {
            @Mutate
            void addTask(ModelMap<Task> tasks, ComponentSpecContainer components) {
                tasks.create("validate") {
                    doLast {
                        assert components.withType(FunctionalSourceSet).isEmpty()
                    }
                }
            }
        }
        apply plugin: Rules
        """

        expect:
        succeeds "validate"
    }
}
