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

public class CustomBinaryTasksIntegrationTest extends AbstractIntegrationSpec {

    def "setup"() {
        buildFile << """
        import org.gradle.model.*
        import org.gradle.model.collection.*

        interface SampleBinary extends BinarySpec {}
        class DefaultSampleBinary extends BaseBinarySpec implements SampleBinary {}
        interface SampleLibrary extends ComponentSpec {}
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
        buildFile << defaultTaskCreationWithRuleFromBinary()
        when:
        succeeds taskName
        then:
        output.contains(":customSampleLibBinaryTask")
        output.contains("Building sampleLibBinary via customSampleLibBinaryTask of type DefaultTask")
        where:
        taskName          | taskdescr
        "assemble"        | "assemble task"
        "sampleLibBinary" | "lifecycle task"
    }

    def "BinaryTasks rule add default task to binary model"() {
        given:
        buildFile << defaultTaskCreationWithRuleFromBinary()
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

    def "BinaryTasks rule can declare typed task"() {
        given:
        buildFile << typedTaskCreationWithRuleFromBinary()
        when:
        succeeds "assemble"
        then:
        output.contains(":customSampleLibBinaryTask")
        output.contains("Building sampleLibBinary via customSampleLibBinaryTask of type BinaryCreationTask")

    }

    def "BinaryTasks rule only applies to matching BinarySpec"() {
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

    def "BinaryTasks rule supports additional parameters as rule inputs"() {
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

    def "can add multiple tasks to multiple binariers from same component"() {
        given:
        buildFile << multipleDefaultTaskCreationWithRuleFromBinary()
        buildFile << """
        class TestBinariesPlugin implements Plugin<Project> {
            void apply(final Project project) {}

           @RuleSource
            static class Rules {
                @ComponentBinaries
                void createBinariesForSampleLibrary(CollectionBuilder<SampleBinary> binaries, SampleLibrary library) {
                    binaries.create("\${library.name}TestBinary")
                }

            }
        }

        apply plugin: TestBinariesPlugin
         task checkModel << {
            assert project.binaries.size() == 2
            assert project.binaries.sampleLibBinary != null
            assert project.binaries.sampleLibBinary.tasks.collect{it.name}.sort() == ['someCustomSampleLibBinaryTask', 'someOtherCustomSampleLibBinaryTask']
        }"""
        expect:
        succeeds "checkModel"


        when:
        succeeds "assemble"
        then:
        output.contains(toPlatformLineSeparators(""":someCustomSampleLibBinaryTask
running someCustomSampleLibBinaryTask
:someOtherCustomSampleLibBinaryTask
running someOtherCustomSampleLibBinaryTask
:sampleLibBinary
:someCustomSampleLibTestBinaryTask
running someCustomSampleLibTestBinaryTask
:someOtherCustomSampleLibTestBinaryTask
running someOtherCustomSampleLibTestBinaryTask
:sampleLibTestBinary
:assemble

BUILD SUCCESSFUL"""))
    }


    String defaultTaskCreationWithRuleFromBinary() {

        """
        class BinaryTasksPlugin implements Plugin<Project> {
            void apply(final Project project) {}

            @RuleSource
            static class Rules {
                @BinaryTasks
                void createSampleComponentComponents(CollectionBuilder<Task> tasks, SampleBinary binary) {
                    tasks.create("custom\${binary.getName().capitalize()}Task"){
                        //TODO this should delegate to Task. currently referencing 'it' explicitly is needed
                        it.doLast{
                            println "Building \${binary.getName()} via \${it.getName()} of type DefaultTask"
                        }
                    }
                }
            }
        }
        apply plugin:BinaryTasksPlugin
"""
    }

    String multipleDefaultTaskCreationWithRuleFromBinary() {

        """
        class BinaryTasksPlugin implements Plugin<Project> {
            void apply(final Project project) {}

            @RuleSource
            static class Rules {
                @BinaryTasks
                void createSampleComponentComponents(CollectionBuilder<Task> tasks, SampleBinary binary) {
                    tasks.create("someCustom\${binary.getName().capitalize()}Task"){
                        it.doLast{
                            println "running \${it.name}"
                        }
                    }
                    tasks.create("someOtherCustom\${binary.getName().capitalize()}Task"){
                        it.doLast{
                            println "running \${it.name}"
                        }
                        it.dependsOn "someCustom\${binary.getName().capitalize()}Task"

                    }
                }
            }
        }
        apply plugin:BinaryTasksPlugin
"""
    }

    String typedTaskCreationWithRuleFromBinary() {

        """
        class BinaryCreationTask extends DefaultTask {
            BinarySpec binary
            @TaskAction void create(){
                println "Building \${binary.getName()} via \${getName()} of type BinaryCreationTask"

            }
        }
        class BinaryTasksPlugin implements Plugin<Project> {
            void apply(final Project project) {}

            @RuleSource
            static class Rules {
                @BinaryTasks
                void createSampleComponentComponents(CollectionBuilder<Task> tasks, SampleBinary binary) {
                    tasks.create("custom\${binary.getName().capitalize()}Task", BinaryCreationTask){
                        println "configuring \${binary.getName()}"
                        it.binary = binary
                    }
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
        interface OtherLibrary extends ComponentSpec {}
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