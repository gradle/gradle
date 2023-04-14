/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.integtests.tooling.r81

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.BuildActionFailureException

@ToolingApiVersion(">=8.1")
class ToolchainsParallelActionExecutionCrossVersionSpec extends ToolingApiSpecification {

    def setup() {
        buildFile << """
            import javax.inject.Inject
            import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry
            import org.gradle.tooling.provider.model.ToolingModelBuilder

            class ToolchainPlugin implements Plugin<Project> {
                ToolingModelBuilderRegistry registry

                @Inject
                ToolchainPlugin(ToolingModelBuilderRegistry registry) {
                    this.registry = registry
                }

                void apply(Project project) {
                    registry.register(new ToolchainBuilder())
                }
            }

            class ToolchainModel implements Serializable {
                String path
                Integer javaVersion
            }

            class ToolchainBuilder implements ToolingModelBuilder {
                boolean canBuild(String modelName) {
                    return modelName == "org.gradle.integtests.tooling.r81.ToolchainModel"
                }

                Object buildAll(String modelName, Project project) {
                    // Access toolchain related information
                    def compileJavaVersion = project.tasks.compileJava.javaCompiler.get().metadata.languageVersion.asInt()
                    return new ToolchainModel(path: project.path, javaVersion: compileJavaVersion);
                }
            }
        """
    }

    @TargetGradleVersion(">=8.1")
    def "nested actions that query a project model which leverages toolchain information do not cause Property evaluation to be in unexpected state"() {
        given:
        setupBuildWithToolchainsResolution()

        when:
        withConnection {
            def action = action(new ActionRunsNestedActions())
            action.standardOutput = System.out
            action.standardError = System.err
            action.addArguments("--parallel")
            action.run()
        }

        then:
        def e = thrown(BuildActionFailureException)
        def root = rootCause(e)
        root.message.startsWith('No locally installed toolchains match')
    }

    def rootCause(Exception e) {
        def ex = e
        while (ex.cause != null) {
            ex = ex.cause
        }
        ex
    }

    def setupBuildWithToolchainsResolution() {
        settingsFile << """
            import java.util.Optional;
            import org.gradle.platform.BuildPlatform;

            public abstract class FakeResolver implements JavaToolchainResolver {
                @Override
                public Optional<JavaToolchainDownload> resolve(JavaToolchainRequest request) {
                    return Optional.empty();
                }
            }
            public abstract class FakeProvider implements Plugin<Settings> {
                @Inject
                protected abstract JavaToolchainResolverRegistry getToolchainResolverRegistry();

                void apply(Settings settings) {
                    settings.getPlugins().apply("jvm-toolchain-management");

                    JavaToolchainResolverRegistry registry = getToolchainResolverRegistry();
                    registry.register(FakeResolver.class);
                }
            }

            apply plugin: FakeProvider

            toolchainManagement {
                jvm {
                    javaRepositories {
                        repository('useless') {
                            resolverClass = FakeResolver
                        }
                    }
                }
            }

            rootProject.name = 'root'
            include 'a', 'b'
        """
        buildFile << """
            allprojects {
                apply plugin: ToolchainPlugin
                apply plugin: 'java'

                java {
                    toolchain {
                        // Using a toolchain that triggers auto-provisioning is needed
                        languageVersion = JavaLanguageVersion.of('99')
                    }
                }
            }
        """
    }
}
