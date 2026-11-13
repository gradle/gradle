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
import org.gradle.integtests.fixtures.modes.UnsupportedWithConfigurationCache

@UnsupportedWithConfigurationCache(because = "software model")
class LanguageTypeIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        buildFile << """
        @Managed interface CustomLanguageSourceSet extends LanguageSourceSet {}

        class CustomLanguagePlugin extends RuleSource {
            @ComponentType
            void declareCustomLanguage(TypeBuilder<CustomLanguageSourceSet> builder) {
            }
        }

        apply plugin:CustomLanguagePlugin
"""
    }

    def "can add custom language sourceSet to component"() {
        when:
        buildFile << """
        @Managed interface SampleComponent extends SourceComponentSpec {}


        class CustomComponentPlugin extends RuleSource {
            @ComponentType
            void register(TypeBuilder<SampleComponent> builder) {
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
        expectSoftwareModelDeprecations("CustomComponentPlugin", "CustomLanguagePlugin", "org.gradle.language.base.plugins.ComponentModelBasePlugin", "org.gradle.language.base.plugins.LanguageBasePlugin", "org.gradle.platform.base.plugins.BinaryBasePlugin", "org.gradle.platform.base.plugins.ComponentBasePlugin")
        expectModelDslDeprecation()
        succeeds "components"
        and:
        output.contains """
SampleComponent 'main'
----------------------

Source sets
    Custom source 'main:custom'
        srcDir: src${File.separator}main${File.separator}custom
"""
    }


    private void expectSoftwareModelDeprecations(String... pluginNames) {
        for (String name : pluginNames) {
            executer.expectDocumentedDeprecationWarning("The ${name} plugin has been deprecated. This is scheduled to be removed in Gradle 10. Rule-based/software model plugins are no longer supported. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_9.html#deprecated_software_model")
        }
    }

    private void expectModelDslDeprecation() {
        executer.expectDocumentedDeprecationWarning("The model DSL has been deprecated. This is scheduled to be removed in Gradle 10. Rule-based/software model plugins are no longer supported. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_9.html#deprecated_software_model")
    }
}
