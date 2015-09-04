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
import org.gradle.util.TextUtil

class CustomBinaryIntegrationTest extends AbstractIntegrationSpec {
    def "setup"() {
        buildFile << """
interface SampleBinary extends BinarySpec {
    String getVersion()
    void setVersion(String version)
}
class DefaultSampleBinary extends BaseBinarySpec implements SampleBinary {
    String version
}
"""
    }

    def "custom binary type can be registered and created"() {
        when:
        buildWithCustomBinaryPlugin()
        and:
        buildFile << """
task checkModel << {
    assert project.binaries.size() == 1
    def sampleBinary = project.binaries.sampleBinary
    assert sampleBinary instanceof SampleBinary
    assert sampleBinary.displayName == "DefaultSampleBinary 'sampleBinary'"
}
"""
        then:
        succeeds "checkModel"
    }

    def "can configure binary defined by rule method using rule DSL"() {
        when:
        buildWithCustomBinaryPlugin()

        and:
        buildFile << """
task checkModel << {
    assert project.binaries.size() == 1
    def sampleBinary = project.binaries.sampleBinary
    assert sampleBinary instanceof SampleBinary
    assert sampleBinary.version == '1.2'
    assert sampleBinary.displayName == "DefaultSampleBinary 'sampleBinary'"
}

model {
    binaries {
        sampleBinary {
            version = '1.2'
        }
    }
}
"""
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
        buildFile << """
        class MySamplePlugin implements Plugin<Project> {
            void apply(final Project project) {}

            static class Rules extends RuleSource {
                @BinaryType
                void register(BinaryTypeBuilder<SampleBinary> builder) {
                    builder.defaultImplementation(DefaultSampleBinary)
                }
            }
        }

        apply plugin:MySamplePlugin

        task checkModel << {
            assert project.binaries.size() == 0
        }
"""

        then:
        succeeds "checkModel"
    }

    def "can have binary declaration and creation in separate plugins"() {
        when:
        buildFile << """
        class MyBinaryDeclarationModel implements Plugin<Project> {
            void apply(final Project project) {}

            static class Rules extends RuleSource {
                @BinaryType
                void register(BinaryTypeBuilder<SampleBinary> builder) {
                    builder.defaultImplementation(DefaultSampleBinary)
                }
            }
        }

        class MyBinaryCreationPlugin implements Plugin<Project> {
            void apply(final Project project) {
                project.apply(plugin:MyBinaryDeclarationModel)
            }

            static class Rules extends RuleSource {
                @Mutate
                void createSampleBinaries(ModelMap<SampleBinary> binaries) {
                    binaries.create("sampleBinary")
                }

            }
        }

        apply plugin:MyBinaryCreationPlugin

        task checkModel << {
            assert project.binaries.size() == 1
            def sampleBinary = project.binaries.sampleBinary
            assert sampleBinary instanceof SampleBinary
            assert sampleBinary.displayName == "DefaultSampleBinary 'sampleBinary'"
        }
"""
        then:
        succeeds "checkModel"
    }

    def "can define and create multiple binary types in the same plugin"() {
        when:
        buildFile << """
        interface AnotherSampleBinary extends BinarySpec {}
        class DefaultAnotherSampleBinary extends BaseBinarySpec implements AnotherSampleBinary {}

        class MySamplePlugin implements Plugin<Project> {
            void apply(final Project project) {}

            static class Rules extends RuleSource {
                @BinaryType
                void register(BinaryTypeBuilder<SampleBinary> builder) {
                    builder.defaultImplementation(DefaultSampleBinary)
                }

                @Mutate
                void createSampleBinaryInstances(ModelMap<SampleBinary> binaries) {
                    binaries.create("sampleBinary")
                }

                @BinaryType
                void registerAnother(BinaryTypeBuilder<AnotherSampleBinary> builder) {
                    builder.defaultImplementation(DefaultAnotherSampleBinary)
                }

                @Mutate
                void createAnotherSampleBinaryInstances(ModelMap<AnotherSampleBinary> anotherBinaries) {
                    anotherBinaries.create("anotherSampleBinary")
                }
            }
        }

        apply plugin:MySamplePlugin

        task checkModel << {
            assert project.binaries.size() == 2
            def sampleBinary = project.binaries.sampleBinary
            assert sampleBinary instanceof SampleBinary
            assert sampleBinary.displayName == "DefaultSampleBinary 'sampleBinary'"

            def anotherSampleBinary = project.binaries.anotherSampleBinary
            assert anotherSampleBinary instanceof AnotherSampleBinary
            assert anotherSampleBinary.displayName == "DefaultAnotherSampleBinary 'anotherSampleBinary'"
        }
"""
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
                @BinaryType
                void register(BinaryTypeBuilder<SampleBinary> builder, String illegalOtherParameter) {
                }
            }
        }

        apply plugin:MySamplePlugin
"""

        when:
        fails "tasks"

        then:
        failure.assertHasDescription "A problem occurred evaluating root project 'custom-binary'."
        failure.assertHasCause "Failed to apply plugin [class 'MySamplePlugin']"
        failure.assertHasCause "MySamplePlugin.Rules#register is not a valid binary model rule method."
        failure.assertHasCause "Method annotated with @BinaryType must have a single parameter of type 'org.gradle.platform.base.BinaryTypeBuilder'."
    }

    def "cannot register same binary type multiple times"() {
        given:
        buildWithCustomBinaryPlugin()
        and:
        buildFile << """
        class MyOtherPlugin implements Plugin<Project> {
            void apply(final Project project) {}

            static class Rules1 extends RuleSource {
                @BinaryType
                void register(BinaryTypeBuilder<SampleBinary> builder) {
                    builder.defaultImplementation(DefaultSampleBinary)
                }
            }
        }

        apply plugin:MyOtherPlugin
"""
        when:
        fails "tasks"
        then:
        failure.assertHasDescription "A problem occurred configuring root project 'custom-binary'."
        failure.assertHasCause "Exception thrown while executing model rule: MyOtherPlugin.Rules1#register"
        failure.assertHasCause "Cannot register a factory for type SampleBinary because a factory for this type was already registered by MySamplePlugin.Rules#register."
    }

    def "additional binaries listed in components report"() {
        given:
        buildWithCustomBinaryPlugin()
        when:
        succeeds "components"
        then:
        output.contains(TextUtil.toPlatformLineSeparators(""":components

------------------------------------------------------------
Root project
------------------------------------------------------------

No components defined for this project.

Additional binaries
-------------------
DefaultSampleBinary 'sampleBinary'
    build using task: :sampleBinary

Note: currently not all plugins register their components, so some components may not be visible here.

BUILD SUCCESSFUL"""))
    }

    def buildWithCustomBinaryPlugin() {
        settingsFile << """rootProject.name = 'custom-binary'"""
        buildFile << """
        class MySamplePlugin implements Plugin<Project> {
            void apply(final Project project) {}

            static class Rules extends RuleSource {
                @BinaryType
                void register(BinaryTypeBuilder<SampleBinary> builder) {
                    builder.defaultImplementation(DefaultSampleBinary)
                }

                @Mutate
                void createSampleBinary(ModelMap<SampleBinary> binarySpecs) {
                    println "creating binary"
                    binarySpecs.create("sampleBinary")
                }
            }
        }

        apply plugin:MySamplePlugin
        """
    }

}
