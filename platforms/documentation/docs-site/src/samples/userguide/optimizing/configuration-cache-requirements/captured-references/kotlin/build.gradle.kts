plugins {
    java
}

version = "1.0"

// tag::implicit-dont[]
tasks.register("checkVersion") {
    doLast {
        println(version) // <1>
    }
}
// end::implicit-dont[]

// tag::implicit-do[]
tasks.register("printVersion") {
    val projectVersion = version // <1>
    doLast {
        println(projectVersion) // <2>
    }
}
// end::implicit-do[]

// tag::dont[]
val outputFile = layout.buildDirectory.file("output.txt")

tasks.register("produce") {
    outputs.file(outputFile)
    doLast {
        outputFile.get().asFile.writeText("Hello") // <1>
    }
}
// end::dont[]

// tag::do[]
val reportFile = layout.buildDirectory.file("report.txt")

tasks.register("report") {
    val reportFile = reportFile // <1>
    outputs.file(reportFile)
    doLast {
        reportFile.get().asFile.writeText("Hello") // <2>
    }
}
// end::do[]

// tag::widen-dont[]
tasks.register("resolveClasspath") {
    val runtimeClasspath = configurations.runtimeClasspath // <1>
    doLast {
        println(runtimeClasspath.get().files)
    }
}
// end::widen-dont[]

// tag::widen-do[]
tasks.register("resolveClasspathSafe") {
    val runtimeClasspath: Provider<out FileCollection> = configurations.runtimeClasspath // <1>
    doLast {
        println(runtimeClasspath.get().files)
    }
}
// end::widen-do[]
