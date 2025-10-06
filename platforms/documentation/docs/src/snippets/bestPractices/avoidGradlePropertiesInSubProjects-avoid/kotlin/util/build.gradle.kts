// This file is located in /util
tasks.register("printProperties") {
    val propA = project.properties.get("propertyA")
    val propB = project.properties.get("propertyB") // <3>

    doLast {
        println("propertyA in util: $propA")
        println("propertyB in util: $propB")
    }
}
