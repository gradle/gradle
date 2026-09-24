plugins {
    signing
}

// Typically set in ~/.gradle/gradle.properties
extra["signing.keyId"] = "24875D73"
extra["signing.password"] = "gradle"
extra["signing.secretKeyRingFile"] = file("secKeyRingFile.gpg").absolutePath

// tag::sign-task[]
tasks.register<Zip>("stuffZip") {
    archiveBaseName = "stuff"
    from("src/stuff")
}

signing {
    sign(tasks["stuffZip"])
}
// end::sign-task[]
