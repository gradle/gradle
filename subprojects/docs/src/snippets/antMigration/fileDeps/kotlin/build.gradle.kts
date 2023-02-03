plugins {
    java
}

// tag::file-deps[]
repositories {
    flatDir {
        name = "libs dir"
        dir(file("libs"))  // <1>
    }
}

dependencies {
    implementation(files("libs/our-custom.jar"))  // <2>
    implementation(":awesome-framework:2.0")     // <3>
    implementation(":utility-library:1.0")  // <3>
}
// end::file-deps[]

// tag::retrieve-deps[]
tasks.register<Copy>("retrieveRuntimeDependencies") {
    into(layout.buildDirectory.dir("libs"))
    from(configurations.runtimeClasspath)
}
// end::retrieve-deps[]

// tag::properties[]
val tmpDistDir = layout.buildDirectory.dir("dist")

tasks.register<Jar>("javadocJarArchive") {
    from(tasks.javadoc)  // <1>
    archiveClassifier.set("javadoc")
}

tasks.register<Copy>("unpackJavadocs") {
    from(zipTree(tasks.named<Jar>("javadocJarArchive").get().archiveFile))  // <2>
    into(tmpDistDir)  // <3>
}
// end::properties[]
