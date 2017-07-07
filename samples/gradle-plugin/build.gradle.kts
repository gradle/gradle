tasks {

    val publishPlugin by creating(GradleBuild::class) {
        dir = file("plugin")
        tasks = listOf("publish")
    }

    val checkSample by creating(GradleBuild::class) {
        dir = file("sample")
        tasks = listOf("myCopyTask")
    }

    checkSample.dependsOn(publishPlugin)
}
