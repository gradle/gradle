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
import org.gradle.integtests.fixtures.UnsupportedWithConfigurationCache
import org.gradle.language.base.FunctionalSourceSet
import org.gradle.platform.base.ComponentSpec
import spock.lang.Issue

@UnsupportedWithConfigurationCache(because = "software model")
class ModelMapIntegrationTest extends AbstractIntegrationSpec {
    def "provides basic meta-data for map"() {
        when:
        buildScript '''
            @Managed
            interface Thing {
            }

            class Rules extends RuleSource {
              @Model
              void things(ModelMap<Thing> things) {
              }
            }

            apply type: Rules

            model {
              tasks {
                create("print") {
                  doLast {
                    def things = $.things
                    println "name: $things.name"
                    println "display-name: $things.displayName"
                    println "to-string: ${things.toString()}"
                  }
                }
              }
            }
        '''

        then:
        succeeds "print"

        and:
        output.contains "name: things"
        output.contains "display-name: ModelMap<Thing> 'things'"
        output.contains "to-string: ModelMap<Thing> 'things'"
    }

    def "can view as ModelElement"() {
        when:
        buildScript '''
            @Managed
            interface Thing {
            }

            class Rules extends RuleSource {
              @Model
              void things(ModelMap<Thing> things) {
              }
              @Mutate
              void tasks(ModelMap<Task> tasks, @Path("things") ModelElement things) {
                tasks.create("print") {
                  doLast {
                    println "name: $things.name"
                    println "display-name: $things.displayName"
                    println "to-string: ${things.toString()}"
                  }
                }
              }
            }

            apply type: Rules
        '''

        then:
        succeeds "print"

        and:
        output.contains "name: things"
        output.contains "display-name: ModelMap<Thing> 'things'"
        output.contains "to-string: ModelMap<Thing> 'things'"
    }

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
        failure.assertHasCause("Cannot create an instance of type 'NonRegisteredComponent' as this type is not known. Known types: ${ComponentSpec.name}, SampleComponent.")
    }

    def "cannot add unregistered type to model map of extensible type"() {
        buildFile << """
        @Managed interface SampleComponent extends ComponentSpec {}
        interface NonRegisteredComponent extends ComponentSpec {}

        class Rules extends RuleSource {
            @ComponentType
            void register1(TypeBuilder<SampleComponent> builder) {}
            @Model
            void things(ModelMap<ComponentSpec> m) {}
        }
        apply plugin: Rules

        model {
            things {
                main(NonRegisteredComponent) {}
            }
        }
        """

        expect:
        fails "model"
        failure.assertHasCause("Cannot create an instance of type 'NonRegisteredComponent' as this type is not known. Known types: ${ComponentSpec.name}, SampleComponent.")
    }

    def "cannot add unregistered type to model map of specialized extensible type"() {
        buildFile << """
        @Managed interface SampleComponent extends ComponentSpec {}
        @Managed interface Sample2Component extends ComponentSpec {}
        interface NonRegisteredComponent extends SampleComponent {}

        class Rules extends RuleSource {
            @ComponentType
            void register1(TypeBuilder<SampleComponent> builder) {}
            @ComponentType
            void register2(TypeBuilder<Sample2Component> builder) {}
            @Model
            void things(ModelMap<SampleComponent> m) {}
        }
        apply plugin: Rules

        model {
            things {
                main(NonRegisteredComponent) {}
            }
        }
        """

        expect:
        fails "model"
        failure.assertHasCause("Cannot create an instance of type 'NonRegisteredComponent' as this type is not known. Known types: SampleComponent.")
    }

    def "cannot add unregistered subtype to filtered specialized model map"() {
        buildFile << """
        @Managed interface SampleComponent extends ComponentSpec {}
        @Managed interface Sample2Component extends ComponentSpec {}
        interface NonRegisteredComponent extends SampleComponent {}

        class Rules extends RuleSource {
            @ComponentType
            void registerType1(TypeBuilder<SampleComponent> builder) {}
            @ComponentType
            void registerType2(TypeBuilder<Sample2Component> builder) {}
        }
        apply plugin: Rules

        model {
            components {
                withType(SampleComponent).create("other", NonRegisteredComponent)
            }
        }
        """

        expect:
        fails "components"
        failure.assertHasCause("Cannot create an instance of type 'NonRegisteredComponent' as this type is not known. Known types: SampleComponent.")
    }

    def "cannot add type to filtered specialized model map when it does not satisfied all type constraints"() {
        buildFile << """
        @Managed interface SampleComponent extends ComponentSpec {}
        @Managed interface Sample2Component extends ComponentSpec {}

        class Rules extends RuleSource {
            @ComponentType
            void registerType1(TypeBuilder<SampleComponent> builder) {}
            @ComponentType
            void registerType2(TypeBuilder<Sample2Component> builder) {}
        }
        apply plugin: Rules

        model {
            components {
                withType(Sample2Component).create("other", SampleComponent)
            }
        }
        """

        expect:
        fails "components"
        failure.assertHasCause("Cannot create 'components.other' with type 'SampleComponent' as this is not a subtype of 'Sample2Component'.")
    }

    def "can add type to filtered specialized model map when it satisfies all type constraints"() {
        buildFile << """
        @Managed interface SampleComponent extends ComponentSpec {}
        interface Thing { }
        @Managed interface Sample2Component extends SampleComponent, Thing {}

        class Rules extends RuleSource {
            @ComponentType
            void registerType1(TypeBuilder<SampleComponent> builder) {}
            @ComponentType
            void registerType2(TypeBuilder<Sample2Component> builder) {}
        }
        apply plugin: Rules

        model {
            components {
                withType(SampleComponent).withType(Thing).create("other", Sample2Component)
            }
        }
        """

        expect:
        succeeds "components"
    }

    def "cannot add invalid type to model map"() {
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
            interface UnknownThing {}

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
        failureHasCause "Exception thrown while executing model rule: things { ... } @ build.gradle line 12, column 17"
        failureHasCause "Cannot create 'things.thing' with type 'UnknownThing' as this is not a subtype of 'Thing'."
    }
}
