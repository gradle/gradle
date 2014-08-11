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
import org.gradle.util.TextUtil

class CustomBinaryIntegrationTest extends AbstractIntegrationSpec {
    def "setup"() {
        buildFile << """
import org.gradle.model.*
import org.gradle.model.collection.*

interface SampleBinary extends BinarySpec {}
class DefaultSampleBinary extends DefaultBinarySpec implements SampleBinary {}
"""
    }

    def "custom binary type can be registered and created"() {
        when:
        buildWithCustomBinaryPlugin()
        and:
        buildFile << """
task checkModel << {
    assert project.binaries.size() == 1
    def sampleBinary = project.binaries.sampleBinary
    assert sampleBinary instanceof SampleBinary
    assert sampleBinary.displayName == "DefaultSampleBinary:sampleBinary"
}
"""
        then:
        succeeds "checkModel"
    }


    def "can register custom binary model without creating"() {
        when:
        buildFile << """
        class MySamplePlugin implements Plugin<Project> {
            void apply(final Project project) {}

            @RuleSource
            static class Rules {
                @BinaryType
                void register(BinaryTypeBuilder<SampleBinary> builder) {
                    builder.setDefaultImplementation(DefaultSampleBinary)
                }
            }
        }

        apply plugin:MySamplePlugin

        task checkModel << {
            assert project.binaries.size() == 0
        }
"""

        then:
        succeeds "checkModel"
    }


    def "additional binaries listed in components report"() {
        given:
        buildWithCustomBinaryPlugin()
        when:
        succeeds "components"
        then:
        output.contains(TextUtil.toPlatformLineSeparators(""":components

------------------------------------------------------------
Root project
------------------------------------------------------------

No components defined for this project.

Additional binaries
-------------------
DefaultSampleBinary:sampleBinary
    build using task: :sampleBinary

Note: currently not all plugins register their components, so some components may not be visible here.

BUILD SUCCESSFUL"""))
    }


    def buildWithCustomBinaryPlugin() {
        settingsFile << """rootProject.name = 'custom-binary'"""
        buildFile << """
        class MySamplePlugin implements Plugin<Project> {
            void apply(final Project project) {}

            @RuleSource
            static class Rules {
                @BinaryType
                void register(BinaryTypeBuilder<SampleBinary> builder) {
                    println "registering sample binary"
                    builder.setDefaultImplementation(DefaultSampleBinary)
                }

                @Mutate
                void createSampleBinary(NamedItemCollectionBuilder<SampleBinary> binarySpecs) {
                    println "creating binary"
                    binarySpecs.create("sampleBinary")
                }
            }
        }

        apply plugin:MySamplePlugin
        """
    }

}