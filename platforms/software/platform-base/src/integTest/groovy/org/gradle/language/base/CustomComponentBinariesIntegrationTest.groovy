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
import org.gradle.integtests.fixtures.StableConfigurationCacheDeprecations
import org.gradle.integtests.fixtures.UnsupportedWithConfigurationCache

@UnsupportedWithConfigurationCache(because = "software model")
class CustomComponentBinariesIntegrationTest extends AbstractIntegrationSpec implements StableConfigurationCacheDeprecations {

    def "setup"() {
        buildFile << """
    @Managed interface SampleLibrary extends GeneralComponentSpec {}
    @Managed interface SampleBinary extends BinarySpec {}
    @Managed interface OtherSampleBinary extends SampleBinary {}
    @Managed interface LibrarySourceSet extends LanguageSourceSet {}

    class MyBinaryDeclarationModel implements Plugin<Project> {
        void apply(final Project project) {}

        static class ComponentModel extends RuleSource {
            @ComponentType
            void registerLibrary(TypeBuilder<SampleLibrary> builder) {}

            @Mutate
            void createSampleComponentComponents(ModelMap<SampleLibrary> componentSpecs) {
                componentSpecs.create("sampleLib") {
                    sources {
                        librarySource(LibrarySourceSet)
                    }
                }
            }

            @ComponentType
            void registerBinary(TypeBuilder<SampleBinary> builder) {}

            @ComponentType
            void registerOtherBinary(TypeBuilder<OtherSampleBinary> builder) {}

            @ComponentType
            void registerSourceSet(TypeBuilder<LibrarySourceSet> builder) {
            }
        }
    }

    apply plugin:MyBinaryDeclarationModel
"""
    }

    def "binaries registered using @ComponentBinaries rule are visible in model report"() {
        when:
        buildFile << withSimpleComponentBinaries()

        then:
        expectTaskGetProjectDeprecations()
        succeeds "model"

        and:
        def reportOutput = ModelReportOutput.from(output)
        reportOutput.hasNodeStructure {
            components {
                sampleLib {
                    binaries {
                        binary(type: 'SampleBinary', creator: 'MyComponentBinariesPlugin.Rules#createBinariesForSampleLibrary(ModelMap<SampleBinary>, SampleLibrary) > create(binary)') {
                            tasks()
                            sources()
                        }
                        otherBinary(type: 'OtherSampleBinary', creator: 'MyComponentBinariesPlugin.Rules#createBinariesForSampleLibrary(ModelMap<SampleBinary>, SampleLibrary) > create(otherBinary)') {
                            tasks()
                            sources()
                        }
                    }
                    sources() {
                        librarySource()
                    }
                }
            }
        }
        reportOutput.hasNodeStructure {
            binaries {
                sampleLibBinary()
                sampleLibOtherBinary()
            }
        }
    }

    def "can register binaries using @ComponentBinaries rule"() {
        when:
        buildFile << withSimpleComponentBinaries()
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
                        assert sampleBinary.displayName == "SampleBinary 'sampleLib:binary'"
                        assert othersSampleBinary instanceof OtherSampleBinary
                        assert othersSampleBinary.displayName == "OtherSampleBinary 'sampleLib:otherBinary'"
                    }
                }
            }
        }
'''

        then:
        succeeds "checkModel"
    }

    def "links binaries to component"() {
        given:
        buildFile << withSimpleComponentBinaries()
        when:
        expectTaskGetProjectDeprecations()
        succeeds "components"
        then:
        output.contains("""
SampleLibrary 'sampleLib'
-------------------------

Source sets
    Library source 'sampleLib:librarySource'
        srcDir: src${File.separator}sampleLib${File.separator}librarySource

Binaries
    SampleBinary 'sampleLib:binary'
        build using task: :sampleLibBinary
    OtherSampleBinary 'sampleLib:otherBinary'
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
                            assert sampleBinarySourceSet instanceof LibrarySourceSet
                            assert sampleBinarySourceSet.displayName == "Library source 'sampleLib:librarySource'"
                            assert othersSampleBinarySourceSet instanceof LibrarySourceSet
                            assert othersSampleBinarySourceSet.displayName == "Library source 'sampleLib:librarySource'"
                        }
                    }
                }
            }
'''
        then:
        succeeds "checkSourceSets"
    }

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

    def "@ComponentBinaries rule supports additional parameters as rule inputs"() {
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
        expectTaskGetProjectDeprecations()
        succeeds "components"
        then:
        output.contains("""
SampleLibrary 'sampleLib'
-------------------------

Source sets
    Library source 'sampleLib:librarySource'
        srcDir: src${File.separator}sampleLib${File.separator}librarySource

Binaries
    SampleBinary 'sampleLib:1stBinary'
        build using task: :sampleLib1stBinary
    SampleBinary 'sampleLib:2ndBinary'
        build using task: :sampleLib2ndBinary
""")
        where:
        ruleInputs << ["SampleLibrary library, CustomModel myModel",  "CustomModel myModel, SampleLibrary library"]
    }

    def "@ComponentBinaries rule operates with fully configured component"() {
        given:
        buildFile << """
@Managed
trait BinaryWithValue implements BinarySpec {
    String valueFromComponent
}
@Managed
trait ComponentWithValue implements GeneralComponentSpec {
    String valueForBinary
}

class MyComponentBinariesPlugin implements Plugin<Project> {
    void apply(final Project project) {}

    static class Rules extends RuleSource {
        @ComponentType
        void registerComponent(TypeBuilder<ComponentWithValue> builder) {}

        @ComponentType
        void registerBinary(TypeBuilder<BinaryWithValue> builder) {}

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
        expectTaskGetProjectDeprecations()
        succeeds "model"

        then:
        def modelReport = ModelReportOutput.from(output).modelNode
        assert modelReport.components.custom.valueForBinary.@nodeValue[0] == 'configured-value'
        assert modelReport.components.custom.binaries.myBinary.valueFromComponent.@nodeValue[0] == 'configured-value'
    }

    String withSimpleComponentBinaries() {
        """
         class MyComponentBinariesPlugin implements Plugin<Project> {
            void apply(final Project project) {}

            static class Rules extends RuleSource {
                @ComponentBinaries
                void createBinariesForSampleLibrary(ModelMap<SampleBinary> binaries, SampleLibrary library) {
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
        expectTaskGetProjectDeprecations()
        succeeds "components"

        then:
        output.contains("""
Binaries
    SampleBinary 'sampleLib:derivedFromMethodName'
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
        failure.assertHasCause("Cannot create 'components.sampleLib.binaries.illegal' using creation rule 'IllegallyMutatingComponentBinariesRules#createBinariesForSampleLibrary(ModelMap<SampleBinary>, SampleLibrary, BinariesHolder) > create(illegal)' as model element 'components.sampleLib.binaries' is no longer mutable.")
    }

    def "reports failure in @ComponentBinaries rule"() {
        when:
        buildFile << """
            class MyComponentBinariesPlugin extends RuleSource {
                @ComponentBinaries
                void createBinariesForSampleLibrary(ModelMap<SampleBinary> binaries, SampleLibrary library) {
                    throw new RuntimeException('broken')
                }
            }
            apply plugin: MyComponentBinariesPlugin
        """

        then:
        fails "model"
        failure.assertHasCause('Exception thrown while executing model rule: MyComponentBinariesPlugin#createBinariesForSampleLibrary')
        failure.assertHasCause('broken')
    }
}
