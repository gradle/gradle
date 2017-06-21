import org.gradle.api.tasks.bundling.Jar

plugins {
    base
}

base {
    archivesBaseName = "gradle-script-kotlin"
}

evaluationDependsOn(":provider")
evaluationDependsOn(":tooling-models")

tasks {
    "jar"(Jar::class) {
        val providerJar = project(":provider").tasks["jar"] as Jar
        val toolingModelsJar = project(":tooling-models").tasks["jar"] as Jar
        dependsOn(providerJar, toolingModelsJar)
        from(project.zipTree(providerJar.archivePath))
        from(project.zipTree(toolingModelsJar.archivePath))
    }
}
