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
    implementation(":log4j:1.2.8")     // <3>
    implementation(":commons-io:2.1")  // <3>
}
// end::file-deps[]

// tag::retrieve-deps[]
tasks {
    register<Copy>("retrieveRuntimeDependencies") {
        into("$buildDir/libs")
        from(configurations.runtimeClasspath)
    }
}
// end::retrieve-deps[]

// tag::properties[]
val tmpDistDir by extra { file("$buildDir/dist") }

tasks {
    register<Jar>("javadocJar") {
        from(javadoc)  // <1>
        archiveClassifier.set("javadoc")
    }

    register<Copy>("unpackJavadocs") {
        from(zipTree(named<Jar>("javadocJar").get().archiveFile))  // <2>
        into(tmpDistDir)  // <3>
    }
}
// end::properties[]
