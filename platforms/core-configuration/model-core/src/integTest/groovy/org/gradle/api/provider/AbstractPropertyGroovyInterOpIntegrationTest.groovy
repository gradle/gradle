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


import org.gradle.api.Plugin
import org.gradle.api.Project

abstract class AbstractPropertyGroovyInterOpIntegrationTest extends AbstractPropertyLanguageInterOpIntegrationTest {
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
                        t.number = 1.23d
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
                        t.number = project.provider { 1.23d }
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
                        t.number = provider.map { s -> 1.23d }
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
