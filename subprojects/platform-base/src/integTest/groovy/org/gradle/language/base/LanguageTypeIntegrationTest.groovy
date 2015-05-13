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

package org.gradle.language.base

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.EnableModelDsl

import static org.gradle.util.TextUtil.toPlatformLineSeparators

class LanguageTypeIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        buildFile << """
        interface CustomLanguageSourceSet extends LanguageSourceSet {}
        class DefaultCustomLanguageSourceSet extends BaseLanguageSourceSet implements CustomLanguageSourceSet {}

        class CustomLanguagePlugin extends RuleSource {
            @LanguageType
            void declareCustomLanguage(LanguageTypeBuilder<CustomLanguageSourceSet> builder) {
                builder.setLanguageName("custom")
                builder.defaultImplementation(DefaultCustomLanguageSourceSet)
            }
        }

        apply plugin:CustomLanguagePlugin
"""
    }

    def "registers language in languageRegistry"(){
        given:
        EnableModelDsl.enable(executer)
        buildFile << """
model {
    tasks {
        create("printLanguages") {
            it.doLast {
                 def languages = \$("languages")*.name.sort().join(", ")
                 println "registered languages: \$languages"
            }
        }
    }
}
        """
        when:
        succeeds "printLanguages"
        then:
        output.contains("registered languages: custom")
    }

    def "can add custom language sourceSet to component"() {
        when:
        buildFile << """
        interface SampleComponent extends ComponentSpec {}
        class DefaultSampleComponent extends BaseComponentSpec implements SampleComponent {}


        class CustomComponentPlugin extends RuleSource {
            @ComponentType
            void register(ComponentTypeBuilder<SampleComponent> builder) {
                builder.defaultImplementation(DefaultSampleComponent)
            }

            @Mutate
            void createSampleComponentComponents(ModelMap<SampleComponent> componentSpecs) {
                componentSpecs.create("main")
            }
        }

        apply plugin:CustomComponentPlugin

        model {
            components {
                main {
                    sources {
                        custom(CustomLanguageSourceSet)
                    }
                }
            }
        }

"""
        then:
        succeeds "components"
        and:
        output.contains(toPlatformLineSeparators("""
DefaultSampleComponent 'main'
-----------------------------

Source sets
    DefaultCustomLanguageSourceSet 'main:custom'
        srcDir: src${File.separator}main${File.separator}custom
"""))
    }

}
