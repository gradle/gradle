// This file is located in /util
tasks.register("printProperties") {
    val propA = project.getProperty("propertyA")
    val propB = project.getProperty("propertyB") // <3>

    doLast {
        println("propertyA in util: $propA")
        println("propertyB in util: $propB")
    }
}
