/*
 * Copyright 2026 the original author or authors.
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
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.test.fixtures.dsl.GradleDsl
import org.gradle.tooling.BuildAction
import org.gradle.tooling.BuildController
import org.gradle.tooling.BuildException
import org.gradle.tooling.IntermediateResultHandler
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.internal.adapter.ProtocolToModelAdapter
import org.gradle.tooling.model.gradle.GradleBuild

/**
 * This is weird functionality, but it's currently needed by IntelliJ's SYNC process.
 * They need the classloader that sees whatever was loaded by init script's buildscript { dependencies { classpath { ... } } }.
 * Their relevant logic is here: https://github.com/JetBrains/intellij-community/blob/f7ae5ea359f1732d72345ff2298dcf86413a7fd6/plugins/gradle/tooling-extension-impl/src/com/intellij/gradle/toolingExtension/impl/modelSerialization/ToolingSerializerConverter.java#L26
 *
 * The purpose of this test class is to make sure the private API they rely on (ProtocolToModelAdapter) won't be modified by us, thus breaking the SYNC process.
 * If we need to modify it, then we need to talk to them first.
 */
@ToolingApiVersion('>=9.3.0')
@TargetGradleVersion('>=9.4.0')
class DummyModelCrossVersionSpec extends ToolingApiSpecification {

    def setup() {
        settingsFile.delete()
    }

    def "can query DummyModel with internal BuildScopeModelBuilder API and get correct classloader when no error"(GradleDsl dsl) {
        given:
        def initScriptFileName = dsl.fileNameFor("dummy-init")
        file(initScriptFileName) << initScriptContent(dsl)
        settingsKotlinFile << """
            rootProject.name = "root"
        """

        when:
        def classLoaderName = ""
        succeeds {
            runDummyModelAction(it, initScriptFileName) {
                classLoaderName = it
            }
        }

        then:
        isRightClassLoader(classLoaderName, dsl)

        where:
        dsl << [GradleDsl.KOTLIN, GradleDsl.GROOVY]
    }

    def "can query DummyModel with internal BuildScopeModelBuilder API and get correct classloader when broken settings script"() {
        given:
        def initScriptFileName = dsl.fileNameFor("dummy-init")
        file(initScriptFileName) << initScriptContent(dsl)
        settingsKotlinFile << """
            broken !!!
        """

        when:
        def classLoaderName = ""
        fails {
            runDummyModelAction(it, initScriptFileName) {
                classLoaderName = it
            }
        }

        then:
        thrown(BuildException)
        isRightClassLoader(classLoaderName, dsl)

        where:
        dsl << [GradleDsl.KOTLIN, GradleDsl.GROOVY]
    }

    def "can query DummyModel with internal BuildScopeModelBuilder API and get correct classloader even if broken settings plugin"() {
        given:
        def initScriptFileName = dsl.fileNameFor("dummy-init")
        file(initScriptFileName) << initScriptContent(dsl)
        settingsKotlinFile << """
            pluginManagement {
                includeBuild("build-logic")
            }
            plugins {
                id("build-logic")
            }
            rootProject.name = "root"
        """
        def included = file("build-logic")
        included.file("settings.gradle.kts") << """
            rootProject.name = "build-logic"

            pluginManagement {
                $repositoriesBlock
            }

            dependencyResolutionManagement {
                $repositoriesBlock
            }
        """
        included.file("build.gradle.kts") << """
            plugins {
                `kotlin-dsl`
            }
        """
        included.file("src/main/kotlin/build-logic.settings.gradle.kts") << """
            broken !!!
        """

        when:
        def classLoaderName = ""
        fails {
            runDummyModelAction(it, initScriptFileName) {
                classLoaderName = it
            }
        }

        then:
        thrown(BuildException)
        isRightClassLoader(classLoaderName, dsl)

        where:
        dsl << [GradleDsl.KOTLIN, GradleDsl.GROOVY]
    }

    String runDummyModelAction(ProjectConnection connection, String initScriptFileName, IntermediateResultHandler<String> resultHandler) {
        connection.action()
            .projectsLoaded(new DummyModelAction(), resultHandler)
            .build()
            .forTasks([])
            .withArguments("--init-script=${file(initScriptFileName).absolutePath}")
            .run()
    }

    // Marker interface for requesting the provider-side Dummy model
    static interface DummyModel {}

    // Build action that fetches GradleBuild to initialize, then queries DummyModel and returns its classloader
    static class DummyModelAction implements BuildAction<String>, Serializable {
        @Override
        String execute(BuildController controller) {
            // Fetch GradleBuild to force init script evaluation
            controller.fetch(GradleBuild)

            // Fetch DummyModel
            def result = controller.fetch(DummyModel.class)
            def dummyModel = result.model
            assert dummyModel != null
            Object unpacked = new ProtocolToModelAdapter().unpack(dummyModel)
            ClassLoader modelBuildersClassLoader = unpacked.getClass().getClassLoader()
            return modelBuildersClassLoader.toString()
        }
    }

    static def initScriptContent(GradleDsl dsl) {
        switch (dsl) {
            case GradleDsl.GROOVY:
                return """
                    import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry
                    import javax.inject.Inject
                    
                    beforeSettings { settings ->
                        settings.plugins.apply(DummyModelPlugin)
                    }
                    
                    class DummyModel implements java.io.Serializable {}
                    
                    class DummyModelBuilder implements org.gradle.tooling.provider.model.internal.BuildScopeModelBuilder {
                        boolean canBuild(String modelName) {
                            return modelName == 'org.gradle.integtests.tooling.r940.DummyModelCrossVersionSpec\$DummyModel'
                        }
                        Object create(org.gradle.internal.build.BuildState target) {
                            return new DummyModel()
                        }
                    }
                    
                    class DummyModelPlugin implements Plugin<Settings> {
                        private final ToolingModelBuilderRegistry registry
                        @Inject
                        DummyModelPlugin(ToolingModelBuilderRegistry registry) {
                            this.registry = registry
                        }
                    
                        void apply(Settings settings) {
                            println("Applying DummyModelBuilder to: " + settings)
                            registry.register(new DummyModelBuilder())
                        }
                    }
                """
            case GradleDsl.KOTLIN:
                return """
                    import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry
                    import org.gradle.tooling.provider.model.internal.BuildScopeModelBuilder
                    import org.gradle.internal.build.BuildState
                    import javax.inject.Inject
                    
                    beforeSettings {
                        apply<DummyModelPlugin>()
                    }
                    
                    class DummyModel : java.io.Serializable
                    
                    class DummyModelBuilder : BuildScopeModelBuilder {
                        override fun canBuild(modelName: String): Boolean {
                            return modelName == "org.gradle.integtests.tooling.r940.DummyModelCrossVersionSpec\\\$DummyModel"
                        }
                    
                        override fun create(target: BuildState): Any {
                            return DummyModel()
                        }
                    }
                    
                    class DummyModelPlugin @Inject constructor(
                        private val registry: ToolingModelBuilderRegistry
                    ) : Plugin<Settings> {
                    
                        override fun apply(settings: Settings) {
                            println("Applying DummyModelBuilder to: \$settings")
                            registry.register(DummyModelBuilder())
                        }
                    }
                """
            default:
                throw new RuntimeException("Unhandled DSL: $dsl")
        }
    }

    static def isRightClassLoader(String classLoaderName, GradleDsl dsl) {
        switch (dsl) {
            case GradleDsl.GROOVY :
                return classLoaderName ==~ /ScriptClassLoader\(groovy-script-.*dummy-init\.gradle-loader\)/
            case GradleDsl.KOTLIN :
                return classLoaderName ==~ /VisitableURLClassLoader\(.*kotlin-dsl:.*dummy-init\.gradle.kts:.*\)/
            default:
                throw new RuntimeException("Unhandled DSL: $dsl")
        }
    }
}
