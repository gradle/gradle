subprojects {
    publishing {
        publications {
            named<MavenPublication>("mavenJava") {
                from(components["java"])
                afterEvaluate {
                    artifactId = tasks.jar.get().archiveBbaseName.get()
                }
            }
        }
    }
}
