/*
 * Copyright 2014 the original author or authors.
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
import org.gradle.integtests.fixtures.EnableModelDsl
import org.gradle.integtests.fixtures.executer.DaemonGradleExecuter
import org.gradle.model.internal.persist.ReusingModelRegistryStore

class ModelReuseIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        executer = new DaemonGradleExecuter(distribution, testDirectoryProvider)
        executer.beforeExecute {
            requireIsolatedDaemons()
            withArgument("-D$ReusingModelRegistryStore.TOGGLE=true")
            withDaemonIdleTimeoutSecs(10)
        }
        EnableModelDsl.enable(executer)
    }

    def cleanup() {
        executer.withArgument("--stop").run()
    }

    String hashFor(String prefix) {
        (output =~ /$prefix: (\d+)/)[0][1]
    }

    def "model elements are reused when toggle is enabled and when using daemon"() {
        when:
        buildScript """
            class Rules extends $RuleSource.name {
                @$Model.name
                List<String> vals() {
                  []
                }
            }

            pluginManager.apply Rules

            model {
                tasks {
                    create("show") {
                        doLast {
                            println "vals: " + System.identityHashCode(\$("vals"))
                            println "task: " + System.identityHashCode(it)
                        }
                    }
                }
            }
        """


        then:
        succeeds "show"
        ":show" in executedTasks
        output.contains ReusingModelRegistryStore.BANNER

        and:
        def valHash = hashFor("vals")
        def taskHash = hashFor("task")

        when:
        succeeds "show"

        then:
        valHash == hashFor("vals")
        taskHash != hashFor("task")
    }

    def "can enable reuse with the component model"() {
        when:
        buildScript """
            plugins {
              id "org.gradle.jvm-component"
              id "org.gradle.java-lang"
            }

            model {
                components {
                    create("main", JvmLibrarySpec)
                }
            }
        """

        then:
        succeeds "build"
        succeeds "build"
    }

    def "can enable reuse with the variants benchmark"() {
        when:
        buildScript """
            import org.gradle.model.*
            import org.gradle.model.collection.*

            @Managed
            interface Flavour {
                String getName()
                void setName(String name)
            }

            @Managed
            interface Type {
                String getName()
                void setName(String name)
            }

            @Managed
            abstract class Variant {
                abstract Flavour getFlavour()
                abstract void setFlavour(Flavour flavour)

                abstract Type getType()
                abstract void setType(Type type)

                String getName() {
                    flavour.name + type.name
                }
            }

            class VariantsRuleSource extends RuleSource {
                @Model
                void flavours(ManagedSet<Flavour> flavours) {
                }

                @Model
                void types(ManagedSet<Type> types) {
                }

                @Model
                void variants(ManagedSet<Variant> variants, ManagedSet<Flavour> flavours, ManagedSet<Type> types) {
                    flavours.each { flavour ->
                        types.each { type ->
                            variants.create {
                                it.flavour = flavour
                                it.type = type
                            }
                        }
                    }
                }

                @Mutate
                void addVariantTasks(CollectionBuilder<Task> tasks, ManagedSet<Variant> variants) {
                    variants.each {
                        tasks.create(it.name)
                    }
                }

                @Mutate
                void addAllVariantsTasks(CollectionBuilder<Task> tasks, ManagedSet<Variant> variants) {
                    tasks.create("allVariants") { allVariants ->
                        variants.each {
                            allVariants.dependsOn it.name
                        }
                    }
                }
            }

            apply type: VariantsRuleSource

            model {
                flavours {
                    create {
                        name = "flavour1"
                    }
                    create {
                        name = "flavour2"
                    }
                }
                types {
                    create {
                        name = "type1"
                    }
                    create {
                        name = "type2"
                    }
                }
            }
        """

        then:
        succeeds "allVariants"
        output.contains ReusingModelRegistryStore.BANNER
        succeeds "allVariants"
    }
}
