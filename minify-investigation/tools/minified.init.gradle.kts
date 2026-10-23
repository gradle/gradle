// Resolves the minified external jars of a few projects, without building any project jars
gradle.lifecycle.afterProject {
    if (name in setOf("kotlin-dsl", "security", "process-services-base", "persistent-cache")) {
        val minified = configurations.named("runtimeClasspath").map { configuration ->
            configuration.incoming.artifactView { componentFilter { it is org.gradle.api.artifacts.component.ModuleComponentIdentifier } }.files
        }
        tasks.register("printMinified") {
            inputs.files(minified)
            val files = minified
            doLast { files.get().filter { it.name.endsWith("-min.jar") }.forEach { println("MINIFIED ${it.length()} $it") } }
        }
    }
}
