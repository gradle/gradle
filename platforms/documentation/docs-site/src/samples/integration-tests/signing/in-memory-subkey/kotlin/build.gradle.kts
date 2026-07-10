plugins {
    signing
}

tasks.register<Zip>("stuffZip") {
    archiveBaseName = "stuff"
    from("src/stuff")
}

// tag::signing[]
signing {
    val signingKeyId = project.findProperty("signingKeyId") as String?
    val signingKey = project.findProperty("signingKey") as String?
    val signingPassword = project.findProperty("signingPassword") as String?
    useInMemoryPgpKeys(signingKeyId, signingKey, signingPassword)
    sign(tasks["stuffZip"])
}
// end::sign-task[]
