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
        interface SampleBinary extends BinarySpec {}
        class DefaultSampleBinary extends BaseBinarySpec implements SampleBinary {}
        interface SampleLibrary extends ComponentSpec {}
        class DefaultSampleLibrary extends BaseComponentSpec implements SampleLibrary {}

        class MyComponentBasePlugin implements Plugin<Project> {
            void apply(final Project project) {}

            static class Rules extends RuleSource {
                @ComponentType
                void register(ComponentTypeBuilder<SampleLibrary> builder) {
                    builder.defaultImplementation(DefaultSampleLibrary)
                }

                @BinaryType
                void register(BinaryTypeBuilder<SampleBinary> builder) {
                    builder.defaultImplementation(DefaultSampleBinary)
                }

                @Mutate
                void createSampleComponentComponents(ModelMap<SampleLibrary> componentSpecs) {
                    componentSpecs.create("sampleLib")
                }

                @ComponentBinaries
                void createBinariesForSampleLibrary(ModelMap<SampleBinary> binaries, SampleLibrary library) {
                    binaries.create("\${library.name}BinaryOne")
                    binaries.create("\${library.name}BinaryTwo")
                }
            }
        }
        apply plugin:MyComponentBasePlugin

        """
    }


    @Unroll
    def "executing #taskdescr triggers custom task"() {
        given:
        buildFile << """
        class BinaryTasksPlugin implements Plugin<Project> {
            void apply(final Project project) {}

            static class Rules extends RuleSource {
                @BinaryTasks
                void createSampleComponentComponents(ModelMap<Task> tasks, SampleBinary binary) {
                    tasks.create("\${binary.name}Task")
                }
            }
        }
        apply plugin:BinaryTasksPlugin
"""

        when:
        succeeds taskName
        then:
        executed ":sampleLibBinaryOneTask", ":sampleLibBinaryOne"

        where:
        taskName             | taskdescr
        "assemble"           | "assemble task"
        "sampleLibBinaryOne" | "binary lifecycle task"
    }

    @Unroll
    def "can use CollectionBuilder as the first parameter of a BinaryTasks annotated rule"() {
        given:
        buildFile << """
        class BinaryTasksPlugin implements Plugin<Project> {
            void apply(final Project project) {}

            static class Rules extends RuleSource {
                @BinaryTasks
                void createSampleComponentComponents(CollectionBuilder<Task> tasks, SampleBinary binary) {
                    tasks.create("usingCollectionBuilder")
                }
            }
        }
        apply plugin: BinaryTasksPlugin
"""

        expect:
        succeeds "usingCollectionBuilder"
    }

    def "can reference rule-added tasks in model"() {
        given:
        buildFile << """
        class BinaryTasksPlugin implements Plugin<Project> {
            void apply(final Project project) {}

            static class Rules extends RuleSource {
                @BinaryTasks
                void createSampleComponentComponents(ModelMap<Task> tasks, SampleBinary binary) {
                    tasks.create("\${binary.name}Task")
                }
            }
        }
        apply plugin:BinaryTasksPlugin

        task checkModel << {
            assert project.binaries.size() == 2
            assert project.binaries.sampleLibBinaryOne != null
            assert project.binaries.sampleLibBinaryOne.tasks*.name == ['sampleLibBinaryOneTask']
        }
"""
        expect:
        succeeds "checkModel"
    }

    def "rule can declare task with type"() {
        given:
        buildFile << """
        class BinaryCreationTask extends DefaultTask {
            BinarySpec binary
            @TaskAction void create(){
                println "Building \${binary.getName()} via \${getName()} of type BinaryCreationTask"

            }
        }
        class BinaryTasksPlugin implements Plugin<Project> {
            void apply(final Project project) {}

            static class Rules extends RuleSource {
                @BinaryTasks
                void createSampleComponentComponents(ModelMap<Task> tasks, SampleBinary binary) {
                    tasks.create("\${binary.name}Task", BinaryCreationTask) {
                        println "configuring \${binary.getName()}"
                        it.binary = binary
                    }
                }
            }
        }
        apply plugin:BinaryTasksPlugin
"""
        when:
        succeeds "sampleLibBinaryOne"

        then:
        executedAndNotSkipped ":sampleLibBinaryOneTask"
        output.contains("Building sampleLibBinaryOne via sampleLibBinaryOneTask of type BinaryCreationTask")
    }

    def "rule applies only to specified binary type"() {
        given:
        buildFile << """
        interface OtherBinary extends SampleBinary {}
        class DefaultOtherBinary extends DefaultSampleBinary implements OtherBinary {}

        class MyOtherBinariesPlugin implements Plugin<Project> {
            void apply(final Project project) {}

            static class Rules extends RuleSource {
                @BinaryType
                void register(BinaryTypeBuilder<OtherBinary> builder) {
                    builder.defaultImplementation(DefaultOtherBinary)
                }

                @ComponentBinaries
                void createBinariesForSampleLibrary(ModelMap<OtherBinary> binaries, SampleLibrary library) {
                    binaries.create("\${library.name}OtherBinary")
                }

                @BinaryTasks
                void createTasks(ModelMap<Task> tasks, OtherBinary binary) {
                    tasks.create("\${binary.name}OtherTask")
                }
            }
        }
        apply plugin:MyOtherBinariesPlugin
"""
        when:
        succeeds "sampleLibBinaryOne"

        then:
        notExecuted ":sampleLibOtherBinaryOtherTask"

        when:
        succeeds "sampleLibOtherBinary"

        then:
        executed ":sampleLibOtherBinaryOtherTask"
    }

    def "can use additional parameters as rule inputs"() {
        given:
        buildFile << """
        class CustomModel {
            List<String> values = []
        }

        class BinaryTasksPlugin implements Plugin<Project> {
            void apply(final Project project) {}

            static class Rules extends RuleSource {

                @Model
                CustomModel customModel() {
                    new CustomModel()
                }

                @BinaryTasks
                void createTasks(ModelMap<Task> tasks, $ruleInputs) {
                    model.values.each { postFix ->
                        tasks.create("\${binary.name}\${postFix}");
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

"""
        when:
        succeeds "assemble"

        then:
        executed ":sampleLibBinaryOne1st", ":sampleLibBinaryOne2nd", ":sampleLibBinaryTwo1st", ":sampleLibBinaryTwo2nd"

        where:
        ruleInputs << ["SampleBinary binary, CustomModel model", "CustomModel model, SampleBinary binary"]
    }

    def "can create multiple tasks for each of multiple binaries for same component"() {
        given:
        buildFile << """
        class BinaryTasksPlugin implements Plugin<Project> {
            void apply(final Project project) {}

            static class Rules extends RuleSource {
                @BinaryTasks
                void createTasks(ModelMap<Task> tasks, SampleBinary binary) {
                    tasks.create("\${binary.name}TaskOne"){
                        it.doLast{
                            println "running \${it.name}"
                        }
                    }
                    tasks.create("\${binary.name}TaskTwo"){
                        it.doLast{
                            println "running \${it.name}"
                        }
                        it.dependsOn "\${binary.name}TaskOne"
                    }
                }
            }
        }
        apply plugin:BinaryTasksPlugin
"""

        when:
        succeeds "assemble"
        then:
        executedAndNotSkipped ":sampleLibBinaryOneTaskOne", ":sampleLibBinaryOneTaskTwo", ":sampleLibBinaryOne",
                ":sampleLibBinaryTwoTaskOne", ":sampleLibBinaryTwoTaskTwo", ":sampleLibBinaryTwo",
                ":assemble"

        output.contains "running sampleLibBinaryOneTaskOne"
        output.contains "running sampleLibBinaryOneTaskTwo"
        output.contains "running sampleLibBinaryTwoTaskOne"
        output.contains "running sampleLibBinaryTwoTaskTwo"
    }


}
