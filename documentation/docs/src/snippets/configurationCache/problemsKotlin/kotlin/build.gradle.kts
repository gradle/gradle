tasks.register("someTask") {
    val destination = System.getProperty("someDestination") // <1>
    inputs.dir("source")
    outputs.dir(destination)
    doLast {
        project.copy { // <2>
            from("source")
            into(destination)
        }
    }
}
