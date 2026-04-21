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

package org.gradle.internal.cc.impl.isolated

import org.gradle.integtests.fixtures.build.KotlinDslTestProjectInitiation
import org.gradle.tooling.model.kotlin.dsl.EditorReport
import org.gradle.tooling.model.kotlin.dsl.KotlinDslScriptModel
import org.gradle.tooling.model.kotlin.dsl.KotlinDslScriptsModel

import static org.gradle.integtests.tooling.fixture.ToolingApiModelChecker.checkModel

class IsolatedProjectsToolingApiKotlinDslIntegrationTest extends AbstractIsolatedProjectsToolingApiIntegrationTest implements KotlinDslTestProjectInitiation {

    def isolatedScriptsModel = "org.gradle.kotlin.dsl.tooling.builders.internal.IsolatedScriptsModel"

    def "can fetch KotlinDslScripts model for single subproject build"() {
        withSettings("""
            rootProject.name = "root"
            include("a")
        """)
        withBuildScript()
        withBuildScriptIn("a")

        when:
        def originalModel = fetchModel(KotlinDslScriptsModel)

        then:
        fixture.assertNoConfigurationCache()


        when:
        withIsolatedProjects()
        def model = fetchModel(KotlinDslScriptsModel)

        then:
        fixture.assertModelStored {
            modelsCreated(":", KotlinDslScriptsModel)
            modelsCreated(":a", [isolatedScriptsModel])
        }
        checkKotlinDslScriptsModel(model, originalModel)

        when:
        withIsolatedProjects()
        fetchModel(KotlinDslScriptsModel)

        then:
        fixture.assertModelLoaded()
    }

    def "can fetch KotlinDslScripts model for build with convention plugin from #buildLogicLocation"() {
        withBuildScriptIn(buildLogicLocation, """
            plugins {
                `kotlin-dsl`
            }

            $repositoriesBlock
        """)
        withFile("$buildLogicLocation/src/main/kotlin/my-convention.gradle.kts", """
            plugins {
                `java-library`
            }
        """)

        withSettings("""
            $pluginManagementBlock
            rootProject.name = "root"
            include("a")
        """)
        withBuildScript()
        withBuildScriptIn("a", """
            plugins {
                id("my-convention")
            }
        """)

        when:
        def originalModel = fetchModel(KotlinDslScriptsModel)

        then:
        fixture.assertNoConfigurationCache()

        when:
        withIsolatedProjects()
        def model = fetchModel(KotlinDslScriptsModel)

        then:
        fixture.assertModelStored {
            projectConfigured(":$buildLogicLocation")
            modelsCreated(":", KotlinDslScriptsModel)
            modelsCreated(":a", [isolatedScriptsModel])
        }
        checkKotlinDslScriptsModel(model, originalModel)

        when:
        withIsolatedProjects()
        fetchModel(KotlinDslScriptsModel)

        then:
        fixture.assertModelLoaded()

        where:
        buildLogicLocation | pluginManagementBlock
        "buildSrc"         | ""
        "build-logic"      | """pluginManagement { includeBuild("build-logic") }"""
    }

    def "can fetch KotlinDslScripts model for build with Kotlin extension from #buildLogicLocation"() {
        withBuildScriptIn(buildLogicLocation, """
            plugins {
                `kotlin-dsl`
            }
            group = "org.test"

            $repositoriesBlock
        """)
        withFile("$buildLogicLocation/src/main/kotlin/extensions.kt", """
            import org.gradle.api.Project
            fun Project.foo() {}
        """)

        withSettings("""
            $settingsBlock
            rootProject.name = "root"
            include("a")
        """)
        withBuildScript()
        withBuildScriptIn("a", """
            $buildscriptBlock
            foo()
        """)

        when:
        def originalModel = fetchModel(KotlinDslScriptsModel)

        then:
        fixture.assertNoConfigurationCache()

        when:
        withIsolatedProjects()
        def model = fetchModel(KotlinDslScriptsModel)

        then:
        fixture.assertModelStored {
            projectConfigured(":$buildLogicLocation")
            modelsCreated(":", KotlinDslScriptsModel)
            modelsCreated(":a", [isolatedScriptsModel])
        }
        checkKotlinDslScriptsModel(model, originalModel)

        when:
        withIsolatedProjects()
        fetchModel(KotlinDslScriptsModel)

        then:
        fixture.assertModelLoaded()

        where:
        buildLogicLocation | settingsBlock                     | buildscriptBlock
        "buildSrc"         | ""                                | ""
        "build-logic"      | """includeBuild("build-logic")""" | """buildscript { dependencies { classpath("org.test:build-logic") } }"""
    }

    def "can fetch KotlinDslScripts model for build with build-logic using Kotlin extension from included build"() {
        withBuildScriptIn("included", """
            plugins {
                `kotlin-dsl`
            }
            group = "org.test"

            $repositoriesBlock
        """)
        withFile("included/src/main/kotlin/extensions.kt", """
            import org.gradle.api.Project
            fun Project.foo() {}
        """)
        withSettingsIn("build-logic", """
            includeBuild("../included")
        """)
        withBuildScriptIn("build-logic", """
            plugins {
                `kotlin-dsl`
            }
            $repositoriesBlock

            gradlePlugin {
                plugins {
                    register("my-convention") {
                        implementationClass = "MyConventionPlugin"
                    }
                }
            }

            dependencies {
                implementation("org.test:included")
            }
        """)
        withFile("build-logic/src/main/kotlin/MyConventionPlugin.kt", """
            import org.gradle.api.Plugin
            import org.gradle.api.Project
            import foo

            class MyConventionPlugin: Plugin<Project> {
                override fun apply(target: Project) {
                    target.foo()
                }
            }
        """)

        withSettings("""
            pluginManagement {
                includeBuild("build-logic")
            }
            rootProject.name = "root"
            include("a")
        """)
        withBuildScript()
        withBuildScriptIn("a", """
            plugins {
                id("my-convention")
            }
        """)

        when:
        def originalModel = fetchModel(KotlinDslScriptsModel)

        then:
        fixture.assertNoConfigurationCache()

        when:
        withIsolatedProjects()
        def model = fetchModel(KotlinDslScriptsModel)

        then:
        fixture.assertModelStored {
            projectsConfigured(":build-logic", ":included")
            modelsCreated(":", KotlinDslScriptsModel)
            modelsCreated(":a", [isolatedScriptsModel])
        }
        checkKotlinDslScriptsModel(model, originalModel)

        when:
        withIsolatedProjects()
        fetchModel(KotlinDslScriptsModel)

        then:
        fixture.assertModelLoaded()
    }

    static void checkKotlinDslScriptsModel(actual, expected) {
        assert expected instanceof KotlinDslScriptsModel
        assert actual instanceof KotlinDslScriptsModel

        checkModel(actual, expected, [
            [{ it.scriptModels }, { a, e -> checkKotlinDslScriptModel(a, e) }]
        ])
    }

    static void checkKotlinDslScriptModel(actual, expected) {
        assert expected instanceof KotlinDslScriptModel
        assert actual instanceof KotlinDslScriptModel

        checkModel(actual, expected, [
            { it.classPath },
            { it.sourcePath },
            { it.implicitImports },
            // TODO:isolated support editor reports
//            [{ it.editorReports }, { a, e -> checkEditorReport(a, e) }],
            { it.exceptions },
        ])
    }

    static void checkEditorReport(actual, expected) {
        assert expected instanceof EditorReport
        assert actual instanceof EditorReport

        checkModel(actual, expected, [
            { it.severity },
            { it.message },
            [{ it.position }, [
                { it.line },
                { it.column },
            ]]
        ])
    }
}
