tasks.register("someTask") {
    val message = System.getProperty("someMessage") // <1>
    inputs.property("message", message)
    doLast {
        project.exec {                              // <2>
            commandLine = listOf("echo", message)
        }
    }
}
