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

import groovy.test.NotYetImplemented
import org.gradle.api.reporting.model.ModelReportOutput
import org.gradle.integtests.fixtures.StableConfigurationCacheDeprecations
import org.gradle.integtests.fixtures.UnsupportedWithConfigurationCache

@UnsupportedWithConfigurationCache(because = "software model")
class ComponentBinarySourcesIntegrationTest extends AbstractComponentModelIntegrationTest implements StableConfigurationCacheDeprecations {
    def setup() {
        withCustomComponentType()
        withCustomBinaryType()
        withCustomLanguageType()

        buildFile << '''
model {
    components {
        mylib(CustomComponent) {
            binaries {
                main(CustomBinary)
                test(CustomBinary)
            }
        }
    }
}
'''
    }

    def "input source sets of binary is union of component source sets and binary specific source sets"() {
        given:
        buildFile << '''
model {
    components {
        mylib {
            sources {
                comp(CustomLanguageSourceSet)
            }
            binaries.all {
                sources {
                    bin(CustomLanguageSourceSet)
                }
            }
        }
    }
    tasks {
        verify(Task) {
            doLast {
                def comp = $.components.mylib
                def binary = comp.binaries.main
                assert comp.sources.size() == 1
                assert binary.sources.size() == 1
                assert binary.inputs == comp.sources + binary.sources as Set
            }
        }
    }
}
'''

        expect:
        succeeds "verify"
    }

    // TODO Fix this regression in behaviour: Fails because the `binary.sources` modelmap is closed before we have a chance to append another source set
    // Needs a few things
    //   1. Copy each component binary into `binaries` early enough for rules to be applied
    //   2. Make `binaries` container have references to the actual binary nodes, rather than unmanaged nodes
    //   3. Fix ordering of rules within a component and binary
    @NotYetImplemented
    def "source sets can be added to the binaries of a component using a rule attached to the top level binaries container"() {
        given:
        buildFile << '''
model {
    binaries {
        all {
            sources {
                custom(CustomLanguageSourceSet)
            }
        }
    }
    tasks {
        verify(Task) {
            doLast {
                def binaries = $.components.mylib.binaries
                assert binaries.main.sources.size() == 1
                assert binaries.main.sources.first() instanceof CustomLanguageSourceSet
                assert binaries.main.inputs.size() == 1
                assert binaries.main.inputs as Set == binaries.main.sources as Set
            }
        }
    }
}
'''

        expect:
        succeeds "verify"
    }

    def "source sets can be added to the binaries of a component using a rule applied to all components"() {
        given:
        buildFile << '''
model {
    components {
        all {
            binaries {
                all {
                    sources {
                        custom(CustomLanguageSourceSet)
                    }
                }
            }
        }
    }
    tasks {
        verify(Task) {
            doLast {
                def binaries = $.components.mylib.binaries
                assert binaries.main.sources.size() == 1
                assert binaries.main.sources.first() instanceof CustomLanguageSourceSet
                assert binaries.main.inputs.size() == 1
                assert binaries.main.inputs as Set == binaries.main.sources as Set
            }
        }
    }
}
'''

        expect:
        succeeds "verify"
    }

    def "can reference sources container for a binary from a rule"() {
        given:
        buildFile << '''
model {
    components {
        mylib {
            binaries.all {
                sources {
                    someLang(CustomLanguageSourceSet)
                }
            }
        }
    }
    tasks {
        create("printSourceNames") {
            def sources = $.components.mylib.binaries.main.sources
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

    def "elements of binary sources container should be visible in model report"() {
        given:
        buildFile << """
            model {
                components {
                    mylib {
                        binaries {
                            main {
                                sources {
                                    someLang(CustomLanguageSourceSet)
                                }
                            }
                            test {
                                sources {
                                    test(CustomLanguageSourceSet)
                                }
                            }
                            foo(CustomBinary) {
                                sources {
                                    bar(CustomLanguageSourceSet)
                                }
                            }
                        }
                    }
                }
            }
        """
        when:
        expectTaskGetProjectDeprecations()
        succeeds "model"

        then:
        ModelReportOutput.from(output).hasNodeStructure {
            components {
                mylib {
                    binaries {
                        foo {
                            sources {
                                bar(type: "CustomLanguageSourceSet")
                            }
                            tasks()
                        }
                        main {
                            sources {
                                someLang(type: "CustomLanguageSourceSet")
                            }
                            tasks()
                        }
                        test {
                            sources {
                                test(type: "CustomLanguageSourceSet")
                            }
                            tasks()
                        }
                    }
                    sources()
                }
            }
        }
    }

    def "elements of binary sources container can be referenced in a rule"() {
        given:
        buildFile << '''
            model {
                components {
                    mylib {
                        binaries.all {
                            sources {
                                someLang(CustomLanguageSourceSet)
                            }
                        }
                    }
                }
                tasks {
                    create("printSourceDisplayName") {
                        def sources = $.components.mylib.binaries.main.sources.someLang
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
        output.contains "sources display name: Custom source 'mylib:main:someLang'"
    }

    def "elements in binary.sources should not be created when defined"() {
        when:
        buildFile << """
            model {
                components {
                    mylib {
                        binaries {
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
                                    println "configured components.mylib.binaries.main.sources"
                                }
                            }
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
        output.contains '''configured components.mylib.binaries.main.sources
before ss1
created ss1
configured ss1
after ss1
'''
    }

    def "reasonable error message when adding source set with unknown type"() {
        when:
        buildFile << """
interface UnregisteredSourceSetType extends LanguageSourceSet {}
model {
    components {
        mylib {
            binaries {
                main {
                    sources {
                        bad(UnregisteredSourceSetType)
                    }
                }
            }
        }
    }
}
"""
        fails "model"

        then:
        failure.assertHasCause("Cannot create an instance of type 'UnregisteredSourceSetType' as this type is not known. Known types: CustomLanguageSourceSet, ${LanguageSourceSet.name}.")
    }

}
