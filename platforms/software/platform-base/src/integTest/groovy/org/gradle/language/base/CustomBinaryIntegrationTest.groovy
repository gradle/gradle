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
import org.gradle.integtests.fixtures.StableConfigurationCacheDeprecations
import org.gradle.integtests.fixtures.UnsupportedWithConfigurationCache

@UnsupportedWithConfigurationCache(because = "software model")
class CustomBinaryIntegrationTest extends AbstractIntegrationSpec implements StableConfigurationCacheDeprecations {
    def "setup"() {
        buildFile << """
@Managed interface SampleBinary extends BinarySpec {
    String getVersion()
    void setVersion(String version)
}
"""
    }

    def "custom binary type can be registered and created"() {
        when:
        buildWithCustomBinaryPlugin()

        and:
        buildFile << '''
model {
    tasks {
        checkModel(Task) {
            doLast {
                def binaries = $.binaries
                assert binaries.size() == 1
                def sampleBinary = binaries.sampleBinary
                assert sampleBinary instanceof SampleBinary
                assert sampleBinary.name == "sampleBinary"
                assert sampleBinary.displayName == "SampleBinary 'sampleBinary'"
                assert sampleBinary.toString() == "SampleBinary 'sampleBinary'"
            }
        }
    }
}
'''
        then:
        succeeds "checkModel"
    }

    def "custom binary type can viewed as ModelElement"() {
        when:
        buildWithCustomBinaryPlugin()

        and:
        buildFile << '''
            class Rules extends RuleSource {
                @Mutate
                void tasks(ModelMap<Task> tasks, @Path("binaries.sampleBinary") ModelElement binary) {
                    tasks.create("checkModel") {
                        doLast {
                            assert binary.name == "sampleBinary"
                            assert binary.displayName == "SampleBinary 'sampleBinary'"
                            assert binary.toString() == binary.displayName
                        }
                    }
                }
            }
            apply plugin: Rules
'''

        then:
        succeeds "checkModel"
    }

    def "can configure binary defined by rule method using rule DSL"() {
        when:
        buildWithCustomBinaryPlugin()

        and:
        buildFile << '''
model {
    tasks {
        checkModel(Task) {
            doLast {
                def binaries = $.binaries
                assert binaries.size() == 1
                def sampleBinary = binaries.sampleBinary
                assert sampleBinary instanceof SampleBinary
                assert sampleBinary.version == '1.2'
                assert sampleBinary.displayName == "SampleBinary 'sampleBinary'"
            }
        }
    }
}

model {
    binaries {
        sampleBinary {
            version = '1.2'
        }
    }
}
'''
        then:
        succeeds "checkModel"
    }

    def "creates lifecycle task per binary"() {
        when:
        buildWithCustomBinaryPlugin()
        then:
        succeeds "sampleBinary"
    }

    def "can register custom binary model without creating"() {
        when:
        buildFile << '''
        class MySamplePlugin implements Plugin<Project> {
            void apply(final Project project) {}

            static class Rules extends RuleSource {
                @ComponentType
                void register(TypeBuilder<SampleBinary> builder) {
                }
            }
        }

        apply plugin:MySamplePlugin

        model {
            tasks {
                checkModel(Task) {
                    doLast {
                        def binaries = $.binaries
                        assert binaries.size() == 0
                    }
                }
            }
        }
'''

        then:
        succeeds "checkModel"
    }

    def "can have binary declaration and creation in separate plugins"() {
        when:
        buildFile << '''
        class MyBinaryDeclarationModel implements Plugin<Project> {
            void apply(final Project project) {}

            static class Rules extends RuleSource {
                @ComponentType
                void register(TypeBuilder<SampleBinary> builder) {
                }
            }
        }

        class MyBinaryCreationPlugin implements Plugin<Project> {
            void apply(final Project project) {
                project.apply(plugin:MyBinaryDeclarationModel)
            }

            static class Rules extends RuleSource {
                @Mutate
                void createSampleBinaries(BinaryContainer binaries) {
                    binaries.create("sampleBinary", SampleBinary)
                }

            }
        }

        apply plugin:MyBinaryCreationPlugin

        model {
            tasks {
                checkModel(Task) {
                    doLast {
                        def binaries = $.binaries
                        assert binaries.size() == 1
                        def sampleBinary = binaries.sampleBinary
                        assert sampleBinary instanceof SampleBinary
                        assert sampleBinary.displayName == "SampleBinary 'sampleBinary'"
                    }
                }
            }
        }
'''
        then:
        succeeds "checkModel"
    }

    def "can define and create multiple binary types in the same plugin"() {
        when:
        buildFile << '''
        @Managed interface AnotherSampleBinary extends BinarySpec {}

        class MySamplePlugin implements Plugin<Project> {
            void apply(final Project project) {}

            static class Rules extends RuleSource {
                @ComponentType
                void register(TypeBuilder<SampleBinary> builder) {
                }

                @Mutate
                void createSampleBinaryInstances(BinaryContainer binaries) {
                    binaries.create("sampleBinary", SampleBinary)
                }

                @ComponentType
                void registerAnother(TypeBuilder<AnotherSampleBinary> builder) {}

                @Mutate
                    void createAnotherSampleBinaryInstances(BinaryContainer anotherBinaries) {
                    anotherBinaries.create("anotherSampleBinary", AnotherSampleBinary)
                }
            }
        }

        apply plugin:MySamplePlugin

        model {
            tasks {
                checkModel(Task) {
                    doLast {
                        def binaries = $.binaries
                        assert binaries.size() == 2
                        def sampleBinary = binaries.sampleBinary
                        assert sampleBinary instanceof SampleBinary
                        assert sampleBinary.displayName == "SampleBinary 'sampleBinary'"

                        def anotherSampleBinary = binaries.anotherSampleBinary
                        assert anotherSampleBinary instanceof AnotherSampleBinary
                        assert anotherSampleBinary.displayName == "AnotherSampleBinary 'anotherSampleBinary'"
                    }
                }
            }
        }
'''
        then:
        succeeds "checkModel"
    }

    def "reports failure for invalid binary type method"() {
        given:
        settingsFile << """rootProject.name = 'custom-binary'"""
        buildFile << """
        class MySamplePlugin implements Plugin<Project> {
            void apply(final Project project) {}

            static class Rules extends RuleSource {
                @ComponentType
                void register(TypeBuilder<SampleBinary> builder, String illegalOtherParameter) {
                }
            }
        }

        apply plugin:MySamplePlugin
"""

        when:
        fails "tasks"

        then:
        failure.assertHasDescription "A problem occurred evaluating root project 'custom-binary'."
        failure.assertHasCause "Failed to apply plugin class 'MySamplePlugin'"
        failure.assertHasCause '''Type MySamplePlugin.Rules is not a valid rule source:
- Method register(org.gradle.platform.base.TypeBuilder<SampleBinary>, java.lang.String) is not a valid rule method: A method annotated with @ComponentType must have a single parameter of type org.gradle.platform.base.TypeBuilder.'''
    }

    def "cannot register implementation for the same binary type multiple times"() {
        given:
        settingsFile << """rootProject.name = 'custom-binary'"""
        buildFile << """
        interface SomeBinary extends BinarySpec {}
        class DefaultSomeBinary extends BaseBinarySpec implements SomeBinary {}
        class Rules1 extends RuleSource {
            @ComponentType
            void register(TypeBuilder<SomeBinary> builder) {
                builder.defaultImplementation(DefaultSomeBinary)
            }
        }
        class Rules2 extends RuleSource {
            @ComponentType
            void register(TypeBuilder<SomeBinary> builder) {
                builder.defaultImplementation(DefaultSomeBinary)
            }
        }

        apply plugin:Rules1
        apply plugin:Rules2
"""
        when:
        fails "tasks"

        then:
        failure.assertHasDescription "A problem occurred configuring root project 'custom-binary'."
        failure.assertHasCause "Exception thrown while executing model rule: Rules2#register"
        failure.assertHasCause "Cannot register implementation for type 'SomeBinary' because an implementation for this type was already registered by Rules1#register"
    }

    def "additional binaries listed in components report"() {
        given:
        buildWithCustomBinaryPlugin()
        when:
        executer.withArgument("--no-problems-report")
        expectTaskGetProjectDeprecations()
        succeeds "components"
        then:
        output.contains """
------------------------------------------------------------
Root project 'custom-binary'
------------------------------------------------------------

No components defined for this project.

Additional binaries
-------------------
SampleBinary 'sampleBinary'
    build using task: :sampleBinary

Note: currently not all plugins register their components, so some components may not be visible here.

BUILD SUCCESSFUL"""
    }

    def buildWithCustomBinaryPlugin() {
        settingsFile << """rootProject.name = 'custom-binary'"""
        buildFile << """
        class MySamplePlugin implements Plugin<Project> {
            void apply(final Project project) {}

            static class Rules extends RuleSource {
                @ComponentType
                void register(TypeBuilder<SampleBinary> builder) {
                }

                @Mutate
                void createSampleBinary(BinaryContainer binarySpecs) {
                    println "creating binary"
                    binarySpecs.create("sampleBinary", SampleBinary)
                }
            }
        }

        apply plugin:MySamplePlugin
        """
    }

}
