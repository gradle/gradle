/**
 * An example Gradle task.
 */
open class GreetingTask : DefaultTask() {

    val message = project.objects.property<String>()

    @TaskAction
    fun greet() = println(message.get())
}

tasks {

    // `hello` is a `TaskProvider<GreetingTask>`
    val hello by registering(GreetingTask::class) {
        message.set("Hello!")
    }

    // `goodbye` is a `TaskProvider<GreetingTask>`
    val goodbye by registering(GreetingTask::class)

    // Every `NamedDomainObjectProvider<T>` can be lazily configured as follows
    goodbye {
        dependsOn(hello)
    }

    // Existing container elements can be lazily configured via the `String` invoke DSL
    "goodbye"(GreetingTask::class) {
        message.set("Goodbye!")
    }

    // Regular API members are also available
    register("chat")
}

// ...

tasks {

    // Existing container elements can be lazily brought into scope
    val goodbye by existing

    "chat" {
        dependsOn(goodbye)
    }
}

defaultTasks("chat")
