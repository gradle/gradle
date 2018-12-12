val sourceSets = the<SourceSetContainer>()

// tag::custom-source-set[]
sourceSets {
    create("integTest") {
        java.srcDir(file("src/integTest/java"))
        resources.srcDir(file("src/integTest/resources"))
        compileClasspath += sourceSets["main"].output + configurations["testRuntimeClasspath"]
        runtimeClasspath += output + compileClasspath
    }
}
// end::custom-source-set[]

// tag::test-task[]
tasks.register<Test>("integTest") {
    description = "Runs the integration tests."
    group = "verification"
    testClassesDirs = sourceSets["integTest"].output.classesDirs
    classpath = sourceSets["integTest"].runtimeClasspath
    mustRunAfter(tasks["test"])
}

tasks.named("check") {
    dependsOn("integTest")
}
// end::test-task[]
