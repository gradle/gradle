// This file is located in /util
tasks.register("printProperties") {
    val propA = project.findProperty("propertyA")
    val propB = project.findProperty("propertyB") // <3>

    doLast {
        println("propertyA in util: $propA")
        println("propertyB in util: $propB")
    }
}
