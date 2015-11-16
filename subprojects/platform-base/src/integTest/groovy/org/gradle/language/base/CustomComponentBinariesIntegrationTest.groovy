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

import org.gradle.api.reporting.model.ModelReportOutput
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.model.ModelMap
import org.gradle.model.collection.CollectionBuilder
import spock.lang.Unroll

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
        buildFile << '''

        model {
            tasks {
                checkModel(Task) {
                    doLast {
                        def binaries = $.binaries
                        assert binaries.size() == 2
                        def sampleBinary = binaries.sampleLibBinary
                        def othersSampleBinary = binaries.sampleLibOtherBinary
                        assert sampleBinary instanceof SampleBinary
                        assert sampleBinary.displayName == "DefaultSampleBinary 'sampleLib:binary'"
                        assert othersSampleBinary instanceof OtherSampleBinary
                        assert othersSampleBinary.displayName == "OtherSampleBinaryImpl 'sampleLib:otherBinary'"
                    }
                }
            }
        }
'''

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
        output.contains(
"""DefaultSampleLibrary 'sampleLib'
--------------------------------

Source sets
    DefaultLibrarySourceSet 'sampleLib:librarySource'
        srcDir: src${File.separator}sampleLib${File.separator}librarySource

Binaries
    DefaultSampleBinary 'sampleLib:binary'
        build using task: :sampleLibBinary
    OtherSampleBinaryImpl 'sampleLib:otherBinary'
        build using task: :sampleLibOtherBinary
""")
    }

    def "links components sourceSets to binaries"() {
        when:
        buildFile << withSimpleComponentBinaries()
        buildFile << '''
            model {
                tasks {
                    checkSourceSets(Task) {
                        doLast {
                            def binaries = $.binaries
                            def sampleBinarySourceSet = binaries.sampleLibBinary.inputs.toList()[0]
                            def othersSampleBinarySourceSet = binaries.sampleLibOtherBinary.inputs.toList()[0]
                            assert sampleBinarySourceSet instanceof DefaultLibrarySourceSet
                            assert sampleBinarySourceSet.displayName == "DefaultLibrarySourceSet 'sampleLib:librarySource'"
                            assert othersSampleBinarySourceSet instanceof DefaultLibrarySourceSet
                            assert othersSampleBinarySourceSet.displayName == "DefaultLibrarySourceSet 'sampleLib:librarySource'"
                        }
                    }
                }
            }
'''
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

    def "can access lifecycle task of binary via BinarySpec.buildTask"(){
        when:
        buildFile << withSimpleComponentBinaries()
        buildFile << '''
            model {
                tasks {
                    tellTaskName(Task) {
                        doLast {
                            def binaries = $.binaries
                            assert binaries.sampleLibBinary.buildTask instanceof Task
                            assert binaries.sampleLibBinary.buildTask.name == "sampleLibBinary"
                        }
                    }
                }
            }
'''
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
                        binaries.create("\${value}Binary")
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
        output.contains("""
DefaultSampleLibrary 'sampleLib'
--------------------------------

Source sets
    DefaultLibrarySourceSet 'sampleLib:librarySource'
        srcDir: src${File.separator}sampleLib${File.separator}librarySource

Binaries
    DefaultSampleBinary 'sampleLib:1stBinary'
        build using task: :sampleLib1stBinary
    DefaultSampleBinary 'sampleLib:2ndBinary'
        build using task: :sampleLib2ndBinary
""")
        where:
        ruleInputs << ["SampleLibrary library, CustomModel myModel",  "CustomModel myModel, SampleLibrary library"]
    }

    def "ComponentBinaries rule operates with fully configured component"() {
        given:
        buildFile << """
@Managed
trait BinaryWithValue implements BinarySpec {
    String valueFromComponent
}
@Managed
trait ComponentWithValue implements ComponentSpec {
    String valueForBinary
}

class MyComponentBinariesPlugin implements Plugin<Project> {
    void apply(final Project project) {}

    static class Rules extends RuleSource {
        @ComponentType
        void register(ComponentTypeBuilder<ComponentWithValue> builder) {}

        @BinaryType
        void register(BinaryTypeBuilder<BinaryWithValue> builder) {}
        
        @ComponentBinaries
        void createBinaries(ModelMap<BinaryWithValue> binaries, ComponentWithValue component) {
            assert component.valueForBinary == "configured-value"
            binaries.create("myBinary") {
                assert component.valueForBinary == "configured-value"
                valueFromComponent = component.valueForBinary
            }
        }
    }
}

apply plugin: MyComponentBinariesPlugin

model {
    components {
        custom(ComponentWithValue) {
            valueForBinary = "create-value"
        }
    }
    components {
        custom {
            valueForBinary = "configured-value"
        }
    }
    tasks {
        checkModel(Task) {
            doLast {
                def component = \$.components.custom
                assert component.binaries.size() == 1
                def binary = component.binaries.myBinary

                assert component.valueForBinary == "configured-value"
                assert binary.valueFromComponent == "configured-value"
            }
        }
    }
}

"""

        when:
        succeeds "model"

        then:
        def modelReport = ModelReportOutput.from(output).modelNode
        assert modelReport.components.custom.valueForBinary.@nodeValue[0] == 'configured-value'
        assert modelReport.components.custom.binaries.myBinary.valueFromComponent.@nodeValue[0] == 'configured-value'
    }

    String withSimpleComponentBinaries(Class<? extends CollectionBuilder> binariesContainerType = ModelMap) {
        """
         class MyComponentBinariesPlugin implements Plugin<Project> {
            void apply(final Project project) {}

            static class Rules extends RuleSource {
                @ComponentBinaries
                void createBinariesForSampleLibrary(${binariesContainerType.simpleName}<SampleBinary> binaries, SampleLibrary library) {
                    binaries.create("binary")
                    binaries.create("otherBinary", OtherSampleBinary)
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
        output.contains("""
Binaries
    DefaultSampleBinary 'sampleLib:derivedFromMethodName'
        build using task: :sampleLibDerivedFromMethodName
""")
    }

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
        failure.assertHasCause("Cannot create 'components.sampleLib.binaries.illegal' using creation rule 'IllegallyMutatingComponentBinariesRules#createBinariesForSampleLibrary > components.sampleLib.getBinaries() > create(illegal)' as model element 'components.sampleLib.binaries' is no longer mutable.")
    }
}
