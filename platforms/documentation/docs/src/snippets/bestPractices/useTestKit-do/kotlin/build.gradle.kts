plugins {
    id("org.example.myplugin")
}

extensions.getByType<org.example.MyExtension>().apply {
    firstName = "John"
    lastName = "Smith"
}

tasks.named("task2", org.example.MyTask::class.java).configure {
    greeting = "Bonjour" // <4>
}
