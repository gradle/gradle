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
    println(">> Compilation deps: ${configurations.compileClasspath.get().files.map { it.name }}")
    into(layout.buildDirectory.dir("output"))
    from(configurations.compileClasspath)
}
// end::copy[]
