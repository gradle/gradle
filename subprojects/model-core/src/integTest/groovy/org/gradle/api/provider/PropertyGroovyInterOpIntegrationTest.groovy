/*
 * Copyright 2018 the original author or authors.
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
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.TaskAction

import javax.inject.Inject

class PropertyGroovyInterOpIntegrationTest extends AbstractPropertyLanguageInterOpIntegrationTest {
    def setup() {
        pluginDir.file("build.gradle") << """
            plugins { 
                id("groovy")
            }
            dependencies {
                compile gradleApi()
                compile localGroovy()
            }
        """
        pluginDir.file("src/main/groovy/SomeTask.groovy") << """
            import ${DefaultTask.name}
            import ${Property.name}
            import ${ObjectFactory.name}
            import ${TaskAction.name}
            import ${Inject.name}

            public class SomeTask extends DefaultTask {
                final Property<Boolean> flag
                final Property<String> message
                
                @Inject
                SomeTask(ObjectFactory objectFactory) {
                    flag = objectFactory.property(Boolean)
                    message = objectFactory.property(String)
                }
                
                @TaskAction
                void run() {
                    System.out.println("flag = " + flag.get())
                    System.out.println("message = " + message.get())
                }
            }
        """
    }

    @Override
    void pluginSetsValues() {
        pluginDir.file("src/main/groovy/SomePlugin.groovy") << """
            import ${Project.name}
            import ${Plugin.name}

            public class SomePlugin implements Plugin<Project> {
                void apply(Project project) {
                    project.tasks.register("someTask", SomeTask) { t ->
                        t.flag = true
                        t.message = "some value"
                    }
                }
            }
        """
    }

    @Override
    void pluginSetsCalculatedValues() {
        pluginDir.file("src/main/groovy/SomePlugin.groovy") << """
            import ${Project.name}
            import ${Plugin.name}

            public class SomePlugin implements Plugin<Project> {
                void apply(Project project) {
                    project.tasks.register("someTask", SomeTask) { t ->
                        t.flag = project.provider { true }
                        t.message = project.provider { "some value" }
                    }
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
