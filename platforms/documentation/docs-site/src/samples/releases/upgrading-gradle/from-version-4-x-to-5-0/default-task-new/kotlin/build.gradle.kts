open class MyTask : DefaultTask() {
    @OutputFile // property needs an annotation
    val outputFile: RegularFileProperty = project.objects.fileProperty()
}

task("myOtherTask") {
    val outputFile = project.objects.fileProperty()
    outputs.file(outputFile) // or to be registered using the runtime API
    doLast { ... }
}
