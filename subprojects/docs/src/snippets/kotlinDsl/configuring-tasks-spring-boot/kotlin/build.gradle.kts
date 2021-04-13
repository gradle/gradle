// tag::lazy[]
import org.springframework.boot.gradle.tasks.bundling.BootJar
import org.springframework.boot.gradle.tasks.run.BootRun

// tag::accessors[]
plugins {
    java
    id("org.springframework.boot") version "2.3.4.RELEASE"
}

// end::lazy[]
// end::accessors[]

// tag::accessors[]
tasks.bootJar {
    archiveFileName.set("app.jar")
    mainClassName = "com.example.demo.Demo"
}

tasks.bootRun {
    mainClass.set("com.example.demo.Demo")
    args("--spring.profiles.active=demo")
}
// end::accessors[]

// tag::lazy[]
tasks.named<BootJar>("bootJar") {
    archiveFileName.set("app.jar")
    mainClassName = "com.example.demo.Demo"
}

tasks.named<BootRun>("bootRun") {
    mainClass.set("com.example.demo.Demo")
    args("--spring.profiles.active=demo")
}
// end::lazy[]
