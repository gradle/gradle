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

package org.gradle.platform.base.plugins

import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.gradle.platform.base.ComponentSpec
import org.gradle.platform.base.PlatformBaseSpecification

class ComponentBasePluginTest extends PlatformBaseSpecification {
    def "can apply plugin by id"() {
        when:
        dsl {
            apply plugin: 'component-base'
        }

        then:
        project.pluginManager.pluginContainer.hasPlugin(ComponentBasePlugin)
    }

    def "applies lifecycle base plugin only"() {
        when:
        dsl {
            apply plugin: ComponentBasePlugin
        }

        then:
        project.pluginManager.pluginContainer.size() == 2
        project.pluginManager.pluginContainer.findPlugin(LifecycleBasePlugin) != null
    }

    def "registers ComponentSpec"() {
        when:
        dsl {
            apply plugin: ComponentBasePlugin
            model {
                baseComponent(ComponentSpec) {
                }
            }
        }

        then:
        realize("baseComponent") instanceof ComponentSpec
    }

    def "adds a 'components' container to the project model"() {
        when:
        dsl {
            apply plugin: ComponentBasePlugin
        }

        then:
        realizeComponents() != null
    }
}
