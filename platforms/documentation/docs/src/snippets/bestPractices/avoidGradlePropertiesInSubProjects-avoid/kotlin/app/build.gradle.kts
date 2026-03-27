// This file is located in /app
tasks.register("printProperties") { // <1>
    val propA = project.getProperty("propertyA") // <2>
    val propB = project.getProperty("propertyB")

    doLast {
        println("propertyA in app: $propA")
        println("propertyB in app: $propB")
    }
}
