plugins {
    signing
}

tasks.register<Zip>("stuffZip") {
    archiveBaseName.set("stuff")
    from("src/stuff")
}

// tag::signing[]
signing {
    val signingKey: String? = project.findProperty("signingKey") as String?
    val signingPassword: String? = project.findProperty("signingPassword") as String?
    useInMemoryPgpKeys(signingKey, signingPassword)
    sign(tasks["stuffZip"])
}
// end::sign-task[]
