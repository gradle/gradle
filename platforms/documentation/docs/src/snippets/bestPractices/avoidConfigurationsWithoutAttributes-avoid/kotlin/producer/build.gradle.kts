// tag::avoid-this[]
configurations.consumable("customElements") // <1>

val generateFile = tasks.register("generateFile") {
    val outputFile = layout.buildDirectory.file("custom/output.txt")
    outputs.file(outputFile)
    doLast {
        outputFile.get().asFile.writeText("Custom output from producer")
    }
}

artifacts {
    add("customElements", generateFile)
}
// end::avoid-this[]
