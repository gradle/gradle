tasks {

    val plugin by registering(GradleBuild::class) {
        group = "sample"
        dir = file("plugin")
        tasks = listOf("publish")
    }

    val consumer by registering(GradleBuild::class) {
        group = "sample"
        dir = file("consumer")
        tasks = listOf("myCopyTask")
    }

    consumer {
        dependsOn(plugin)
    }
}
