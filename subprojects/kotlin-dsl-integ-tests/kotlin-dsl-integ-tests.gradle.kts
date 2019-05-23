/*
 * Copyright 2018 the original author or authors.
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

import org.gradle.gradlebuild.test.integrationtests.IntegrationTest
import org.gradle.gradlebuild.unittestandcompile.ModuleType
import plugins.futurePluginVersionsFile

plugins {
    `kotlin-library`
}

description = "Kotlin DSL Integration Tests"

gradlebuildJava {
    moduleType = ModuleType.INTERNAL
}

dependencies {
    testImplementation(project(":kotlinDslTestFixtures"))

    integTestImplementation(project(":baseServices"))
    integTestImplementation(project(":coreApi"))
    integTestImplementation(project(":core"))
    integTestImplementation(project(":internalTesting"))
    integTestImplementation("com.squareup.okhttp3:mockwebserver:3.9.1")

    val allTestRuntimeDependencies: DependencySet by rootProject.extra
    allTestRuntimeDependencies.forEach {
        integTestRuntimeOnly(it)
    }
}

val pluginBundles = listOf(
    ":kotlinDslPlugins")

pluginBundles.forEach {
    evaluationDependsOn(it)
}

tasks {

    // TODO:kotlin-dsl
    verifyTestFilesCleanup {
        enabled = false
    }

    val testEnvironment by registering {
        pluginBundles.forEach {
            dependsOn("$it:publishPluginsToTestRepository")
        }
    }

    val integTestTasks: DomainObjectCollection<IntegrationTest> by project.extra
    integTestTasks.configureEach {
        dependsOn(testEnvironment)
    }

    val writeFuturePluginVersions by registering {

        group = "build"
        description = "Merges all future plugin bundle versions so they can all be tested at once"

        val futurePluginVersionsTasks =
            pluginBundles.map {
                project(it).tasks["writeFuturePluginVersions"] as WriteProperties
            }

        dependsOn(futurePluginVersionsTasks)
        inputs.files(futurePluginVersionsTasks.map { it.outputFile })
        outputs.file(processIntegTestResources.get().futurePluginVersionsFile)

        doLast {
            outputs.files.singleFile.bufferedWriter().use { writer ->
                inputs.files.forEach { input ->
                    writer.appendln(input.readText())
                }
            }
        }
    }

    processIntegTestResources {
        dependsOn(writeFuturePluginVersions)
    }
}
