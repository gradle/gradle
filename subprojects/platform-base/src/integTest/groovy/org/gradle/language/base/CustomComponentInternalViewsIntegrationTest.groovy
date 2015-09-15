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

import groovy.transform.NotYetImplemented
import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class CustomComponentInternalViewsIntegrationTest extends AbstractIntegrationSpec {

    @NotYetImplemented
    def "can filter for custom internal view with ComponentSpecContainer.withType()"() {
        buildFile << """
        apply plugin: "jvm-component"

        interface SampleLibrarySpec extends ComponentSpec {}

        interface SampleLibrarySpecInternal {}

        class DefaultSampleLibrarySpec extends BaseComponentSpec implements SampleLibrarySpec, SampleLibrarySpecInternal {}

        class Rules extends RuleSource {
            @ComponentType
            void register(ComponentTypeBuilder<SampleLibrarySpec> builder) {
                builder.defaultImplementation(DefaultSampleLibrarySpec)
            }

            @Mutate
            void createValidateTask(ModelMap<Task> tasks, ComponentSpecContainer components) {
                tasks.create("validate") {
                    assert components*.name == ["jar", "sampleLib"]
                    assert components.withType(ComponentSpec)*.name == ["jar", "sampleLib"]
                    assert components.withType(JvmLibrarySpec)*.name == ["jar"]
                    assert components.withType(SampleLibrarySpec)*.name == ["sampleLib"]
                    assert components.withType(SampleLibrarySpecInternal)*.name == ["sampleLib"]
                }
            }
        }

        apply plugin: Rules

        model {
            components {
                jar(JvmLibrarySpec) {}
                sampleLib(SampleLibrarySpec) {}
            }
        }
        """
        expect:
        succeeds "validate"
    }
}
