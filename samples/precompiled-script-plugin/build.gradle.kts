tasks {

    val plugin by creating(GradleBuild::class) {
        group = "sample"
        dir = file("plugin")
        tasks = listOf("publish")
    }

    val consumer by creating(GradleBuild::class) {
        group = "sample"
        dir = file("consumer")
        tasks = listOf("myCopyTask")
    }

    consumer.dependsOn(plugin)
}
