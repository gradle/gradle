/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.integtests.tooling.r89

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.integtests.tooling.fixture.CompositeModel
import org.gradle.integtests.tooling.fixture.DeepChildModel
import org.gradle.integtests.tooling.fixture.ShallowChildModel
import org.gradle.integtests.tooling.fixture.SideModel
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.integtests.tooling.fixture.BaseModel
import org.gradle.integtests.tooling.fixture.VeryDeepChildModel
import org.gradle.tooling.provider.model.ToolingModelBuilder
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry

import javax.inject.Inject

@ToolingApiVersion(">=8.9")
@TargetGradleVersion('>=6.4') // that's when support for precompiled script plugins in Groovy has been added (used in this test)
class ToolingApiPolymorphismCrossVersionTest extends ToolingApiSpecification {

    def "setup"() {
        file("plugins/build.gradle") << """
            plugins {
                id("groovy-gradle-plugin")
            }
            gradlePlugin {
                plugins {
                    test {
                        id = "my.plugin"
                        implementationClass = "my.MyPlugin"
                    }
                }
            }
        """

        file("plugins/src/main/groovy/my/MyModel.groovy") << """
            package org.gradle.integtests.tooling.fixture

            interface BaseModel {
            }

            interface ShallowChildModel extends BaseModel {
                String getShallowMessage()
            }

            interface DeepChildModel extends BaseModel {
                String getDeepMessage()
            }

            interface VeryDeepChildModel extends DeepChildModel {
                String getVeryDeepMessage()
            }

            interface SideModel extends BaseModel {
            }

            abstract class AbstractModel implements DeepChildModel {
            }

            class DefaultModel extends AbstractModel implements ShallowChildModel, DeepChildModel, VeryDeepChildModel, SideModel, java.io.Serializable {

                private final String message

                DefaultModel(String message) { this.message = message }

                @Override
                String getShallowMessage() { "shallow " + message }

                @Override
                String getDeepMessage() { "deep " + message }

                @Override
                String getVeryDeepMessage() { "very deep " + message }

                @Override
                String toString() { message }
            }

            class DefaultCompositeModel implements java.io.Serializable {
                private final DefaultModel nested
                DefaultCompositeModel(DefaultModel nested) { this.nested = nested }
                BaseModel getNested() { nested }
            }
        """
    }

    def "supports model polymorphism"() {
        given:
        file("plugins/src/main/groovy/my/MyModelBuilder.groovy") << """
            package my

            import ${ToolingModelBuilder.name}
            import ${Project.name}

            class MyModelBuilder implements ToolingModelBuilder {
                boolean canBuild(String modelName) {
                    return modelName == "${BaseModel.name}"
                }
                Object buildAll(String modelName, Project project) {
                    println("creating model for \$project")
                    return new org.gradle.integtests.tooling.fixture.DefaultModel("poly from '" + project.name + "'")
                }
            }
        """.stripIndent()

        addBuilderRegisteringPluginImplementation("plugins", "MyModelBuilder")

        settingsFile << """
            includeBuild("plugins")
            rootProject.name = 'root'
        """
        buildFile << """
            plugins {
                id("my.plugin")
            }
        """

        when:
        def model = toolingApi.withConnection() { connection -> connection.getModel(BaseModel) }

        then:
        assertModelIsPolymorphic(model)
    }

    def "supports nested model polymorphism"() {
        given:
        file("plugins/src/main/groovy/my/MyModelBuilder.groovy") << """
            package my

            import ${ToolingModelBuilder.name}
            import ${Project.name}

            import org.gradle.integtests.tooling.fixture.DefaultCompositeModel
            import org.gradle.integtests.tooling.fixture.DefaultModel

            class MyModelBuilder implements ToolingModelBuilder {
                boolean canBuild(String modelName) {
                    return modelName == "${CompositeModel.name}"
                }
                Object buildAll(String modelName, Project project) {
                    println("creating model for \$project")
                    return new DefaultCompositeModel(new DefaultModel("poly from '" + project.name + "'"))
                }
            }
        """.stripIndent()

        addBuilderRegisteringPluginImplementation("plugins", "MyModelBuilder")

        settingsFile << """
            includeBuild("plugins")
            rootProject.name = 'root'
        """
        buildFile << """
            plugins {
                id("my.plugin")
            }
        """

        when:
        def model = toolingApi.withConnection() { connection -> connection.getModel(CompositeModel) }.nested

        then:
        assertModelIsPolymorphic(model)
    }

    private void addBuilderRegisteringPluginImplementation(String targetBuildName, String builderClassName, String content = "") {
        file("$targetBuildName/src/main/groovy/my/MyPlugin.groovy") << """
            package my

            import ${Project.name}
            import ${Plugin.name}
            import ${Inject.name}
            import ${ToolingModelBuilderRegistry.name}

            abstract class MyPlugin implements Plugin<Project> {
                void apply(Project project) {
                    $content
                    registry.register(new my.$builderClassName())
                }

                @Inject
                abstract ToolingModelBuilderRegistry getRegistry()
            }
        """.stripIndent()
    }

    private static void assertModelIsPolymorphic(Object model) {
        assert model != null

        assert model instanceof BaseModel

        assert model instanceof ShallowChildModel
        assert ((ShallowChildModel) model).getShallowMessage() == "shallow poly from 'root'"

        assert model instanceof DeepChildModel
        assert ((DeepChildModel) model).getDeepMessage() == "deep poly from 'root'"

        assert model instanceof VeryDeepChildModel
        assert ((VeryDeepChildModel) model).getVeryDeepMessage() == "very deep poly from 'root'"

        assert !(model instanceof SideModel)
    }

}
