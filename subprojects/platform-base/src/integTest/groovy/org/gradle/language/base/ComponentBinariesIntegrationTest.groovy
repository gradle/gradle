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
        def reportOutput = ModelReportOutput.from(output)
        reportOutput.hasNodeStructure {
            components {
                myLib {
                    binaries {
                        main {
                            tasks()
                            sources()
                        }
                        test {
                            tasks()
                            sources()
                        }
                    }
                    sources()
                }
            }
        }
        reportOutput.hasNodeStructure {
            binaries {
                myLibMain()
                myLibTest()
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
                            sources()
                        }
                        test {
                            tasks()
                            sources()
                        }
                    }
                    sources()
                }
                otherLib {
                    binaries {
                        main {
                            tasks()
                            sources()
                        }
                    }
                    sources()
                }
            }
        }
        reportOutput.hasNodeStructure {
            binaries {
                myLibMain()
                myLibTest()
                otherLibMain()
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
        failure.assertHasCause("Exception thrown while executing model rule: binaries { ... } @ build.gradle line 54, column 5")
        failure.assertHasCause("Cannot create 'binaries.mylibMain' using creation rule 'mylibMain(CustomBinary) { ... } @ build.gradle line 55, column 9' as the rule 'ComponentModelBasePlugin.PluginRules#collectBinaries(BinaryContainer, ComponentSpecContainer) > put()' is already registered to create this model element.")
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
                def binaries = $.components.mylib.binaries
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

    def "binary has check task"() {
        given:
        buildFile << '''
model {
    tasks {
        verify(Task) {
            doLast {
                def binaries = $.components.mylib.binaries
                assert binaries.main.checkTask != null
                assert binaries.test.checkTask != null
            }
        }
    }
}
'''

        expect:
        succeeds "verify"
    }
}
