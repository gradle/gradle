/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.language.base.plugins

import org.gradle.platform.base.*
import org.gradle.platform.base.plugins.BinaryBasePlugin
import org.gradle.platform.base.plugins.ComponentBasePlugin

class ComponentModelBasePluginTest extends PlatformBaseSpecification {
    def "applies language and binary base plugins"() {
        when:
        dsl {
            apply plugin: ComponentModelBasePlugin
        }

        then:
        project.pluginManager.pluginContainer.size() == 5
        project.pluginManager.pluginContainer.findPlugin(ComponentBasePlugin) != null
        project.pluginManager.pluginContainer.findPlugin(BinaryBasePlugin) != null
        project.pluginManager.pluginContainer.findPlugin(LanguageBasePlugin) != null
        project.pluginManager.pluginContainer.findPlugin(LifecycleBasePlugin) != null
    }

    def "registers base types"() {
        when:
        dsl {
            apply plugin: ComponentModelBasePlugin
            model {
                baseComponent(type) {
                }
            }
        }

        then:
        type.isInstance(realize("baseComponent"))

        where:
        type                 | _
        GeneralComponentSpec | _
        ApplicationSpec      | _
        LibrarySpec          | _
    }

    def "links the binaries of each component in 'components' container into the 'binaries' container"() {
        when:
        dsl {
            apply plugin: ComponentModelBasePlugin
            model {
                components {
                    comp1(GeneralComponentSpec) {
                        binaries {
                            bin1(BinarySpec)
                            bin2(BinarySpec)
                        }
                    }
                    comp2(ComponentSpec)
                }
            }
        }

        then:
        def binaries = realizeBinaries()
        def components = realizeComponents()
        binaries.size() == 2
        binaries.comp1Bin1 == components.comp1.binaries.bin1
        binaries.comp1Bin2 == components.comp1.binaries.bin2
    }

    def "links the tasks of each component in 'components' container into the 'tasks' container"() {
        when:
        dsl {
            apply plugin: ComponentModelBasePlugin
            model {
                components {
                    comp1(GeneralComponentSpec) {
                        binaries {
                            bin1(BinarySpec)
                            bin2(BinarySpec)
                        }
                    }
                    comp2(ComponentSpec)
                }
            }
        }

        then:
        def tasks = realizeTasks()
        def components = realizeComponents()
        tasks.comp1Bin1 == components.comp1.binaries.bin1.tasks.build
        tasks.comp1Bin2 == components.comp1.binaries.bin2.tasks.build
    }
}
