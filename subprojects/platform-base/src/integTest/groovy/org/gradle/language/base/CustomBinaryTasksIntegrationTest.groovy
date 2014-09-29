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

public class CustomBinaryTasksIntegrationTest extends AbstractIntegrationSpec {

    def "setup"() {
        buildFile << """
        import org.gradle.model.*
        import org.gradle.model.collection.*

        interface SampleBinary extends BinarySpec {}
        interface OtherSampleBinary extends SampleBinary {}
        class DefaultSampleBinary extends BaseBinarySpec implements SampleBinary {}
        class OtherSampleBinaryImpl extends BaseBinarySpec implements OtherSampleBinary {}
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
                    binaries.create("\${library.name}Binary", SampleBinary)
                }


            }
        }
        apply plugin:MyComponentBasePlugin

        """
    }

    def "can register binary creation tasks using @BinaryTask"() {
        given:
        buildFile << withBinaryTasksPlugin();
        when:
        succeeds "tasks", "--all"
        then:
        output.contains(toPlatformLineSeparators(
        """
sampleLibBinary - Assembles DefaultSampleBinary: 'sampleLibBinary'.
    customSampleLibBinaryTask"""))
    }

    def "@BinaryTasks adds task to binary model"() {
        when:
        buildFile << withBinaryTasksPlugin();
        buildFile << """

        task checkModel << {
            assert project.binaries.size() == 1
            def sampleBinary = project.binaries.sampleLibBinary
            assert sampleBinary instanceof SampleBinary
            assert sampleBinary.displayName == "DefaultSampleBinary: 'sampleLibBinary'"
            assert sampleBinary.tasks.collect{it.name} == ['customSampleLibBinaryTask']
        }
"""
        then:
        succeeds "checkModel"
    }

    def "lifecycle task task runs custom task"() {
        given:
        buildFile << withBinaryTasksPlugin();
        when:
        succeeds "assemble"
        then:
        output.contains(toPlatformLineSeparators(""":customSampleLibBinaryTask UP-TO-DATE
:sampleLibBinary UP-TO-DATE
:assemble UP-TO-DATE"""))
    }

    String withBinaryTasksPlugin() {
 """class MyBinaryTasks implements Plugin<Project> {
        void apply(final Project project) {}

        @RuleSource
        static class Rules1 {
            @BinaryTasks
            void createSampleComponentComponents(CollectionBuilder<Task> tasks, SampleBinary binary) {
                tasks.create("custom\${binary.getName().capitalize()}Task")
            }
        }
    }
    apply plugin:MyBinaryTasks"""
    }
}
