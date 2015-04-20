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

package org.gradle.language.base

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.EnableModelDsl
import org.gradle.util.TextUtil
import spock.lang.Ignore

class ComponentModelIntegrationTest extends AbstractIntegrationSpec {

    def "setup"() {
        EnableModelDsl.enable(executer)

        buildScript """
            interface CustomComponent extends ComponentSpec {}
            class DefaultCustomComponent extends BaseComponentSpec implements CustomComponent {}

            class ComponentTypeRules extends RuleSource {
                @ComponentType
                void registerCustomComponentType(ComponentTypeBuilder<CustomComponent> builder) {
                    builder.defaultImplementation(DefaultCustomComponent)
                }
            }

            apply type: ComponentTypeRules

            model {
                components {
                    main(CustomComponent)
                }
            }
        """
    }

    void withMainSourceSet() {
        buildFile << """
            interface CustomLanguageSourceSet extends LanguageSourceSet {
                String getData();
            }
            class DefaultCustomLanguageSourceSet extends BaseLanguageSourceSet implements CustomLanguageSourceSet {
                final String data = "foo"
            }

            class LanguageTypeRules extends RuleSource {
                @LanguageType
                void registerCustomLanguage(LanguageTypeBuilder<CustomLanguageSourceSet> builder) {
                    builder.setLanguageName("custom")
                    builder.defaultImplementation(DefaultCustomLanguageSourceSet)
                }
            }

            apply type: LanguageTypeRules

            model {
                components {
                    main {
                        sources {
                            main(CustomLanguageSourceSet)
                        }
                    }
                }
            }
        """
    }

    def "component source container is visible in model report"() {
        when:
        succeeds "model"

        then:
        output.contains(TextUtil.toPlatformLineSeparators("""
    components
        main
            source"""))
    }

    def "can reference source container for a component in a rule"() {
        given:
        withMainSourceSet()
        buildFile << '''
            model {
                tasks {
                    create("printSourceNames") {
                        def source = $("components.main.source")
                        doLast {
                            println "names: ${source*.name}"
                        }
                    }
                }
            }
        '''

        when:
        succeeds "printSourceNames"

        then:
        output.contains "names: [main]"
    }

    def "component source container elements are visible in model report"() {
        given:
        withMainSourceSet()
        buildFile << """
            model {
                components {
                    main {
                        sources {
                            test(CustomLanguageSourceSet)
                        }
                    }
                    test(CustomComponent) {
                        sources {
                            test(CustomLanguageSourceSet)
                        }
                    }
                    foo(CustomComponent) {
                        sources {
                            bar(CustomLanguageSourceSet)
                        }
                    }
                }
            }
        """

        when:
        succeeds "model"

        then:
        output.contains(TextUtil.toPlatformLineSeparators("""
    components
        foo
            source
                bar
        main
            source
                main
                test
        test
            source
                test"""))
    }

    def "can reference source container elements in a rule"() {
        given:
        withMainSourceSet()
        buildFile << '''
            model {
                tasks {
                    create("printSourceDisplayName") {
                        def source = $("components.main.source.main")
                        doLast {
                            println "source display name: ${source.displayName}"
                        }
                    }
                }
            }
        '''

        when:
        succeeds "printSourceDisplayName"

        then:
        output.contains "source display name: DefaultCustomLanguageSourceSet 'main:main'"
    }

    def "can reference source container elements using specialized type in a rule"() {
        given:
        withMainSourceSet()
        buildFile << '''
            import org.gradle.model.collection.*

            class TaskRules extends RuleSource {
                @Mutate
                void addPrintSourceDisplayNameTask(CollectionBuilder<Task> tasks, @Path("components.main.source.main") CustomLanguageSourceSet sourceSet) {
                    tasks.create("printSourceData") {
                        doLast {
                            println "source data: ${sourceSet.data}"
                        }
                    }
                }
            }

            apply type: TaskRules
        '''

        when:
        succeeds "printSourceData"

        then:
        output.contains "source data: foo"
    }

    def "cannot remove source sets"() {
        given:
        withMainSourceSet()
        buildFile << '''
            import org.gradle.model.collection.*

            class SourceSetRemovalRules extends RuleSource {
                @Mutate
                void clearSourceSets(@Path("components.main.source") DomainObjectSet<LanguageSourceSet> sourceSets) {
                    sourceSets.clear()
                }

                @Mutate
                void closeMainComponentSourceSetsForTasks(CollectionBuilder<Task> tasks, @Path("components.main.source") DomainObjectSet<LanguageSourceSet> sourceSets) {
                }
            }

            apply type: SourceSetRemovalRules
        '''

        when:
        fails()

        then:
        failureHasCause("This collection does not support element removal.")
    }



    def "componentSpecContainer is groovy decorated when used in rules"() {
        given:
        withMainSourceSet()
        buildFile << '''
            import org.gradle.model.collection.*

            class ComponentSpecContainerRules extends RuleSource {
                @Mutate
                void addComponents(ComponentSpecContainer componentSpecs) {
                    componentSpecs.anotherCustom(CustomComponent) {
                    }
                }

                @Mutate
                void addComponentTasks(TaskContainer tasks, ComponentSpecContainer componentSpecs) {
                    tasks.create("printMainComponent") {
                        doLast{
                            //reference by name
                            println "Main component: " + componentSpecs.main.name
                        }

                    }
                }
            }

            apply type: ComponentSpecContainerRules
        '''

        when:
        succeeds "printMainComponent"
        then:
        output.contains("Main component: main")
    }

    // this exposes a problem with the CollectionBuilder view
    // as they don't noticed when get closed
    @Ignore
    def "CollectionBuilder<ComponentSpec> is closed when used as input"() {
        given:
        withMainSourceSet()
        buildFile << '''
            import org.gradle.model.collection.*

            class ComponentSpecContainerRules extends RuleSource {

                @Mutate
                void addComponentTasks(TaskContainer tasks, CollectionBuilder<ComponentSpec> componentSpecs) {
                    componentSpecs.all {
                        // some stuff here
                    }
                }
            }

            apply type: ComponentSpecContainerRules
        '''

        when:
        fails "tasks"
    }
}