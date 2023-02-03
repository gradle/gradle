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

import org.gradle.api.reporting.model.ModelReportOutput
import org.gradle.integtests.fixtures.UnsupportedWithConfigurationCache

@UnsupportedWithConfigurationCache(because = "software model")
class ComponentSourcesIntegrationTest extends AbstractComponentModelIntegrationTest {

    def "setup"() {
        withCustomComponentType()
        withCustomLanguageType()
        buildFile << """
            model {
                components {
                    main(CustomComponent)
                }
            }
        """
    }

    void withMainSourceSet() {
        buildFile << """
            model {
                components {
                    main {
                        sources {
                            someLang(CustomLanguageSourceSet)
                        }
                    }
                }
            }
        """
    }

    def "can reference sources container for a component in a rule"() {
        given:
        withMainSourceSet()
        buildFile << '''
            model {
                tasks {
                    create("printSourceNames") {
                        def sources = $.components.main.sources
                        doLast {
                            println "names: ${sources.values()*.name}"
                        }
                    }
                }
            }
        '''

        when:
        succeeds "printSourceNames"

        then:
        output.contains "names: [someLang]"
    }

    def "elements of component sources container should be visible in model report"() {
        given:
        buildFile << """
            model {
                components {
                    main {
                        sources {
                            someLang(CustomLanguageSourceSet)
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
        ModelReportOutput.from(output).hasNodeStructure {
            components {
                foo {
                    binaries()
                    sources {
                        bar(type: "CustomLanguageSourceSet")
                    }
                }
                main {
                    binaries()
                    sources {
                        someLang(type: "CustomLanguageSourceSet")
                        test(type: "CustomLanguageSourceSet")
                    }
                }
                test {
                    binaries()
                    sources {
                        test(type: "CustomLanguageSourceSet")
                    }
                }
            }
        }
    }

    def "can reference sources container elements in a rule"() {
        given:
        withMainSourceSet()
        buildFile << '''
            model {
                tasks {
                    create("printSourceDisplayName") {
                        def sources = $.components.main.sources.someLang
                        doLast {
                            println "sources display name: ${sources.displayName}"
                        }
                    }
                }
            }
        '''

        when:
        succeeds "printSourceDisplayName"

        then:
        output.contains "sources display name: Custom source 'main:someLang'"
    }

    def "can reference sources container elements using specialized type in a rule"() {
        given:
        withMainSourceSet()
        buildFile << '''
            class TaskRules extends RuleSource {
                @Mutate
                void addPrintSourceDisplayNameTask(ModelMap<Task> tasks, @Path("components.main.sources.someLang") CustomLanguageSourceSet sourceSet) {
                    tasks.create("printSourceData") {
                        doLast {
                            println "sources data: ${sourceSet.data}"
                        }
                    }
                }
            }

            apply type: TaskRules
        '''

        when:
        succeeds "printSourceData"

        then:
        output.contains "sources data: foo"
    }

    def "elements in component.sources should not be created when defined"() {
        when:
        buildFile << """
            model {
                components {
                    main {
                        sources {
                            ss1(CustomLanguageSourceSet) {
                                println "created ss1"
                            }
                            beforeEach {
                                println "before \$it.name"
                            }
                            all {
                                println "configured \$it.name"
                            }
                            afterEach {
                                println "after \$it.name"
                            }
                            println "configured components.main.sources"
                        }
                    }
                }
                tasks {
                    verify(Task)
                }
            }
        """
        then:
        succeeds "verify"
        output.contains '''configured components.main.sources
before ss1
created ss1
configured ss1
after ss1
'''
    }
}
