import build.*
import plugins.*

plugins {
    id("kotlin-library")
}

dependencies {
    compile(project(":test-fixtures"))
}

val pluginBundles = listOf(
    ":plugins",
    ":plugins-experiments")

pluginBundles.forEach {
    evaluationDependsOn(it)
}

val futurePluginVersionsTasks =
    pluginBundles.map {
        project(it).tasks["writeFuturePluginVersions"] as WriteProperties
    }

val customInstallation by rootProject.tasks

tasks {

    "test" {
        dependsOn(customInstallation)
        pluginBundles.forEach {
            dependsOn(":$it:publishPluginsToTestRepository")
        }
    }


    val processTestResources by getting(ProcessResources::class)

    val writeFuturePluginVersions by creating {

        group = "build"
        description = "Merges all future plugin bundle versions so they can all be tested at once"

        dependsOn(futurePluginVersionsTasks)
        inputs.files(futurePluginVersionsTasks.map { it.outputFile })
        outputs.file(processTestResources.futurePluginVersionsFile)

        doLast {
            outputs.files.singleFile.bufferedWriter().use { writer ->
                inputs.files.forEach { input ->
                    writer.appendln(input.readText())
                }
            }
        }
    }

    processTestResources.dependsOn(writeFuturePluginVersions)
}

withParallelTests()
