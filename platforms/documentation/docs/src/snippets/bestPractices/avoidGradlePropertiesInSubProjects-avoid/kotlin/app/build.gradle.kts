// This file is located in /app
tasks.register("printProperties") { // <1>
    val propA = project.properties.get("propertyA") // <2>
    val propB = project.properties.get("propertyB")

    doLast {
        println("propertyA in app: $propA")
        println("propertyB in app: $propB")
    }
}
