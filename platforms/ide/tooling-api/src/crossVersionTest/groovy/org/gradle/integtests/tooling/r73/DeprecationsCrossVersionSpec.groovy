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

package org.gradle.integtests.tooling.r73

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.util.GradleVersion

class DeprecationsCrossVersionSpec extends ToolingApiSpecification {
    @TargetGradleVersion(">=7.3")
    def "deprecation is reported when tooling model builder resolves configuration from a project other than its target"() {
        settingsFile << """
            include("a")
        """
        file("a/build.gradle") << """
            plugins {
                id("java-library")
            }
        """
        buildFile << """
            import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry
            import org.gradle.tooling.provider.model.ToolingModelBuilder
            import org.gradle.api.Project

            class MyModelBuilder implements ToolingModelBuilder {
                boolean canBuild(String modelName) {
                    return modelName == "${List.class.name}"
                }
                Object buildAll(String modelName, Project project) {
                    println("creating model for \$project")
                    project.subprojects.each { p ->
                        p.configurations.compileClasspath.files
                    }
                    return ["result"]
                }
            }

            project.services.get(ToolingModelBuilderRegistry.class).register(new MyModelBuilder())
        """

        expect:
        if (GradleVersion.version(targetDist.version.version) < GradleVersion.version("8.0")) {
            expectDocumentedDeprecationWarning("Resolution of the configuration :a:compileClasspath was attempted from a context different than the project context. Have a look at the documentation to understand why this is a problem and how it can be resolved. This behaviour has been deprecated and is scheduled to be removed in Gradle 8.0. See https://docs.gradle.org/current/userguide/viewing_debugging_dependencies.html#sub:resolving-unsafe-configuration-resolution-errors for more details.")
        } else if (GradleVersion.version(targetDist.version.version) < GradleVersion.version("8.2")) {
            expectDocumentedDeprecationWarning("Resolution of the configuration :a:compileClasspath was attempted from a context different than the project context. Have a look at the documentation to understand why this is a problem and how it can be resolved. This behavior has been deprecated. This will fail with an error in Gradle 9.0. See https://docs.gradle.org/current/userguide/viewing_debugging_dependencies.html#sub:resolving-unsafe-configuration-resolution-errors for more details.")
        } else {
            expectDocumentedDeprecationWarning("Resolution of the configuration :a:compileClasspath was attempted from a context different than the project context. Have a look at the documentation to understand why this is a problem and how it can be resolved. This behavior has been deprecated. This will fail with an error in Gradle 9.0. For more information, please refer to https://docs.gradle.org/current/userguide/viewing_debugging_dependencies.html#sub:resolving-unsafe-configuration-resolution-errors in the Gradle documentation.")
        }

        succeeds { connection ->
            connection.model(List)
                .withArguments("--parallel")
                .get()
        }
    }
}
