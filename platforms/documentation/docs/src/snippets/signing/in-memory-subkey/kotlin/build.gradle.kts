plugins {
    signing
}

tasks.register<Zip>("stuffZip") {
    archiveBaseName = "stuff"
    from("src/stuff")
}

// tag::signing[]
signing {
    val signingKeyId: String? by project
    val signingKey: String? by project
    val signingPassword: String? by project
    useInMemoryPgpKeys(signingKeyId, signingKey, signingPassword)
    sign(tasks["stuffZip"])
}
// end::sign-task[]
