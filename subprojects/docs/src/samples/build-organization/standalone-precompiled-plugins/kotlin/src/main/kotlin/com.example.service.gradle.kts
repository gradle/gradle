// tag::plugins[]
plugins {
    id("com.example.java-convention")
}
// end::plugins[]

val integrationTest by sourceSets.creating {
    compileClasspath += sourceSets.main.get().output + sourceSets.test.get().output
    runtimeClasspath += sourceSets.main.get().output + sourceSets.test.get().output
}

val integrationTestTask = tasks.register<Test>("integrationTest") {
    shouldRunAfter(tasks.named("test"))

    testClassesDirs = sourceSets["integrationTest"].output.classesDirs
    classpath = sourceSets["integrationTest"].runtimeClasspath
}

configurations["integrationTestImplementation"].extendsFrom(configurations["testImplementation"])
configurations["integrationTestRuntimeOnly"].extendsFrom(configurations["testRuntimeOnly"])

// tag::use-java-class[]
val readmeCheck by tasks.registering(com.example.ReadmeVerificationTask::class) {
    readme.set(file("${rootProject.rootDir}/README.md"))
    readmePatterns.set(listOf("^## Service API$"))
}
// end::use-java-class[]

tasks.named("check") { dependsOn(integrationTestTask, readmeCheck) }
