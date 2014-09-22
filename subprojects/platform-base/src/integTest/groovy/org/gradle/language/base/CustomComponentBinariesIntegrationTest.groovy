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

import static org.gradle.util.TextUtil.toPlatformLineSeparators

class CustomComponentBinariesIntegrationTest extends AbstractIntegrationSpec{

    def "setup"() {
        buildFile << """
import org.gradle.model.*
import org.gradle.model.collection.*

interface SampleBinary extends BinarySpec {}
class DefaultSampleBinary extends BaseBinarySpec implements SampleBinary {}
interface SampleLibrary extends ComponentSpec<SampleBinary> {}
class DefaultSampleLibrary extends BaseComponentSpec implements SampleLibrary {}

"""
    }

    def "can register binaries using @ComponentBinaries"() {
        when:
        buildFile << myBinaryDeclarationModel()
        buildFile << """

        apply plugin:MyBinaryDeclarationModel

        task checkModel << {
            assert project.binaries.size() == 1
            def sampleBinary = project.binaries.sampleLibBinary
            assert sampleBinary instanceof SampleBinary
            assert sampleBinary.displayName == "DefaultSampleBinary: 'sampleLibBinary'"
        }
"""
        then:
        succeeds "checkModel"
    }

    def "links binaries to component"() {
        given:
        buildFile << myBinaryDeclarationModel()
        buildFile << """

        apply plugin:MyBinaryDeclarationModel
"""
        when:
        succeeds "components"
        then:
        output.contains(toPlatformLineSeparators(
"""DefaultSampleLibrary 'sampleLib'
--------------------------------

Source sets
    No source sets.

Binaries
    DefaultSampleBinary: 'sampleLibBinary'
        build using task: :sampleLibBinary

"""))
    }

    def "creates lifecycle task for binary"() {
        given:
        buildFile << myBinaryDeclarationModel()
        buildFile << """

        apply plugin:MyBinaryDeclarationModel
"""
        when:
        succeeds "sampleLibBinary"
        then:
        output.contains(":sampleLibBinary UP-TO-DATE")
    }


    String myBinaryDeclarationModel() {
"""
        class MyBinaryDeclarationModel implements Plugin<Project> {
            void apply(final Project project) {}

            @RuleSource
            static class ComponentModel {
                @ComponentType
                void register(ComponentTypeBuilder<SampleLibrary> builder) {
                    builder.defaultImplementation(DefaultSampleLibrary)
                }
                @Mutate
                void createSampleComponentComponents(CollectionBuilder<SampleLibrary> componentSpecs) {
                    componentSpecs.create("sampleLib")
                }

                @BinaryType
                void register(BinaryTypeBuilder<SampleBinary> builder) {
                    builder.defaultImplementation(DefaultSampleBinary)
                }

                @ComponentBinaries
                void createBinariesForSampleLibrary(CollectionBuilder<SampleBinary> binaries, SampleLibrary library) {
                    binaries.create("\${library.name}Binary", SampleBinary)
                    //binaries.create("\${library.name}OtherBinary", OtherSampleBinary)
                }
            }
        }
"""
    }
}
