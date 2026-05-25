tasks.register("showLayout") {
    doLast {
        val layout = project.layout
        println("Project Directory: ${layout.projectDirectory}")
        println("Build Directory: ${layout.buildDirectory.get()}")
    }
}
