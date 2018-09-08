plugins {
    base
}

// tag::bundle-task[]
task("bundle") {
    val scripts = file("scripts")
    val bundle = file("$buildDir/bundle.js")

    outputs.cacheIf { true }

    inputs.dir(scripts)
        .withPropertyName("scripts")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.file(bundle)
        .withPropertyName("bundle")

    doLast {
        bundle.outputStream().use { stream ->
            scripts.listFiles().sorted().forEach {
                stream.write(it.readBytes())
            }
        }
    }
}
// end::bundle-task[]

task("printBundle") {
    dependsOn("bundle")
    doLast {
        println(file("$buildDir/bundle.js").readText())
    }
}
