plugins {
    `java-library`
}

// tag::api[]
tasks.named("check")                    // <1>
tasks.register("myTask1")               // <2>

tasks.named<JavaCompile>("compileJava") // <3>
tasks.register<Copy>("myCopy1")         // <4>

tasks.named("assemble") {               // <5>
    dependsOn(":myTask1")
}
tasks.register("myTask2") {             // <6>
    description = "Some meaningful words"
}

tasks.named<Test>("test") {             // <7>
    testLogging.showStackTraces = true
}
tasks.register<Copy>("myCopy2") {       // <8>
    from("source")
    into("destination")
}
// end::api[]
