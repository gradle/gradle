// This file is located in /app
tasks.register("printProperties") { // <1>
    val propA = project.findProperty("propertyA") // <2>
    val propB = project.findProperty("propertyB")

    doLast {
        println("propertyA in app: $propA")
        println("propertyB in app: $propB")
    }
}
