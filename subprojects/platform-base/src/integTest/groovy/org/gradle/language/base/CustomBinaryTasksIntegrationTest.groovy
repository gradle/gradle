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

public class CustomBinaryTasksIntegrationTest extends AbstractIntegrationSpec {

    def "setup"() {
        buildFile << """
        import org.gradle.model.*
        import org.gradle.model.collection.*

        interface SampleBinary extends BinarySpec {}
        class DefaultSampleBinary extends BaseBinarySpec implements SampleBinary {}
        interface SampleLibrary extends ComponentSpec<SampleBinary> {}
        class DefaultSampleLibrary extends BaseComponentSpec implements SampleLibrary {}

        class MyComponentBasePlugin implements Plugin<Project> {
            void apply(final Project project) {}

            @RuleSource
            static class Rules {
                @ComponentType
                void register(ComponentTypeBuilder<SampleLibrary> builder) {
                    builder.defaultImplementation(DefaultSampleLibrary)
                }

                @BinaryType
                void register(BinaryTypeBuilder<SampleBinary> builder) {
                    builder.defaultImplementation(DefaultSampleBinary)
                }

                @Mutate
                void createSampleComponentComponents(CollectionBuilder<SampleLibrary> componentSpecs) {
                    componentSpecs.create("sampleLib")
                }

                @ComponentBinaries
                void createBinariesForSampleLibrary(CollectionBuilder<SampleBinary> binaries, SampleLibrary library) {
                    binaries.create("\${library.name}Binary")
                }

            }
        }
        apply plugin:MyComponentBasePlugin

        """
    }


    @Unroll
    def "executing #taskdescr triggers custom task"() {
        given:
        buildFile << taskCreationRuleFromBinary()
        when:
        succeeds taskName
        then:
        output.contains(":customSampleLibBinaryTask UP-TO-DATE")
        where:
        taskName          | taskdescr
        "sampleLibBinary" | "lifecycle task"
        "assemble"        | "assemble task"
    }

    def "@BinaryTasks adds task to binary model"() {
        given:
        buildFile << taskCreationRuleFromBinary()
        when:
        buildFile << """

        task checkModel << {
            assert project.binaries.size() == 1
            assert project.binaries.sampleLibBinary != null
            assert project.binaries.sampleLibBinary.tasks.collect{it.name} == ['customSampleLibBinaryTask']
        }
"""
        then:
        succeeds "checkModel"
    }

    def "@BinaryTasks only applies to matching BinarySpec"() {
        when:
        buildFile << withOtherBinaryPlugin()
        buildFile << """

        task checkModel << {
            assert project.binaries.size() == 2
            def otherBinary = project.binaries.otherLibBinary
            assert otherBinary.tasks == [] as Set
        }
"""
        then:
        succeeds "checkModel"
    }

    def "@BinaryTasks supports additional parameters as rule inputs"() {
        when:
        buildFile << """
        class CustomModel {
            List<String> values = []
        }

        class BinaryTasksPlugin implements Plugin<Project> {
            void apply(final Project project) {}

            @RuleSource
            static class Rules {

                @Model
                CustomModel customModel() {
                    new CustomModel()
                }

                @BinaryTasks
                void createSampleComponentComponents(CollectionBuilder<Task> tasks, $ruleInputs) {
                    model.values.each { postFix ->
                        tasks.create("\${binary.getName()}\${postFix}");
                    }
                }
            }
        }

        apply plugin: BinaryTasksPlugin

        model {
            customModel {
                values << "1st" << "2nd"
            }
        }

        task checkModel << {
            assert project.binaries.size() == 1
            assert project.binaries.sampleLibBinary != null
            assert project.binaries.sampleLibBinary.tasks.collect{it.name}.sort() == ['sampleLibBinary1st', 'sampleLibBinary2nd']
        }"""
        then:
        succeeds "checkModel"
        where:
        ruleInputs << ["SampleBinary binary, CustomModel model",  "CustomModel model, SampleBinary binary"]
    }

    String taskCreationRuleFromBinary() {

        """
        class BinaryTasksPlugin implements Plugin<Project> {
            void apply(final Project project) {}

            @RuleSource
            static class Rules {
                @BinaryTasks
                void createSampleComponentComponents(CollectionBuilder<Task> tasks, SampleBinary binary) {
                    tasks.create("custom\${binary.getName().capitalize()}Task")
                }
            }
        }
        apply plugin:BinaryTasksPlugin
"""
    }

    String withOtherBinaryPlugin() {
        """
        interface OtherBinary extends BinarySpec {}
        class DefaultOtherBinary extends BaseBinarySpec implements OtherBinary {}
        interface OtherLibrary extends ComponentSpec<OtherBinary> {}
        class DefaultOtherLibrary extends BaseComponentSpec implements OtherLibrary{}

        class MyOtherBinariesPlugin implements Plugin<Project> {
            void apply(final Project project) {}

            @RuleSource
            static class Rules {
                @ComponentType
                void register(ComponentTypeBuilder<OtherLibrary> builder) {
                    builder.defaultImplementation(DefaultOtherLibrary)
                }

                @BinaryType
                void register(BinaryTypeBuilder<OtherBinary> builder) {
                    builder.defaultImplementation(DefaultOtherBinary)
                }

                @Mutate
                void createSampleComponentComponents(CollectionBuilder<OtherLibrary> componentSpecs) {
                    componentSpecs.create("otherLib")
                }

                @ComponentBinaries
                void createBinariesForSampleLibrary(CollectionBuilder<OtherBinary> binaries, OtherLibrary library) {
                    binaries.create("\${library.name}Binary")
                }
            }
        }
        apply plugin:MyOtherBinariesPlugin"""
    }
}