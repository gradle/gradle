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
import spock.lang.Ignore

class DeprecationsCrossVersionSpec extends ToolingApiSpecification {
    @TargetGradleVersion(">=7.3 <8.0")
    def "deprecation is reported when tooling model builder resolves configuration from a project other than its target"() {
        settingsFile << """
            include("a")
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
                    project.allprojects.each { p ->
                        p.configurations.compileClasspath.files()
                    }
                    return ["result"]
                }
            }

            project.services.get(ToolingModelBuilderRegistry.class).register(new MyModelBuilder())

            allprojects {
                apply plugin: 'java-library'
            }
            project(':a') {
                dependencies {
                    implementation rootProject
                }
            }
        """

        when:
        withConnection {
            def builder = it.model(List)
            builder.withArguments("--parallel")
            collectOutputs(builder)
            return builder.get()
        }

        then:
        stdout.toString().contains("Deprecated Gradle features were used in this build")
    }

    @Ignore("https://github.com/gradle/gradle/issues/22088")
    @TargetGradleVersion(">=8.0")
    def "fails when tooling model builder resolves configuration from a project other than its target post Gradle 8.0"() {
        settingsFile << """
            include("a")
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
                    project.allprojects.each { p ->
                        p.configurations.compileClasspath.files()
                    }
                    return ["result"]
                }
            }

            project.services.get(ToolingModelBuilderRegistry.class).register(new MyModelBuilder())

            allprojects {
                apply plugin: 'java-library'
            }
            project(':a') {
                dependencies {
                    implementation rootProject
                }
            }
        """

        when:
        withConnection {
            def builder = it.model(List)
            builder.withArguments("--parallel")
            collectOutputs(builder)
            return builder.get()
        }

        then:
        def e = thrown(org.gradle.tooling.BuildException)
        e.message.contains("Could not fetch model of type 'List'")
        e.cause.message.contains("Resolution of the configuration :a:compileClasspath was attempted from a context different than the project context.")
    }
}
