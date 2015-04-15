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

class ComponentModelIntegrationTest extends AbstractIntegrationSpec {

    def "setup"() {
        EnableModelDsl.enable(executer)

        buildScript """
            interface CustomComponent extends ComponentSpec {}
            class DefaultCustomComponent extends BaseComponentSpec implements CustomComponent {}

            interface CustomLanguageSourceSet extends LanguageSourceSet {}
            class DefaultCustomLanguageSourceSet extends BaseLanguageSourceSet implements CustomLanguageSourceSet {}

            class Rules extends RuleSource {
                @ComponentType
                void registerCustomComponentType(ComponentTypeBuilder<CustomComponent> builder) {
                    builder.defaultImplementation(DefaultCustomComponent)
                }

                @LanguageType
                void registerCustomLanguage(LanguageTypeBuilder<CustomLanguageSourceSet> builder) {
                    builder.setLanguageName("custom")
                    builder.defaultImplementation(DefaultCustomLanguageSourceSet)
                }
            }

            apply type: Rules

            model {
                components {
                    main(CustomComponent) {
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
        output.contains """\
    components
        main
            source"""
    }

    def "can reference source container for a component in a rule"() {
        given:
        buildFile << '''
            model {
                tasks {
                    create("printLanguageName") {
                        def source = $("components.main.source")
                        doLast {
                            println "names: ${source*.name}"
                        }
                    }
                }
            }
        '''

        when:
        succeeds "printLanguageName"

        then:
        output.contains "names: [main]"
    }
}