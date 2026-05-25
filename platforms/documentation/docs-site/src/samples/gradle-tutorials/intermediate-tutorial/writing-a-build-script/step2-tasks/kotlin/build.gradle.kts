val functionalTest by tasks.registering(Test::class) {
    testClassesDirs = functionalTestSourceSet.output.classesDirs
    classpath = functionalTestSourceSet.runtimeClasspath
    useJUnitPlatform()
}

tasks.named<Test>("test") {
    // Use JUnit Jupiter for unit tests.
    useJUnitPlatform()
}
