plugins {
    base
}

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
