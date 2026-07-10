abstract class MyProviderTask : DefaultTask() {
    val messageProvider: Provider<String> = project.providers.provider { "Hello, Gradle!" } // message provider

    @TaskAction
    fun printMessage() {
        println(messageProvider.get())
    }
}

tasks.register<MyProviderTask>("MyProviderTask") {

}
