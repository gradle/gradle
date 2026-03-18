/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.integtests.tooling.r940

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.junit.Rule

@TargetGradleVersion(">=9.4.0")
class ParallelActionExecutionCrossVersionSpec extends ToolingApiSpecification {
    @Rule
    BlockingHttpServer server = new BlockingHttpServer()

    def setup() {
        groovyFile "buildSrc/src/main/groovy/CustomPlugin.groovy", """
            import javax.inject.Inject
            import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry
            import org.gradle.tooling.provider.model.ToolingModelBuilder
            import org.gradle.api.Project
            import org.gradle.api.Plugin

            abstract class CustomPlugin implements Plugin<Project> {
                void apply(Project project) { registry.register(new CustomBuilder()) }
                @Inject abstract ToolingModelBuilderRegistry getRegistry()
            }

            class CustomModel implements Serializable {
                String path
            }

            class CustomBuilder implements ToolingModelBuilder {
                boolean canBuild(String modelName) { modelName.endsWith("CustomModel") }

                Object buildAll(String modelName, Project project) {
                    // Do some dependency resolution
                    project.configurations.runtimeClasspath.files.each { }
                    return new CustomModel(path: project.path)
                }
            }
        """
    }

    def "nested actions that query a project model run in parallel when target Gradle version supports it and #args is used"() {
        given:
        setupBuildWithDependencyResolution()

        expect:
        server.expectConcurrent('root', 'a', 'b')
        def models = withConnection { connection ->
            connection.action(new ActionRunsNestedActions())
                .addArguments(args)
                .run()
        }

        models.mayRunInParallel
        models.subResults.path == [':', ':a', ':b']

        where:
        args << [
            ["--parallel"],
            ["-Dorg.gradle.tooling.parallel=true"],
        ]
    }

    def "nested actions that query a project model do not run in parallel when target Gradle version supports it with public options and #args is used"() {
        given:
        setupBuildWithDependencyResolution()

        expect:
        server.expectConcurrent(1, 'root', 'a', 'b')
        def models = withConnection { connection ->
            connection.action(new ActionRunsNestedActions())
                .addArguments(args)
                .run()
        }

        !models.mayRunInParallel
        models.subResults.path == [':', ':a', ':b']

        where:
        args << [
            ["--no-parallel"],
            ["--parallel", "-Dorg.gradle.tooling.parallel.ignore-legacy-default=true"],
            ["--parallel", "-Dorg.gradle.tooling.parallel=false"],
        ]
    }

    def setupBuildWithDependencyResolution() {
        server.start()

        settingsFile """
            rootProject.name = 'root'
        """

        includeProjects("a", "b")

        [".", "a", "b"].each {
            buildFile "$it/build.gradle", """
                apply plugin: 'java'
                apply plugin: CustomPlugin

                configurations.runtimeClasspath.incoming.beforeResolve {
                    ${server.callFromBuildUsingExpression('project.name')}
                }
            """
        }

        buildFile """
            dependencies {
                implementation(project('a'))
                implementation(project('b'))
            }
        """
    }
}
