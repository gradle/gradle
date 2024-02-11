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
import org.gradle.integtests.fixtures.UnsupportedWithConfigurationCache

@UnsupportedWithConfigurationCache(because = "software model")
class CustomBinaryTasksIntegrationTest extends AbstractIntegrationSpec {

    def "setup"() {
        buildFile << """
        @Managed interface SampleLibrary extends GeneralComponentSpec {}
        @Managed interface SampleBinary extends BinarySpec {}

        class MyComponentBasePlugin extends RuleSource {
            @ComponentType
            void registerLibrary(TypeBuilder<SampleLibrary> builder) {
            }

            @ComponentType
            void registerBinary(TypeBuilder<SampleBinary> builder) {
            }

            @Mutate
            void createSampleComponentComponents(ModelMap<SampleLibrary> componentSpecs) {
                componentSpecs.create("sampleLib")
            }

            @ComponentBinaries
            void createBinariesForSampleLibrary(ModelMap<SampleBinary> binaries, SampleLibrary library) {
                binaries.create("binaryOne")
                binaries.create("binaryTwo")
            }
        }
        apply plugin:MyComponentBasePlugin

        """
    }

    def "executing #taskdescr triggers custom task"() {
        given:
        buildFile << """
        class BinaryTasksPlugin extends RuleSource {
            @BinaryTasks
            void createSampleComponentComponents(ModelMap<Task> tasks, SampleBinary binary) {
                tasks.create("\${binary.projectScopedName}Task")
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

    def "details of rule-added tasks are visible in model report"() {
        given:
        buildFile << '''
        class BinaryTasksPlugin extends RuleSource {
            @BinaryTasks
            void createSampleComponentComponents(ModelMap<Task> tasks, SampleBinary binary) {
                tasks.create("${binary.projectScopedName}Task")
            }
        }
        apply plugin:BinaryTasksPlugin
'''

        when:
        run "model"

        then:
        def tasksNode = ModelReportOutput.from(output).modelNode.tasks
        tasksNode.sampleLibBinaryOneTask.@type[0] == 'org.gradle.api.DefaultTask'
    }

    def "can reference rule-added tasks in model"() {
        given:
        buildFile << '''
        class BinaryTasksPlugin extends RuleSource {
            @BinaryTasks
            void createSampleComponentComponents(ModelMap<Task> tasks, SampleBinary binary) {
                tasks.create("${binary.projectScopedName}Task")
            }
        }
        apply plugin:BinaryTasksPlugin
        model {
            tasks {
                checkModel(Task) {
                    doLast {
                        def binaries = $.binaries
                        assert binaries.size() == 2
                        assert binaries.sampleLibBinaryOne != null
                        assert binaries.sampleLibBinaryOne.tasks*.name == ['sampleLibBinaryOneTask']
                    }
                }
            }
        }
'''
        expect:
        succeeds "checkModel"
    }

    def "rule can declare task with type"() {
        given:
        buildFile << """
        class BinaryCreationTask extends DefaultTask {
            @Internal
            BinarySpec binary
            @TaskAction void create(){
                println "Building \${binary.projectScopedName} via \${name} of type BinaryCreationTask"
            }
        }
        class BinaryTasksPlugin extends RuleSource {
            @BinaryTasks
            void createSampleComponentComponents(ModelMap<Task> tasks, SampleBinary binary) {
                tasks.create("\${binary.projectScopedName}Task", BinaryCreationTask) {
                    it.binary = binary
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
        @Managed interface OtherBinary extends SampleBinary {}

        class MyOtherBinariesPlugin extends RuleSource {
            @ComponentType
            void register(TypeBuilder<OtherBinary> builder) {
            }

            @ComponentBinaries
            void createBinariesForSampleLibrary(ModelMap<OtherBinary> binaries, SampleLibrary library) {
                binaries.create("otherBinary")
            }

            @BinaryTasks
            void createTasks(ModelMap<Task> tasks, OtherBinary binary) {
                tasks.create("\${binary.projectScopedName}OtherTask")
            }
        }
        apply plugin:MyOtherBinariesPlugin
"""
        when:
        succeeds "sampleLibBinaryOne"

        then:
        result.assertTasksExecuted(":sampleLibBinaryOne")

        when:
        succeeds "sampleLibOtherBinary"

        then:
        result.assertTasksExecuted(":sampleLibOtherBinaryOtherTask", ":sampleLibOtherBinary")
    }

    def "can use additional parameters as rule inputs"() {
        given:
        buildFile << """
        class CustomModel {
            List<String> values = []
        }

        class BinaryTasksPlugin extends RuleSource {
            @Model
            CustomModel customModel() {
                new CustomModel()
            }

            @BinaryTasks
            void createTasks(ModelMap<Task> tasks, $ruleInputs) {
                model.values.each { postFix ->
                    tasks.create("\${binary.projectScopedName}\${postFix}");
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
        class BinaryTasksPlugin extends RuleSource {
            @BinaryTasks
            void createTasks(ModelMap<Task> tasks, SampleBinary binary) {
                tasks.create("\${binary.projectScopedName}TaskOne"){
                    it.doLast{
                        println "running \${it.name}"
                    }
                }
                tasks.create("\${binary.projectScopedName}TaskTwo"){
                    it.doLast{
                        println "running \${it.name}"
                    }
                    it.dependsOn "\${binary.projectScopedName}TaskOne"
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

    def "reports failure in rule method"() {
        given:
        buildFile << '''
        class BinaryTasksPlugin extends RuleSource {
            @BinaryTasks
            void createSampleComponentComponents(ModelMap<Task> tasks, SampleBinary binary) {
                throw new RuntimeException('broken')
            }
        }
        apply plugin:BinaryTasksPlugin
        model {
            tasks {
                checkModel(Task) {
                    doLast {
                        def binaries = $.binaries
                        assert binaries.size() == 2
                    }
                }
            }
        }
'''
        expect:
        fails "checkModel"
        failure.assertHasCause("Exception thrown while executing model rule: BinaryTasksPlugin#createSampleComponentComponents")
        failure.assertHasCause("broken")
    }

}
