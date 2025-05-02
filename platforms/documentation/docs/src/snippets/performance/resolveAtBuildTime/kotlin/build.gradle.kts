plugins {
    id("java-library")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.apache.commons:commons-lang3:3.11")
}

// tag::copy[]
tasks.register<Copy>("copyFiles") {
    into(layout.buildDirectory.dir("output"))
    // Store the configuration into a variable because referencing the project from the task action
    // is not compatible with the configuration cache.
    val compileClasspath: FileCollection = configurations.compileClasspath.get()
    from(compileClasspath)
    doFirst {
        println(">> Compilation deps: ${compileClasspath.files.map { it.name }}")
    }
}
// end::copy[]
