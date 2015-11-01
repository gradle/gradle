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
import org.gradle.util.TextUtil

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

    def "model report should display empty component sources"() {
        when:
        succeeds "model"

        then:
        ModelReportOutput.from(output).hasNodeStructure {
            components {
                main {
                    binaries()
                    sources()
                }
            }
        }
    }

    def "model report should display each configured component source set"() {
        buildFile << """
            model {
                components {
                    main {
                        sources {
                            someLang(CustomLanguageSourceSet)
                            someOther(CustomLanguageSourceSet)
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
                main {
                    binaries()
                    sources {
                        someLang(type: "CustomLanguageSourceSet", nodeValue: "DefaultCustomLanguageSourceSet 'main:someLang'")
                        someOther(type: "CustomLanguageSourceSet", nodeValue: "DefaultCustomLanguageSourceSet 'main:someOther'")
                    }
                }
            }
        }
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
        output.contains TextUtil.toPlatformLineSeparators('''configured components.main.sources
before ss1
created ss1
configured ss1
after ss1
''')
    }
}
