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

import org.gradle.api.reporting.model.ModelReportOutput
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.language.base.FunctionalSourceSet
import org.gradle.platform.base.ComponentSpec
import spock.lang.Issue

class ModelMapIntegrationTest extends AbstractIntegrationSpec {
    def "cannot add unregistered type to specialized model map"() {
        buildFile << """
        @Managed interface SampleComponent extends ComponentSpec {}
        interface NonRegisteredComponent extends ComponentSpec {}

        class Rules extends RuleSource {
            @ComponentType
            void registerComponent(TypeBuilder<SampleComponent> builder) {}
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

    def "cannot add unregistered type to model map"() {
        buildFile << """
            @Managed interface Thing {}

            class Rules extends RuleSource {
                @Model void things(ModelMap<Thing> things) { }
            }
            apply plugin: Rules

            model {
                things {
                    bad(FunctionalSourceSet)
                }
            }
        """

        expect:
        fails "model"
        failureHasCause "Cannot create 'things.bad' with type '$FunctionalSourceSet.name' as this is not a subtype of 'Thing'."
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
        failure.assertHasCause("Cannot create 'components.main' with type '$FunctionalSourceSet.name' as this is not a subtype of '$ComponentSpec.name'.")
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


    def "can create a ModelMap of List<String>"() {
        buildFile << """
            class Rules extends RuleSource {
                @Model void things(ModelMap<List<String>> things) { }
            }
            apply plugin: Rules

            model {
                things { it.create("elem") }
            }
        """

        expect:
        succeeds "model"
        ModelReportOutput.from(output).hasNodeStructure {
            things {
                elem(type: 'java.util.List<java.lang.String>', creator: 'things { ... } @ build.gradle line 8, column 17 > create(elem)')
            }
        }
    }

    @Issue("https://issues.gradle.org/browse/GRADLE-3376")
    def "reasonable error message when trying to create unknown type in ModelMap"() {
        buildFile << """
            @Managed interface Thing {}

            class Rules extends RuleSource {
                @Model
                void things(ModelMap<Thing> things) {}
            }
            apply plugin: Rules

            model {
                things {
                    thing(UnknownThing)
                }
            }
        """

        expect:
        fails "model"
        failureHasCause "Attempt to read property 'UnknownThing' from a write only view of model element 'things' given to rule 'things { ... } @ build.gradle line 11, column 17"
    }
}
