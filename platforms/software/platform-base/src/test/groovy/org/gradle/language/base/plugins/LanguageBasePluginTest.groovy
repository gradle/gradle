/*
 * Copyright 2015 the original author or authors.
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

import org.gradle.language.base.LanguageSourceSet
import org.gradle.platform.base.PlatformBaseSpecification
import org.gradle.platform.base.plugins.ComponentBasePlugin

class LanguageBasePluginTest extends PlatformBaseSpecification {
    def "applies component base plugin only"() {
        when:
        dsl {
            apply plugin: LanguageBasePlugin
        }

        then:
        project.pluginManager.pluginContainer.size() == 3
        project.pluginManager.pluginContainer.findPlugin(ComponentBasePlugin) != null
        project.pluginManager.pluginContainer.findPlugin(LifecycleBasePlugin) != null
    }

    def "registers LanguageSourceSet"() {
        when:
        dsl {
            apply plugin: LanguageBasePlugin
            model {
                baseSourceSet(LanguageSourceSet) {
                }
            }
        }

        then:
        realize("baseSourceSet") instanceof LanguageSourceSet
    }

    def "adds a 'sources' container to the project model"() {
        when:
        dsl {
            apply plugin: LanguageBasePlugin
        }

        then:
        realizeSourceSets() != null
    }
}
