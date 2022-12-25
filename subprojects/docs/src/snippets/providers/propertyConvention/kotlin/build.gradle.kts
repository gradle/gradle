// tag::conventions[]
tasks.register("show") {
    val property = objects.property(String::class)

    // Set a convention
    property.convention("convention 1")

    doLast {
        println("value = " + property.get())

        // Can replace the convention
        property.convention("convention 2")
        println("value = " + property.get())

        property.set("value")

        // Once a value is set, the convention is ignored
        property.convention("ignored convention")
        println("value = " + property.get())
    }
}
// end::conventions[]
