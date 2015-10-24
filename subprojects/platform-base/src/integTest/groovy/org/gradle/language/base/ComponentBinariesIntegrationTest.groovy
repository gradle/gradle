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

class ComponentBinariesIntegrationTest extends AbstractComponentModelIntegrationTest {
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

    def "binaries of a component are visible in the top level binaries container"() {
        when:
        succeeds "model"

        then:
        ModelReportOutput.from(output).hasNodeStructure {
            binaries {
                myLibMain {
                    tasks()
                }
                myLibTest {
                    tasks()
                }
            }
        }
    }

    def "multiple components may have binary with the same given name"() {
        given:
        buildFile << """
            model {
                components {
                    otherlib(CustomComponent) {
                        binaries {
                            main(CustomBinary)
                        }
                    }
                }
            }
        """

        when:
        succeeds "model"

        then:
        def reportOutput = ModelReportOutput.from(output)
        reportOutput.hasNodeStructure {
            components {
                myLib {
                    binaries {
                        main {
                            tasks()
                        }
                        test {
                            tasks()
                        }
                    }
                    sources()
                }
                otherLib {
                    binaries {
                        main {
                            tasks()
                        }
                    }
                    sources()
                }
            }
        }
        reportOutput.hasNodeStructure {
            binaries {
                myLibMain {
                    tasks()
                }
                myLibTest {
                    tasks()
                }
                otherLibMain {
                    tasks()
                }
            }
        }
    }

    def "fails when component has binary whose qualified name conflicts with another binary"() {
        given:
        buildFile << """
model {
    binaries {
        mylibMain(CustomBinary) {}
    }
}
"""
        when:
        fails "model"

        then:
        failure.assertHasCause("Exception thrown while executing model rule: model.binaries @ build.gradle line 64, column 5")
        failure.assertHasCause("Cannot create 'binaries.mylibMain' using creation rule 'model.binaries @ build.gradle line 64, column 5 > create(mylibMain)' as the rule 'ComponentModelBasePlugin.Rules#collectBinaries > put()' is already registered to create this model element.")
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
                def comp = $('components.mylib')
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

    def "binaries of a component can be configured using a rule attached to the top level binaries container"() {
        given:
        buildFile << '''
model {
    binaries {
        beforeEach {
            data = name
        }
        all {
            data = "[$data]"
        }
        afterEach {
            data = "($data)"
        }
    }
    tasks {
        verify(Task) {
            doLast {
                def binaries = $('components.mylib.binaries')
                assert binaries.main.data == '([main])'
                assert binaries.test.data == '([test])'
            }
        }
    }
}
'''

        expect:
        succeeds "verify"
    }

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
                def binaries = $('components.mylib.binaries')
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
}
