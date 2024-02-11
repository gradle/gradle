plugins {
    signing
}

tasks.register<Zip>("stuffZip") {
    archiveBaseName = "stuff"
    from("src/stuff")
}

// tag::signing[]
signing {
    val signingKey: String? by project
    val signingPassword: String? by project
    useInMemoryPgpKeys(signingKey, signingPassword)
    sign(tasks["stuffZip"])
}
// end::sign-task[]
