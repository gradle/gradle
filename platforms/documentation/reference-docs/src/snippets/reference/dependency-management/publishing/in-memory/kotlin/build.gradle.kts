plugins {
    signing
}

tasks.register<Zip>("stuffZip") {
    archiveBaseName = "stuff"
    from("src/stuff")
}

// tag::signing[]
signing {
    val signingKey = project.findProperty("signingKey") as String?
    val signingPassword = project.findProperty("signingPassword") as String?
    useInMemoryPgpKeys(signingKey, signingPassword)
    sign(tasks["stuffZip"])
}
// end::sign-task[]
