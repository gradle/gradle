val artifactType = Attribute.of("artifactType", String::class.java)

val produce = tasks.register("produce") {
    dependsOn(rootProject.tasks.named("intro"))
    val output = layout.buildDirectory.file("produced/data.txt")
    outputs.file(output)
    outputs.upToDateWhen { false }
    doLast {
        Thread.sleep(2000)
        output.get().asFile.writeText("produced at ${System.currentTimeMillis()}")
    }
}

configurations.create("texts") {
    isCanBeConsumed = true
    isCanBeResolved = false
    attributes { attribute(artifactType, "txt") }
    outgoing.artifact(produce)
}
