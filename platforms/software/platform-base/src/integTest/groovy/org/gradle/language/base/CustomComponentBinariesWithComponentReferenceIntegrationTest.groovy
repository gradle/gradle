/*
 * Copyright 2016 the original author or authors.
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
import org.gradle.integtests.fixtures.StableConfigurationCacheDeprecations
import org.gradle.integtests.fixtures.UnsupportedWithConfigurationCache
import spock.lang.Issue

@UnsupportedWithConfigurationCache(because = "software model")
class CustomComponentBinariesWithComponentReferenceIntegrationTest extends AbstractIntegrationSpec implements StableConfigurationCacheDeprecations {

    @Issue("https://issues.gradle.org/browse/GRADLE-3422")
    def "@ComponentBinaries rule is not applied to component reference field of managed binary"() {
        buildFile << """
            @Managed interface SampleLibrary extends GeneralComponentSpec {}
            @Managed interface SampleBinary extends BinarySpec {
                SampleLibrary getParent()
                void setParent(SampleLibrary parent)
            }

            class Rules extends RuleSource {
                @ComponentType
                void registerLibrary(TypeBuilder<SampleLibrary> builder) {}

                @ComponentType
                void registerBinary(TypeBuilder<SampleBinary> builder) {}

                @ComponentBinaries
                void generateBinaries(ModelMap<SampleBinary> binaries, SampleLibrary library) {
                    binaries.create("bin", SampleBinary) { binary ->
                        binary.parent = library
                    }
                }
            }

            apply plugin: Rules

            model {
                components {
                    myComp(SampleLibrary)
                }
            }
        """

        expect:
        expectTaskGetProjectDeprecations()
        succeeds "components"
    }
}
