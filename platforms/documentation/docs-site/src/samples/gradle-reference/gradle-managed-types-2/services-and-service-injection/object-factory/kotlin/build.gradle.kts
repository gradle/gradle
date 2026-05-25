tasks.register("myObjectFactoryTask") {
    doLast {
        val objectFactory = project.objects
        val myProperty = objectFactory.property(String::class)
        myProperty.set("Hello, Gradle!")
        println(myProperty.get())
    }
}
