extensions.create<ProjectProperties>("myProperties") // <2>

tasks.register("printProperties") { // <3>
    val myProperties = project.extensions.getByName("myProperties") as ProjectProperties
    val projectName = project.name

    doLast {
        println("propertyA in ${projectName}: ${myProperties.propertyA.get()}")
        println("propertyB in ${projectName}: ${myProperties.propertyB.get()}")
    }
}
