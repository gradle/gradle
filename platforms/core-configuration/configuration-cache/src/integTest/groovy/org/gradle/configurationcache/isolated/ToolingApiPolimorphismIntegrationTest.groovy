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
import org.gradle.configurationcache.fixtures.ChildModel
import org.gradle.tooling.provider.model.ToolingModelBuilder

class ToolingApiPolimorphismIntegrationTest extends AbstractIsolatedProjectsToolingApiIntegrationTest {

    def "ongoing"() {
        given:
        addPluginBuildScript("plugins")

        file("plugins/src/main/groovy/my/MyModel.groovy") << """
            package my

            interface BaseModel {
            }

            // Mark as protocol
            interface ChildModel extends BaseModel {
                String getMessage()
            }

            interface SideModel extends BaseModel {
            }

            class DefaultModel implements ChildModel, SideModel, java.io.Serializable {
                private final String message
                DefaultModel(String message) { this.message = message }
                String getMessage() { message }
                String toString() { message }
            }
        """.stripIndent()

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
                    return new DefaultModel("poly from '" + project.name + "'")
                }
            }
        """.stripIndent()

        addBuilderRegisteringPluginImplementation("plugins", "MyModelBuilder")

        settingsFile << """
            includeBuild("plugins")
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
//        model instanceof ChildModel
        ((ChildModel) model).getMessage() == "poly from ':'"
    }
}
