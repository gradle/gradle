import org.example.GreetTask

plugins {
    id("org.example.hello-problems")
}

repositories {
    mavenCentral()
}


tasks.named<GreetTask>("greet") {
    recipient = "World"
}
