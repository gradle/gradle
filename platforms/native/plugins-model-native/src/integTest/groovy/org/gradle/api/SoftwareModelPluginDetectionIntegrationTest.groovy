/*
 * Copyright 2026 the original author or authors.
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
package org.gradle.api

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class SoftwareModelPluginDetectionIntegrationTest extends AbstractIntegrationSpec {

    def "plugin manager with id is fired after the plugin is applied for hybrid plugins"() {
        when:
        file("buildSrc/src/main/groovy/MyPlugin.groovy") << """
            import org.gradle.api.Plugin
            import org.gradle.api.Task
            import org.gradle.model.*

            class MyPlugin implements Plugin {
                void apply(project) {
                  project.tasks.create("imperative-sentinel")
                }

                static class Rules extends RuleSource {
                    @Model String thing() {
                        "foo"
                    }
                }
            }
        """

        file("buildSrc/src/main/resources/META-INF/gradle-plugins/my.properties") << "implementation-class=MyPlugin"

        buildFile """
            import org.gradle.model.internal.core.ModelPath

            pluginManager.withPlugin("my") {
              assert tasks."imperative-sentinel"
              // note: modelRegistry property is internal on project
              assert modelRegistry.node(ModelPath.path("thing")) != null
            }

            pluginManager.apply(MyPlugin)
        """

        then:
        succeeds "help"
    }

    def "plugin manager with id is fired after the plugin is applied for rule plugins"() {
        when:
        file("buildSrc/src/main/groovy/MyPlugin.groovy") << """
            import org.gradle.model.*

            class Rules extends RuleSource {
                @Model String thing() {
                    "foo"
                }
            }
        """

        file("buildSrc/src/main/resources/META-INF/gradle-plugins/my.properties") << "implementation-class=Rules"

        buildFile """
            import org.gradle.model.internal.core.ModelPath

            pluginManager.withPlugin("my") {
              // note: modelRegistry property is internal on project
              assert modelRegistry.node(ModelPath.path("thing")) != null
            }

            pluginManager.apply("my")
        """

        then:
        succeeds "help"
    }
}
