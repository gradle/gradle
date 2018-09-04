// tag::custom-source-set[]
sourceSets {
    create("integTest") {
        java.srcDir(file("src/integTest/java"))
        resources.srcDir(file("src/integTest/resources"))
        compileClasspath += sourceSets.getByName("main").output + configurations.getByName("testRuntimeClasspath")
        runtimeClasspath += output + compileClasspath
    }
}
// end::custom-source-set[]

// tag::test-task[]
val integTest = task("integTest", Test::class) {
    description = "Runs the integration tests."
    group = "verification"
    testClassesDirs = sourceSets.getByName("integTest").output.classesDirs
    classpath = sourceSets.getByName("integTest").runtimeClasspath
    mustRunAfter(tasks.getByName("test"))
}

tasks.getByName("check").dependsOn(integTest)
// end::test-task[]
