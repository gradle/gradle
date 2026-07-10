open class MyTask : DefaultTask() {
    // note: no annotation here
    val outputFile: RegularFileProperty = newOutputFile()
}

task("myOtherTask") {
    val outputFile = newOutputFile()
    doLast { ... }
}
