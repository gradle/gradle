/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.instantexecution


import org.gradle.api.Plugin
import org.gradle.api.Project

class InstantExecutionUndeclaredBuildInputsDynamicGroovyBuildSrcIntegrationTest extends AbstractInstantExecutionUndeclaredBuildInputsIntegrationTest {
    @Override
    void pluginDefinition() {
        file("buildSrc/src/main/groovy/SneakyPlugin.groovy") << """
            import ${Project.name}
            import ${Plugin.name}

            class SneakyPlugin implements Plugin<Project> {
                public void apply(Project project) {
                    def ci = System.getProperty("CI")
                    println("apply CI = " + ci)
                    project.tasks.register("thing") { t ->
                        t.doLast {
                            def ci2 = System.getProperty("CI")
                            println("task CI = " + ci2)
                        }
                    }
                }
            }
        """
    }
}
