// Upper-cases a text file, taking 1s
abstract class Uppercase : TransformAction<TransformParameters.None> {
    @get:InputArtifact
    abstract val inputArtifact: Provider<FileSystemLocation>

    override fun transform(outputs: TransformOutputs) {
        val input = inputArtifact.get().asFile
        Thread.sleep(1000)
        outputs.file(input.nameWithoutExtension + ".upper").writeText(input.readText().uppercase())
    }
}

val intro = tasks.register("intro") {
    doLast {
        println("A produce task (2s) in the producer project writes a file that a consume task (2s) reads through an artifact transform (1s).")
        println("Shows the ETA unavailable on every run, as the transform runs as its own step in the plan but has no recorded duration.")
    }
}

val artifactType = Attribute.of("artifactType", String::class.java)

val texts = configurations.create("texts") {
    isCanBeConsumed = false
    isCanBeResolved = true
    attributes { attribute(artifactType, "txt") }
}
dependencies {
    registerTransform(Uppercase::class) {
        from.attribute(artifactType, "txt")
        to.attribute(artifactType, "upper")
    }
    "texts"(project(":producer", "texts"))
}

val consume = tasks.register("consume") {
    dependsOn(intro)
    val upper = texts.incoming.artifactView { attributes { attribute(artifactType, "upper") } }.files
    inputs.files(upper)
    doLast {
        Thread.sleep(2000)
        upper.forEach { println(it.readText()) }
    }
}
tasks.register("all") { dependsOn(intro, consume) }
