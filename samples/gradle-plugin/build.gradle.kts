tasks {

    val publishPlugin by creating(GradleBuild::class) {
        dir = file("plugin")
        tasks = listOf("publish")
    }

    val checkConsumer by creating(GradleBuild::class) {
        dir = file("consumer")
        tasks = listOf("myCopyTask")
    }

    checkConsumer.dependsOn(publishPlugin)
}
