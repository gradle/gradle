tasks {

    val plugin by registering(GradleBuild::class) {
        group = "sample"
        description = "Publishes the plugin to the local repository."
        dir = file("plugin")
        tasks = listOf("publish")
    }

    val consumer by registering(GradleBuild::class) {
        group = "sample"
        description = "Consumes the plugin from the local repository."
        dir = file("consumer")
        tasks = listOf("books")
    }

    consumer {
        dependsOn(plugin)
    }
}
