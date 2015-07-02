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
import org.gradle.integtests.fixtures.EnableModelDsl
import org.gradle.util.TextUtil

class CustomComponentPluginIntegrationTest extends AbstractIntegrationSpec {
    def "setup"() {
        EnableModelDsl.enable(executer)
        buildFile << """
interface SampleComponent extends ComponentSpec {
    String getVersion()
    void setVersion(String version)
}
class DefaultSampleComponent extends BaseComponentSpec implements SampleComponent {
    String version
}
"""
    }

    def "plugin declares custom component"() {
        when:
        buildWithCustomComponentPlugin()

        and:
        buildFile << """
model {
    tasks {
        create("checkModel") {
            def components = \$("components")
            doLast {
                assert components.size() == 1
                def sampleLib = components.sampleLib
                assert sampleLib instanceof SampleComponent
                assert sampleLib.projectPath == project.path
                assert sampleLib.displayName == "DefaultSampleComponent 'sampleLib'"
                assert sampleLib.version == null
            }
        }
    }
}
"""
        then:
        succeeds "checkModel"
    }

    def "can configure component declared by model rule method using model rules DSL"() {
        when:
        buildWithCustomComponentPlugin()

        and:
        buildFile << """
model {
    components {
        sampleLib {
            version = '12'
        }
    }
    tasks {
        create("checkModel") {
            doLast {
                assert \$("components").sampleLib.version == '12'
            }
        }
    }
}
"""

        then:
        succeeds "checkModel"
    }

    def "can configure component declared by model rule DSL using model rule method"() {
        when:
        buildFile << """
            class MySamplePlugin extends RuleSource {
                @ComponentType
                void register(ComponentTypeBuilder<SampleComponent> builder) {
                    builder.defaultImplementation(DefaultSampleComponent)
                }

                @Mutate
                void createSampleComponentComponents(ModelMap<SampleComponent> componentSpecs) {
                    componentSpecs.afterEach {
                        version += ".1"
                    }
                }
            }

            apply plugin:MySamplePlugin

            model {
                components {
                    sampleLib(SampleComponent) {
                        version = '12'
                    }
                }
                tasks {
                    create("checkModel") {
                        doLast {
                            assert \$("components").sampleLib.version == '12.1'
                        }
                    }
                }
            }
"""

        then:
        succeeds "checkModel"
    }

    def "can register custom component model without creating"() {
        when:
        buildFile << """
            class MySamplePlugin extends RuleSource {
                @ComponentType
                void register(ComponentTypeBuilder<SampleComponent> builder) {
                    builder.defaultImplementation(DefaultSampleComponent)
                }
            }

            apply plugin:MySamplePlugin

            model {
                tasks {
                    create("checkModel") {
                        doLast {
                            assert \$("components").size() == 0
                        }
                    }
                }
            }
"""

        then:
        succeeds "checkModel"
    }

    def "custom component listed in components report"() {
        given:
        buildWithCustomComponentPlugin()

        when:
        succeeds "components"

        then:
        output.contains(TextUtil.toPlatformLineSeparators(""":components

------------------------------------------------------------
Root project
------------------------------------------------------------

DefaultSampleComponent 'sampleLib'
----------------------------------

Source sets
    No source sets.

Binaries
    No binaries.

Note: currently not all plugins register their components, so some components may not be visible here.

BUILD SUCCESSFUL"""))
    }

    def "can have component declaration and creation in separate plugins"() {
        when:
        buildFile << """
            class MyComponentDeclarationModel extends RuleSource {
                @ComponentType
                void register(ComponentTypeBuilder<SampleComponent> builder) {
                    builder.defaultImplementation(DefaultSampleComponent)
                }
            }

            class MyComponentCreationPlugin implements Plugin<Project> {
                void apply(final Project project) {
                    project.apply(plugin:MyComponentDeclarationModel)
                }

                static class Rules extends RuleSource {
                    @Mutate
                    void createSampleComponentComponents(ModelMap<SampleComponent> componentSpecs) {
                        componentSpecs.create("sampleLib")
                    }
                }
            }

            apply plugin:MyComponentCreationPlugin

            model {
                tasks {
                    create("checkModel") {
                        def components = \$("components")
                        doLast {
                            assert components.size() == 1
                            def sampleLib = components.sampleLib
                            assert sampleLib instanceof SampleComponent
                            assert sampleLib.projectPath == project.path
                            assert sampleLib.displayName == "DefaultSampleComponent 'sampleLib'"
                        }
                    }
                }
            }
"""

        then:
        succeeds "checkModel"
    }

    def "Can define and create multiple component types in the same plugin"(){
        when:
        buildFile << """
            interface SampleLibrary extends LibrarySpec {}
            class DefaultSampleLibrary extends BaseComponentSpec implements SampleLibrary {}

            class MySamplePlugin extends RuleSource {
                @ComponentType
                void register(ComponentTypeBuilder<SampleComponent> builder) {
                    builder.defaultImplementation(DefaultSampleComponent)
                }

                @ComponentType
                void registerAnother(ComponentTypeBuilder<SampleLibrary> builder) {
                    builder.defaultImplementation(DefaultSampleLibrary)
                }

                @Mutate
                void createSampleComponentInstances(ModelMap<SampleComponent> componentSpecs) {
                    componentSpecs.create("sampleComponent")
                }

                @Mutate
                void createSampleLibraryInstances(ModelMap<SampleLibrary> componentSpecs) {
                    componentSpecs.create("sampleLib")
                }
            }

            apply plugin:MySamplePlugin

            model {
                tasks {
                    create("checkModel") {
                        def components = \$("components")
                        doLast {
                            assert components.size() == 2

                            def sampleComponent = components.sampleComponent
                            assert sampleComponent instanceof SampleComponent
                            assert sampleComponent.projectPath == project.path
                            assert sampleComponent.displayName == "DefaultSampleComponent 'sampleComponent'"

                            def sampleLib = components.sampleLib
                            assert sampleLib instanceof SampleLibrary
                            assert sampleLib.projectPath == project.path
                            assert sampleLib.displayName == "DefaultSampleLibrary 'sampleLib'"
                        }
                    }
                }
            }
"""

        then:
        succeeds "checkModel"
    }

    def "reports failure for invalid component type method"() {
        given:
        settingsFile << """rootProject.name = 'custom-component'"""
        buildFile << """
            class MySamplePlugin extends RuleSource {
                @ComponentType
                void register(ComponentTypeBuilder<SampleComponent> builder, String illegalOtherParameter) {
                }
            }

            apply plugin:MySamplePlugin
"""

        when:
        fails "tasks"

        then:
        failure.assertHasDescription "A problem occurred evaluating root project 'custom-component'."
        failure.assertHasCause "Failed to apply plugin [class 'MySamplePlugin']"
        failure.assertHasCause "MySamplePlugin#register is not a valid component model rule method."
        failure.assertHasCause "Method annotated with @ComponentType must have a single parameter of type 'org.gradle.platform.base.ComponentTypeBuilder'."
    }

    def "cannot register same component type multiple times"(){
        given:
        buildWithCustomComponentPlugin()

        and:
        buildFile << """
            class MyOtherPlugin extends RuleSource {
                @ComponentType
                void register(ComponentTypeBuilder<SampleComponent> builder) {
                    builder.defaultImplementation(DefaultSampleComponent)
                }
            }

            apply plugin:MyOtherPlugin
"""

        when:
        fails "tasks"

        then:
        failure.assertHasDescription "A problem occurred configuring root project 'custom-component'."
        failure.assertHasCause "Exception thrown while executing model rule: MyOtherPlugin#register"
        failure.assertHasCause "Cannot register a factory for type SampleComponent because a factory for this type was already registered by MySamplePlugin#register."
    }

    def buildWithCustomComponentPlugin() {
        settingsFile << """rootProject.name = 'custom-component'"""
        buildFile << """
            class MySamplePlugin extends RuleSource {
                @ComponentType
                void register(ComponentTypeBuilder<SampleComponent> builder) {
                    builder.defaultImplementation(DefaultSampleComponent)
                }
                @Mutate
                void createSampleComponentComponents(ModelMap<SampleComponent> componentSpecs) {
                    componentSpecs.create("sampleLib")
                }
            }

            apply plugin:MySamplePlugin
        """
    }
}
