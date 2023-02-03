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

package org.gradle.api.provider

import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.TaskAction

class NestedBeanGroovyInterOpIntegrationTest extends AbstractNestedBeanLanguageInterOpIntegrationTest {
    def setup() {
        pluginDir.file("build.gradle") << """
            plugins {
                id("groovy")
            }
            dependencies {
                implementation gradleApi()
                implementation localGroovy()
            }
        """
        pluginDir.file("src/main/groovy/Params.groovy") << """
            import ${Property.name}
            import ${Internal.name}

            interface Params {
                @Internal
                Property<Boolean> getFlag()
            }
        """
        pluginDir.file("src/main/groovy/SomeTask.groovy") << """
            import ${DefaultTask.name}
            import ${TaskAction.name}
            import ${Nested.name}

            public abstract class SomeTask extends DefaultTask {
                @Nested
                abstract Params getParams()

                @TaskAction
                void run() {
                    System.out.println("flag = " + params.flag.get())
                }
            }
        """
    }

    @Override
    void pluginDefinesTask() {
        pluginDir.file("src/main/groovy/SomePlugin.groovy") << """
            import ${Project.name}
            import ${Plugin.name}

            public class SomePlugin implements Plugin<Project> {
                void apply(Project project) {
                    project.tasks.register("someTask", SomeTask)
                }
            }
        """
    }
}
