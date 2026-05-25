tasks.register("printMessage") {
    doLast {
        val providerFactory = project.providers
        val messageProvider = providerFactory.provider { "Hello, Gradle!" }
        println(messageProvider.get())
    }
}
