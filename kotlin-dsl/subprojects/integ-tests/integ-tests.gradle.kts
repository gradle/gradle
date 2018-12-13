import build.*
import plugins.*

plugins {
    `kotlin-library`
}

dependencies {
    testImplementation(project(":test-fixtures"))
    testImplementation("com.squareup.okhttp3:mockwebserver:3.9.1")
}

val pluginBundles = listOf(
    ":plugins")

pluginBundles.forEach {
    evaluationDependsOn(it)
}

tasks {

    val testEnvironment by registering {
        dependsOn(":prepareIntegrationTestFixtures")
        dependsOn(":customInstallation")
        pluginBundles.forEach {
            dependsOn(":$it:publishPluginsToTestRepository")
        }
    }

    withType<Test>().configureEach {
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
        outputs.file(processTestResources.get().futurePluginVersionsFile)

        doLast {
            outputs.files.singleFile.bufferedWriter().use { writer ->
                inputs.files.forEach { input ->
                    writer.appendln(input.readText())
                }
            }
        }
    }

    processTestResources {
        dependsOn(writeFuturePluginVersions)
    }
}

withParallelTests()
