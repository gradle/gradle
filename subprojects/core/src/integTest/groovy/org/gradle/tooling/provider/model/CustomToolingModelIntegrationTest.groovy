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

package org.gradle.tooling.provider.model

import org.gradle.api.tasks.TasksWithInputsAndOutputs
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.UnsupportedWithConfigurationCache

import javax.inject.Inject

class CustomToolingModelIntegrationTest extends AbstractIntegrationSpec implements TasksWithInputsAndOutputs {
    // This is not an intended usage, but the Android plugin currently does this
    @UnsupportedWithConfigurationCache(because = "Tooling model builders require access to the project state, and this is not available when loading from cache")
    def "task that use a custom tooling model can run concurrently"() {
        createDirs("a", "b", "c")
        settingsFile << """
            include 'a', 'b', 'c'
        """
        taskTypeWithOutputFileProperty()
        buildFile << """
            import ${Inject.name}
            import ${ToolingModelBuilderRegistry.name}
            import ${ToolingModelBuilder.name}

            abstract class SomePlugin implements Plugin<Project> {
                @Inject
                abstract ToolingModelBuilderRegistry getRegistry();

                void apply(Project project) {
                    registry.register(new SomeModelBuilder())
                    project.tasks.register("model1", SomeTask)
                    project.tasks.register("model2", SomeTask)
                    project.configurations.create("classpath")
                }
            }

            class SomeModelBuilder implements ToolingModelBuilder {
                static final Object lock = new Object()

                boolean canBuild(String modelName) {
                    return modelName == "thing"
                }

                Object buildAll(String modelName, Project project) {
                    // Do some dependency resolution while holding a shared lock
                    synchronized(lock) {
                        project.configurations.classpath.files.forEach { }
                    }
                    return "model"
                }
            }

            abstract class SomeTask extends DefaultTask {
                @Inject
                abstract ToolingModelBuilderRegistry getRegistry();

                @TaskAction
                void run() {
                    registry.getBuilder("thing").buildAll("thing", project)
                }
            }

            project(':a') {
                apply plugin: SomePlugin
                dependencies { classpath project(':c') }
            }
            project(':b') {
                apply plugin: SomePlugin
                dependencies { classpath project(':c') }
            }
            project(':c') {
                project.configurations.create("default") {
                    outgoing.artifact(layout.projectDirectory.file("thing.bin"))
                }
            }
        """

        when:
        run("model1", "model2", "--parallel")

        then:
        noExceptionThrown()
    }
}
