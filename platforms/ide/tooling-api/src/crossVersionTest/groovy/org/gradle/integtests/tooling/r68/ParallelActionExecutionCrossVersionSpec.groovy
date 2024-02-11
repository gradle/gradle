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

package org.gradle.integtests.tooling.r68

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.TextUtil
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.gradle.tooling.BuildActionFailureException
import org.junit.Rule

@ToolingApiVersion(">=6.8")
class ParallelActionExecutionCrossVersionSpec extends ToolingApiSpecification {
    @Rule
    BlockingHttpServer server = new BlockingHttpServer()

    def setup() {
        buildFile << """
            import javax.inject.Inject
            import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry
            import org.gradle.tooling.provider.model.ToolingModelBuilder

            class CustomPlugin implements Plugin<Project> {
                ToolingModelBuilderRegistry registry

                @Inject
                CustomPlugin(ToolingModelBuilderRegistry registry) {
                    this.registry = registry
                }

                void apply(Project project) {
                    registry.register(new CustomBuilder())
                }
            }

            class CustomModel implements Serializable {
                String path
            }

            class CustomBuilder implements ToolingModelBuilder {
                boolean canBuild(String modelName) {
                    return modelName == "org.gradle.integtests.tooling.r68.CustomModel"
                }

                Object buildAll(String modelName, Project project) {
                    // Do some dependency resolution
                    project.configurations.runtimeClasspath.files.each { }
                    return new CustomModel(path: project.path);
                }
            }
        """
    }

    @TargetGradleVersion(">=6.8")
    def "nested actions that query a project model run in parallel when target Gradle version supports it and --parallel is used"() {
        given:
        setupBuildWithDependencyResolution()

        expect:
        server.expectConcurrent('root', 'a', 'b')
        def models = withConnection {
            def action = action(new ActionRunsNestedActions())
            action.standardOutput = System.out
            action.standardError = System.err
            action.addArguments("--parallel")
            action.run()
        }

        models.mayRunInParallel
        models.projects.path == [':', ':a', ':b']
    }

    @TargetGradleVersion(">=6.8")
    def "nested actions that query a project model do not run in parallel when target Gradle version supports it and #args is used"() {
        given:
        setupBuildWithDependencyResolution()

        expect:
        server.expectConcurrent(1, 'root', 'a', 'b')
        def models = withConnection {
            def action = action(new ActionRunsNestedActions())
            collectOutputs(action)
            action.addArguments(args)
            action.run()
        }

        !models.mayRunInParallel
        models.projects.path == [':', ':a', ':b']

        where:
        args << [
            ["--no-parallel"],
            ["--parallel", "-Dorg.gradle.internal.tooling.parallel=false"]
        ]
    }

    @TargetGradleVersion(">=3.4 <6.8")
    def "nested actions do not run in parallel when target Gradle version does not support it"() {
        given:
        setupBuildWithDependencyResolution()

        expect:
        server.expect('root')
        server.expect('a')
        server.expect('b')
        def models = withConnection {
            def action = action(new ActionRunsNestedActions())
            action.standardOutput = System.out
            action.standardError = System.err
            action.addArguments("--parallel")
            action.run()
        }

        !models.mayRunInParallel
        models.projects.path == [':', ':a', ':b']
    }

    @TargetGradleVersion(">=3.4")
    def "nested action can run further nested actions"() {
        settingsFile << """
            rootProject.name = 'root'
            include 'a', 'b'
        """
        buildFile << """
            allprojects {
                apply plugin: CustomPlugin
                apply plugin: 'java'
            }
        """

        expect:
        def models = withConnection {
            def action = action(new ActionRunsMultipleLevelsOfNestedActions())
            action.standardOutput = System.out
            action.standardError = System.err
            action.addArguments("--parallel")
            action.run()
        }

        models.size() == 3
        models.every { it.projects.size() == 5 }
    }

    def "propagates nested action failures"() {
        when:
        withConnection {
            action(new ActionRunsBrokenNestedActions()).run()
        }

        then:
        def e = thrown(BuildActionFailureException)
        e.cause instanceof RuntimeException
        TextUtil.normaliseLineSeparators(e.cause.message) == """Multiple build operations failed.
    broken: one
    broken: two"""
    }

    def setupBuildWithDependencyResolution() {
        server.start()

        settingsFile << """
            rootProject.name = 'root'
            include 'a', 'b'
        """
        buildFile << """
            allprojects {
                apply plugin: CustomPlugin
                apply plugin: 'java'

                configurations.runtimeClasspath.incoming.beforeResolve {
                    ${server.callFromBuildUsingExpression('project.name')}
                }
            }
            dependencies {
                implementation project('a')
                implementation project('b')
            }
        """
    }
}
