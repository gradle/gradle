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
import org.gradle.integtests.fixtures.EnableModelDsl
import org.gradle.integtests.fixtures.daemon.DaemonIntegrationSpec
import org.gradle.model.internal.persist.ReusingModelRegistryStore

// Requires daemon because reuse right now doesn't handle the build actually changing
class ModelReuseIntegrationTest extends DaemonIntegrationSpec {

    def setup() {
        EnableModelDsl.enable(executer)

        executer.beforeExecute {
            withArgument("-D$ReusingModelRegistryStore.TOGGLE=true")
        }
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

    def "can enable reuse with the variants benchmark"() {
        when:
        buildScript """
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
                void flavours(ModelSet<Flavour> flavours) {
                }

                @Model
                void types(ModelSet<Type> types) {
                }

                @Model
                void variants(ModelSet<Variant> variants, ModelSet<Flavour> flavours, ModelSet<Type> types) {
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
                void addVariantTasks(ModelMap<Task> tasks, ModelSet<Variant> variants) {
                    variants.each {
                        tasks.create(it.name)
                    }
                }

                @Mutate
                void addAllVariantsTasks(ModelMap<Task> tasks, ModelSet<Variant> variants) {
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
