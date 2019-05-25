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
            import ${ListProperty.name}
            import ${SetProperty.name}
            import ${MapProperty.name}
            import ${ObjectFactory.name}
            import ${TaskAction.name}
            import ${Inject.name}

            public class SomeTask extends DefaultTask {
                final Property<Boolean> flag
                final Property<String> message
                final ListProperty<Integer> list
                final SetProperty<Integer> set
                final MapProperty<Integer, Boolean> map
                
                @Inject
                SomeTask(ObjectFactory objectFactory) {
                    flag = objectFactory.property(Boolean)
                    message = objectFactory.property(String)
                    list = objectFactory.listProperty(Integer) 
                    set = objectFactory.setProperty(Integer) 
                    map = objectFactory.mapProperty(Integer, Boolean) 
                }
                
                @TaskAction
                void run() {
                    System.out.println("flag = " + flag.get())
                    System.out.println("message = " + message.get())
                    System.out.println("list = " + list.get())
                    System.out.println("set = " + set.get())
                    System.out.println("map = " + map.get().toString())
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
                        t.list = [1, 2]
                        t.set = [1, 2]
                        t.map = [1: true, 2: false]
                    }
                }
            }
        """
    }

    @Override
    void pluginSetsCalculatedValuesUsingCallable() {
        pluginDir.file("src/main/groovy/SomePlugin.groovy") << """
            import ${Project.name}
            import ${Plugin.name}

            public class SomePlugin implements Plugin<Project> {
                void apply(Project project) {
                    project.tasks.register("someTask", SomeTask) { t ->
                        t.flag = project.provider { true }
                        t.message = project.provider { "some value" }
                        t.list = project.provider { [1, 2] }
                        t.set = project.provider { [1, 2] }
                        t.map = project.provider { [1: true, 2: false] }
                    }
                }
            }
        """
    }

    @Override
    void pluginSetsCalculatedValuesUsingMappedProvider() {
        pluginDir.file("src/main/groovy/SomePlugin.groovy") << """
            import ${Project.name}
            import ${Plugin.name}

            public class SomePlugin implements Plugin<Project> {
                void apply(Project project) {
                    project.tasks.register("someTask", SomeTask) { t ->
                        def provider = project.provider { "some value" }
                        t.flag = provider.map { s -> !s.empty }
                        t.message = provider.map { s -> s }
                        t.list = provider.map { s -> [1, 2] }
                        t.set = provider.map { s -> [1, 2] }
                        t.map = provider.map { s -> [1: true, 2: false] }
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
