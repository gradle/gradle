/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.configurationcache.isolated

import org.gradle.api.Project
import org.gradle.configurationcache.fixtures.BaseModel
import org.gradle.configurationcache.fixtures.CompositeModel
import org.gradle.configurationcache.fixtures.DeepChildModel
import org.gradle.configurationcache.fixtures.ShallowChildModel
import org.gradle.tooling.provider.model.ToolingModelBuilder

class ToolingApiPolymorphismIntegrationTest extends AbstractIsolatedProjectsToolingApiIntegrationTest {

    def "setup"() {
        addPluginBuildScript("plugins")

        groovyFile("plugins/src/main/groovy/my/MyModel.groovy", """
            package org.gradle.configurationcache.fixtures

            interface BaseModel {
            }

            interface ShallowChildModel extends BaseModel {
                String getShallowMessage()
            }

            interface DeepChildModel extends BaseModel {
                String getDeepMessage()
            }

            interface SideModel extends BaseModel {
            }

            abstract class AbstractModel implements DeepChildModel {
            }

            class DefaultModel extends AbstractModel implements ShallowChildModel, java.io.Serializable {
                private final String message
                DefaultModel(String message) { this.message = message }
                String getShallowMessage() { "shallow " + message }
                String getDeepMessage() { "deep " + message }
                String toString() { message }
            }

            class DefaultCompositeModel implements java.io.Serializable {
                private final DefaultModel nested
                DefaultCompositeModel(DefaultModel nested) { this.nested = nested }
                BaseModel getNested() { nested }
            }
        """)
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
                    return new org.gradle.configurationcache.fixtures.DefaultModel("poly from '" + project.name + "'")
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
        def model = fetchModel(BaseModel)

        then:
        model != null
        model instanceof BaseModel
        model instanceof ShallowChildModel
        ((ShallowChildModel) model).getShallowMessage() == "shallow poly from 'root'"
        model instanceof DeepChildModel
        ((DeepChildModel) model).getDeepMessage() == "deep poly from 'root'"
    }

    def "supports nested model polymorphism"() {
        given:

        file("plugins/src/main/groovy/my/MyModelBuilder.groovy") << """
            package my

            import ${ToolingModelBuilder.name}
            import ${Project.name}

            import org.gradle.configurationcache.fixtures.DefaultCompositeModel
            import org.gradle.configurationcache.fixtures.DefaultModel

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
        def model = fetchModel(CompositeModel).nested

        then:
        model != null
        model instanceof BaseModel
        model instanceof ShallowChildModel
        ((ShallowChildModel) model).getShallowMessage() == "shallow poly from 'root'"
        model instanceof DeepChildModel
        ((DeepChildModel) model).getDeepMessage() == "deep poly from 'root'"
    }
}
