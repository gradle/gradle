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
        when:
        buildFile << """
        class MyBinaryTasks implements Plugin<Project> {
            void apply(final Project project) {}

            @RuleSource
            static class Rules1 {
                @BinaryTasks
                void createSampleComponentComponents(CollectionBuilder<Task> tasks, SampleBinary binary) {
                    tasks.create("custom\${binary.getName().capitalize()}Task")
                }
            }
        }

        apply plugin:MyBinaryTasks
"""
        then:
        succeeds "customSampleLibBinaryTask"
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

                @BinaryType
                void registerOther(BinaryTypeBuilder<OtherSampleBinary> builder) {
                    builder.defaultImplementation(OtherSampleBinaryImpl)
                }

                @ComponentBinaries
                void createBinariesForSampleLibrary(CollectionBuilder<SampleBinary> binaries, SampleLibrary library) {
                    binaries.create("\${library.name}Binary", SampleBinary)
                    binaries.create("\${library.name}OtherBinary", OtherSampleBinary)
                }
            }
        }
        """
    }
}
