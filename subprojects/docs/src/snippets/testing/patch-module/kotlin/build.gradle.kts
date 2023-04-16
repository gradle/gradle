plugins {
    `java-library`
}
repositories {
    mavenCentral()
}

// tag::patchArgs[]
val moduleName = "org.gradle.sample"
val patchArgs = listOf("--patch-module", "$moduleName=${tasks.compileJava.get().destinationDirectory.asFile.get().path}")
tasks.compileTestJava {
    options.compilerArgs.addAll(patchArgs)
}
tasks.test {
    jvmArgs(patchArgs)
}
// end::patchArgs[]

tasks.named<Test>("test") {
    useJUnitPlatform()
}
dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.7.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
