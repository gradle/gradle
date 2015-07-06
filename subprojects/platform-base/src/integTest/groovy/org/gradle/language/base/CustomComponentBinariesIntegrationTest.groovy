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
import org.gradle.model.ModelMap
import org.gradle.model.collection.CollectionBuilder
import spock.lang.Ignore
import spock.lang.Unroll

import static org.gradle.util.TextUtil.toPlatformLineSeparators

class CustomComponentBinariesIntegrationTest extends AbstractIntegrationSpec {

    def "setup"() {
        buildFile << """
interface SampleBinary extends BinarySpec {}
interface OtherSampleBinary extends SampleBinary {}

interface LibrarySourceSet extends LanguageSourceSet {}

class DefaultLibrarySourceSet extends BaseLanguageSourceSet implements LibrarySourceSet { }

class DefaultSampleBinary extends BaseBinarySpec implements SampleBinary {}

class OtherSampleBinaryImpl extends BaseBinarySpec implements OtherSampleBinary {}

interface SampleLibrary extends ComponentSpec {}

class DefaultSampleLibrary extends BaseComponentSpec implements SampleLibrary {}

    class MyBinaryDeclarationModel implements Plugin<Project> {
        void apply(final Project project) {}

        static class ComponentModel extends RuleSource {
            @ComponentType
            void register(ComponentTypeBuilder<SampleLibrary> builder) {
                builder.defaultImplementation(DefaultSampleLibrary)
            }

            @Mutate
            void createSampleComponentComponents(ModelMap<SampleLibrary> componentSpecs) {
                componentSpecs.create("sampleLib") {
                    sources {
                        librarySource(LibrarySourceSet)
                    }
                }
            }

            @BinaryType
            void register(BinaryTypeBuilder<SampleBinary> builder) {
                builder.defaultImplementation(DefaultSampleBinary)
            }

            @BinaryType
            void registerOther(BinaryTypeBuilder<OtherSampleBinary> builder) {
                builder.defaultImplementation(OtherSampleBinaryImpl)
            }

            @LanguageType
            void registerSourceSet(LanguageTypeBuilder<LibrarySourceSet> builder) {
                builder.setLanguageName("librarySource")
                builder.defaultImplementation(DefaultLibrarySourceSet)
            }
        }
    }

    apply plugin:MyBinaryDeclarationModel
"""
    }

    @Unroll
    def "can register binaries using @ComponentBinaries when viewing binaries container as #binariesContainerType.simpleName"() {
        when:
        buildFile << withSimpleComponentBinaries(binariesContainerType)
        buildFile << """


        task checkModel << {
            assert project.binaries.size() == 2
            def sampleBinary = project.binaries.sampleLibBinary
            def othersSampleBinary = project.binaries.sampleLibOtherBinary
            assert sampleBinary instanceof SampleBinary
            assert sampleBinary.displayName == "DefaultSampleBinary 'sampleLibBinary'"
            assert othersSampleBinary instanceof OtherSampleBinary
            assert othersSampleBinary.displayName == "OtherSampleBinaryImpl 'sampleLibOtherBinary'"
        }
"""
        then:
        succeeds "checkModel"

        where:
        binariesContainerType << [CollectionBuilder, ModelMap]
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
    DefaultLibrarySourceSet 'sampleLib:librarySource'
        srcDir: src${File.separator}sampleLib${File.separator}librarySource

Binaries
    DefaultSampleBinary 'sampleLibBinary'
        build using task: :sampleLibBinary
    OtherSampleBinaryImpl 'sampleLibOtherBinary'
        build using task: :sampleLibOtherBinary
"""))
    }

    def "links components sourceSets to binaries"() {
        when:
        buildFile << withSimpleComponentBinaries()
        buildFile << """
        task checkSourceSets << {
            def sampleBinarySourceSet = project.binaries.sampleLibBinary.inputs.toList()[0]
            def othersSampleBinarySourceSet = project.binaries.sampleLibOtherBinary.inputs.toList()[0]
            assert sampleBinarySourceSet instanceof DefaultLibrarySourceSet
            assert sampleBinarySourceSet.displayName == "DefaultLibrarySourceSet 'sampleLib:librarySource'"
            assert othersSampleBinarySourceSet instanceof DefaultLibrarySourceSet
            assert othersSampleBinarySourceSet.displayName == "DefaultLibrarySourceSet 'sampleLib:librarySource'"
        }
"""
        then:
        succeeds "checkSourceSets"
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

            static class Rules extends RuleSource {
               @Model
               CustomModel customModel() {
                   new CustomModel()
               }

               @ComponentBinaries
               void createBinariesForSampleLibrary(ModelMap<SampleBinary> binaries, $ruleInputs) {
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
    DefaultLibrarySourceSet 'sampleLib:librarySource'
        srcDir: src${File.separator}sampleLib${File.separator}librarySource

Binaries
    DefaultSampleBinary 'sampleLib1stBinary'
        build using task: :sampleLib1stBinary
    DefaultSampleBinary 'sampleLib2ndBinary'
        build using task: :sampleLib2ndBinary
"""))
        where:
        ruleInputs << ["SampleLibrary library, CustomModel myModel"]//,  "CustomModel myModel, SampleLibrary library"]
    }

    String withSimpleComponentBinaries(Class<? extends CollectionBuilder> binariesContainerType = ModelMap) {
        """
         class MyComponentBinariesPlugin implements Plugin<Project> {
            void apply(final Project project) {}

            static class Rules extends RuleSource {
                @ComponentBinaries
                void createBinariesForSampleLibrary(${binariesContainerType.simpleName}<SampleBinary> binaries, SampleLibrary library) {
                    binaries.create("\${library.name}Binary")
                    binaries.create("\${library.name}OtherBinary", OtherSampleBinary)
                }
            }
         }
        apply plugin: MyComponentBinariesPlugin
"""
    }

    def "subject of @ComponentBinaries rule is Groovy decorated"() {
        buildFile << """
            class GroovyComponentBinariesRules extends RuleSource {
                @ComponentBinaries
                void createBinariesForSampleLibrary(ModelMap<SampleBinary> binaries, SampleLibrary library) {
                    binaries.derivedFromMethodName(SampleBinary) {}
                }
            }

            apply type: GroovyComponentBinariesRules
        """

        when:
        succeeds "components"

        then:
        output.contains(toPlatformLineSeparators("""
Binaries
    DefaultSampleBinary 'derivedFromMethodName'
        build using task: :derivedFromMethodName
"""))
    }

    @Ignore("Not supported due to BinaryTasks rules now operating directly on component.binaries, which is not managed - LD - 15/5/15")
    def "attempt to mutate the subject of a @ComponentBinaries after the method has finished results in an error"() {
        buildFile << """
            class BinariesHolder {
                ModelMap<SampleBinary> binaries
            }
            class IllegallyMutatingComponentBinariesRules extends RuleSource {
                @Model
                BinariesHolder holder() {
                    return new BinariesHolder()
                }

                @ComponentBinaries
                void createBinariesForSampleLibrary(ModelMap<SampleBinary> binaries, SampleLibrary library, BinariesHolder holder) {
                    holder.binaries = binaries
                }

                @Mutate
                void mutateBinariesOutsideOfComponentBinariesRule(ModelMap<Task> task, BinariesHolder holder) {
                    holder.binaries.create("illegal", SampleBinary)
                }
            }

            apply type: IllegallyMutatingComponentBinariesRules
        """

        when:
        fails "tasks"

        then:
        failure.assertHasCause("Attempt to mutate closed view of model of type '${ModelMap.name}<SampleBinary>' given to rule 'IllegallyMutatingComponentBinariesRules#createBinariesForSampleLibrary(org.gradle.model.ModelMap<SampleBinary>, SampleLibrary, BinariesHolder)'")
    }
}
