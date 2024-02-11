// tag::conventions[]
tasks.register("show") {
    val property = objects.property(String::class)

    // Set a convention
    property.convention("convention 1")

    println("value = " + property.get())

    // Can replace the convention
    property.convention("convention 2")
    println("value = " + property.get())

    property.set("explicit value")

    // Once a value is set, the convention is ignored
    property.convention("ignored convention")

    doLast {
        println("value = " + property.get())
    }
}
// end::conventions[]
