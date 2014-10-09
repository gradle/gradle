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
import spock.lang.Unroll

import static org.gradle.util.TextUtil.toPlatformLineSeparators

class CustomComponentBinariesIntegrationTest extends AbstractIntegrationSpec{

    def "setup"() {
        buildFile << """
import org.gradle.model.*
import org.gradle.model.collection.*

interface SampleBinary extends BinarySpec {}
interface OtherSampleBinary extends SampleBinary {}
class DefaultSampleBinary extends BaseBinarySpec implements SampleBinary {}
class OtherSampleBinaryImpl extends BaseBinarySpec implements OtherSampleBinary {}
interface SampleLibrary extends ComponentSpec {}
class DefaultSampleLibrary extends BaseComponentSpec implements SampleLibrary {}

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

                @BinaryType
                void registerOther(BinaryTypeBuilder<OtherSampleBinary> builder) {
                    builder.defaultImplementation(OtherSampleBinaryImpl)
                }
            }
        }

        apply plugin:MyBinaryDeclarationModel
"""
    }

    def "can register binaries using @ComponentBinaries"() {
        when:
        buildFile << withSimpleComponentBinaries()
        buildFile << """


        task checkModel << {
            assert project.binaries.size() == 2
            def sampleBinary = project.binaries.sampleLibBinary
            def othersSampleBinary = project.binaries.sampleLibOtherBinary
            assert sampleBinary instanceof SampleBinary
            assert sampleBinary.displayName == "DefaultSampleBinary: 'sampleLibBinary'"
            assert othersSampleBinary instanceof OtherSampleBinary
            assert othersSampleBinary.displayName == "OtherSampleBinaryImpl: 'sampleLibOtherBinary'"
        }
"""
        then:
        succeeds "checkModel"
    }

    def "links binaries to component"() {
        given:
        buildFile << withSimpleComponentBinaries()
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
    OtherSampleBinaryImpl: 'sampleLibOtherBinary'
        build using task: :sampleLibOtherBinary
"""))
    }

    @Unroll
    def "can execute #taskdescr to build binary"() {
        given:
        buildFile << withSimpleComponentBinaries()
        when:
        succeeds taskName
        then:
        output.contains(":sampleLibBinary UP-TO-DATE")
        where:
        taskName          | taskdescr
        "sampleLibBinary" | "lifecycle task"
        "assemble"        | "assemble task"
    }

    def "Can access lifecycle task of binary via BinarySpec.buildTask"(){
        when:
        buildFile << withSimpleComponentBinaries()
        buildFile << """

        task tellTaskName << {
            assert project.binaries.sampleLibBinary.buildTask instanceof Task
            assert project.binaries.sampleLibBinary.buildTask.name ==  "sampleLibBinary"
        }
"""
        then:
        succeeds "tellTaskName"
    }

    def "ComponentBinaries rule supports additional parameters as rule inputs"() {
        given:
        buildFile << """
        class CustomModel {
            List<String> values = []
        }

        class MyComponentBinariesPlugin implements Plugin<Project> {
            void apply(final Project project) {}

            @RuleSource
            static class Rules {

               @Model
               CustomModel customModel() {
                   new CustomModel()
               }

               @ComponentBinaries
               void createBinariesForSampleLibrary(CollectionBuilder<SampleBinary> binaries, $ruleInputs) {
                   myModel.values.each{ value ->
                        binaries.create("\${library.name}\${value}Binary")

                   }
               }
           }
        }


        apply plugin: MyComponentBinariesPlugin

        model {
            customModel {
                values << "1st" << "2nd"
            }
        }"""

        when:
        succeeds "components"
        then:
        output.contains(toPlatformLineSeparators("""
DefaultSampleLibrary 'sampleLib'
--------------------------------

Source sets
    No source sets.

Binaries
    DefaultSampleBinary: 'sampleLib1stBinary'
        build using task: :sampleLib1stBinary
    DefaultSampleBinary: 'sampleLib2ndBinary'
        build using task: :sampleLib2ndBinary
"""))
        where:
        ruleInputs << ["SampleLibrary library, CustomModel myModel"]//,  "CustomModel myModel, SampleLibrary library"]
    }


    String withSimpleComponentBinaries() {
        """
         class MyComponentBinariesPlugin implements Plugin<Project> {
            void apply(final Project project) {}

            @RuleSource
            static class Rules {
                @ComponentBinaries
                void createBinariesForSampleLibrary(CollectionBuilder<SampleBinary> binaries, SampleLibrary library) {
                    binaries.create("\${library.name}Binary")
                    binaries.create("\${library.name}OtherBinary", OtherSampleBinary)
                }
            }
         }
        apply plugin: MyComponentBinariesPlugin
"""
    }
}
