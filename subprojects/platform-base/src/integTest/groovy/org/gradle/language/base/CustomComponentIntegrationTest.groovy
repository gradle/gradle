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

class CustomComponentIntegrationTest extends AbstractIntegrationSpec {
    // TODO:LPTR Add test for extending ComponentSpec that doesn't have a default implementation
    def "can declare custom managed component"() {
        buildFile << """
            apply plugin: "jvm-component"

            @Managed
            interface SampleLibrarySpec extends JvmLibrarySpec {
                String getPublicData()
                void setPublicData(String publicData)
            }

            class RegisterComponentRules extends RuleSource {
                @ComponentType
                void register(ComponentTypeBuilder<SampleLibrarySpec> builder) {
                }
            }
            apply plugin: RegisterComponentRules

            model {
                components {
                    jar(JvmLibrarySpec) {}
                    sampleLib(SampleLibrarySpec) {}
                }
            }

            class ValidateTaskRules extends RuleSource {
                @Mutate
                void createValidateTask(ModelMap<Task> tasks, ComponentSpecContainer components) {
                    tasks.create("validate") {
                        assert components*.name == ["jar", "sampleLib"]
                        assert components.withType(ComponentSpec)*.name == ["jar", "sampleLib"]
                        assert components.withType(JvmLibrarySpec)*.name == ["jar", "sampleLib"]
                        assert components.withType(SampleLibrarySpec)*.name == ["sampleLib"]
                    }
                }
            }
            apply plugin: ValidateTaskRules
        """

        expect:
        succeeds "validate"
    }
}
