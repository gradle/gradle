// tag::lazy[]
import org.springframework.boot.gradle.tasks.bundling.BootJar
import org.springframework.boot.gradle.tasks.run.BootRun

// TODO:Finalize Upload Removal - Issue #21439
// tag::accessors[]
plugins {
    java
    id("org.springframework.boot") version "2.7.8"
}

// end::lazy[]
// end::accessors[]

// tag::accessors[]
tasks.bootJar {
    archiveFileName = "app.jar"
    mainClass = "com.example.demo.Demo"
}

tasks.bootRun {
    mainClass = "com.example.demo.Demo"
    args("--spring.profiles.active=demo")
}
// end::accessors[]

// tag::lazy[]
tasks.named<BootJar>("bootJar") {
    archiveFileName = "app.jar"
    mainClass = "com.example.demo.Demo"
}

tasks.named<BootRun>("bootRun") {
    mainClass = "com.example.demo.Demo"
    args("--spring.profiles.active=demo")
}
// end::lazy[]
