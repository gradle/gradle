// tag::use-plugin[]
plugins {
    // end::use-plugin[]
    java
    maven
// tag::use-plugin[]
    signing
}
// end::use-plugin[]


group = "gradle"
version = "1.0"

// Typically set in ~/.gradle/gradle.properties
extra["signing.keyId"] = "24875D73"
extra["signing.password"] = "gradle"
extra["signing.secretKeyRingFile"] = file("secKeyRingFile.gpg").absolutePath

// tag::sign-archives[]
signing {
    sign(configurations.archives.get())
}
// end::sign-archives[]

// tag::sign-pom[]
tasks.named<Upload>("uploadArchives") {
    repositories {
        withConvention(MavenRepositoryHandlerConvention::class) {
            mavenDeployer {
                // end::sign-pom[]
                withGroovyBuilder {
                    "repository"("url" to uri("$buildDir/repo"))
                }
                // tag::sign-pom[]
                beforeDeployment { signing.signPom(this) }
            }
        }
    }
}
// end::sign-pom[]
