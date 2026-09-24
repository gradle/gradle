plugins {
    id("org.example.myplugin")
}

myExtension {
    firstName = "John"
    lastName = "Smith"
}

tasks.task2 {
    greeting = "Bonjour" // <4>
}
