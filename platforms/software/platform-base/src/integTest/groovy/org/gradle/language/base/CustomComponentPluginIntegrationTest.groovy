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
class CustomComponentPluginIntegrationTest extends AbstractIntegrationSpec implements StableConfigurationCacheDeprecations {
    def "setup"() {
        buildFile << """
@Managed
interface SampleComponent extends ComponentSpec {
    String getVersion()
    void setVersion(String version)
}
"""
    }

    def "plugin declares custom component"() {
        when:
        buildWithCustomComponentPlugin()

        and:
        buildFile << '''
model {
    tasks {
        create("checkModel") {
            def components = $.components
            doLast {
                assert components.size() == 1
                def sampleLib = components.sampleLib
                assert sampleLib instanceof SampleComponent
                assert sampleLib.projectPath == project.path
                assert sampleLib.displayName == "SampleComponent 'sampleLib'"
                assert sampleLib.version == null
            }
        }
    }
}
'''
        then:
        expectTaskGetProjectDeprecations()
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
                void register(TypeBuilder<SampleComponent> builder) {
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
        buildFile << '''
            class MySamplePlugin extends RuleSource {
                @ComponentType
                void register(TypeBuilder<SampleComponent> builder) {
                }
            }

            apply plugin:MySamplePlugin

            model {
                tasks {
                    create("checkModel") {
                        doLast {
                            assert $.components.size() == 0
                        }
                    }
                }
            }
'''

        then:
        succeeds "checkModel"
    }

    def "custom component listed in components report"() {
        given:
        buildWithCustomComponentPlugin()

        when:
        executer.withArgument("--no-problems-report")
        expectTaskGetProjectDeprecations()
        succeeds "components"

        then:
        output.contains """
------------------------------------------------------------
Root project 'custom-component'
------------------------------------------------------------

SampleComponent 'sampleLib'
---------------------------

Note: currently not all plugins register their components, so some components may not be visible here.

BUILD SUCCESSFUL"""
    }

    def "can have component declaration and creation in separate plugins"() {
        when:
        buildFile << '''
            class MyComponentDeclarationModel extends RuleSource {
                @ComponentType
                void register(TypeBuilder<SampleComponent> builder) {
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
                        def components = $.components
                        doLast {
                            assert components.size() == 1
                            def sampleLib = components.sampleLib
                            assert sampleLib instanceof SampleComponent
                            assert sampleLib.projectPath == project.path
                            assert sampleLib.displayName == "SampleComponent 'sampleLib'"
                        }
                    }
                }
            }
'''

        then:
        expectTaskGetProjectDeprecations()
        succeeds "checkModel"
    }

    def "Can define and create multiple component types in the same plugin"(){
        when:
        buildFile << '''
            interface SampleLibrary extends LibrarySpec {}
            class DefaultSampleLibrary extends BaseComponentSpec implements SampleLibrary {}

            class MySamplePlugin extends RuleSource {
                @ComponentType
                void register(TypeBuilder<SampleComponent> builder) {
                }

                @ComponentType
                void registerAnother(TypeBuilder<SampleLibrary> builder) {
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
                        def components = $.components
                        doLast {
                            assert components.size() == 2

                            def sampleComponent = components.sampleComponent
                            assert sampleComponent instanceof SampleComponent
                            assert sampleComponent.projectPath == project.path
                            assert sampleComponent.displayName == "SampleComponent 'sampleComponent'"

                            def sampleLib = components.sampleLib
                            assert sampleLib instanceof SampleLibrary
                            assert sampleLib.projectPath == project.path
                            assert sampleLib.displayName == "SampleLibrary 'sampleLib'"
                        }
                    }
                }
            }
'''

        then:
        expectTaskGetProjectDeprecations(2)
        succeeds "checkModel"
    }

    def "reports failure for invalid component type method"() {
        given:
        settingsFile << """rootProject.name = 'custom-component'"""
        buildFile << """
            class MySamplePlugin extends RuleSource {
                @ComponentType
                void register(TypeBuilder<SampleComponent> builder, String illegalOtherParameter) {
                }
            }

            apply plugin:MySamplePlugin
"""

        when:
        fails "tasks"

        then:
        failure.assertHasDescription "A problem occurred evaluating root project 'custom-component'."
        failure.assertHasCause "Failed to apply plugin class 'MySamplePlugin'"
        failure.assertHasCause '''Type MySamplePlugin is not a valid rule source:
- Method register(org.gradle.platform.base.TypeBuilder<SampleComponent>, java.lang.String) is not a valid rule method: A method annotated with @ComponentType must have a single parameter of type org.gradle.platform.base.TypeBuilder.'''
    }

    def "cannot register same unmanaged component type implementation multiple times"(){
        given:
        buildWithCustomComponentPlugin()

        and:
        buildFile << """
            interface UnmanagedComponent extends ComponentSpec {}
            class DefaultUnmanagedComponent extends BaseComponentSpec implements UnmanagedComponent {}
            class MyPlugin extends RuleSource {
                @ComponentType
                void register(TypeBuilder<UnmanagedComponent> builder) {
                    builder.defaultImplementation(DefaultUnmanagedComponent)
                }
            }
            class MyOtherPlugin extends RuleSource {
                @ComponentType
                void register(TypeBuilder<UnmanagedComponent> builder) {
                    builder.defaultImplementation(DefaultUnmanagedComponent)
                }
            }

            apply plugin:MyPlugin
            apply plugin:MyOtherPlugin
"""

        when:
        expectTaskGetProjectDeprecations()
        fails "model"

        then:
        failure.assertHasDescription "Execution failed for task ':model'."
        failure.assertHasCause "Exception thrown while executing model rule: MyOtherPlugin#register"
        failure.assertHasCause "Cannot register implementation for type 'UnmanagedComponent' because an implementation for this type was already registered by MyPlugin#register"
    }

    def buildWithCustomComponentPlugin() {
        settingsFile << """rootProject.name = 'custom-component'"""
        buildFile << """
            class MySamplePlugin extends RuleSource {
                @ComponentType
                void register(TypeBuilder<SampleComponent> builder) {
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
