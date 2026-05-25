plugins {
    `java-library`
}

// tag::verify_shared_versions[]
tasks.register("verifySharedVersions") {
    val distToml = file("gradle/distribution.versions.toml").readText()
    val testToml = file("gradle/test.versions.toml").readText()

    doLast {
        // Extract the errorProne version from each file
        val regex = """errorProne\s*=\s*"(.+?)"""".toRegex()
        val distVersion = regex.find(distToml)?.groupValues?.get(1)
        val testVersion = regex.find(testToml)?.groupValues?.get(1)

        require(distVersion == testVersion) {
            "errorProne version mismatch: distribution=$distVersion, test=$testVersion"
        }
    }
}
// end::verify_shared_versions[]
