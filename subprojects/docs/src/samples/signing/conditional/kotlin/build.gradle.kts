// tag::conditional-signing[]
import java.util.concurrent.Callable

// end::conditional-signing[]

plugins {
    java
    maven
    signing
}

group = "gradle"

// tag::conditional-signing[]
version = "1.0-SNAPSHOT"
extra["isReleaseVersion"] = !version.toString().endsWith("SNAPSHOT")

signing {
    setRequired(Callable {
        (project.extra["isReleaseVersion"] as Boolean) && gradle.taskGraph.hasTask("uploadArchives")
    })
    sign(configurations.archives.get())
}
// end::conditional-signing[]

// Alternative to signing.required
// tag::only-if[]
tasks.withType<Sign> {
    onlyIf { project.extra["isReleaseVersion"] as Boolean }
}
// end::only-if[]

tasks.getByName<Upload>("uploadArchives") {
    repositories {
        withConvention(MavenRepositoryHandlerConvention::class) {
            mavenDeployer {
                withGroovyBuilder {
                    "repository"("url" to uri("$buildDir/repo"))
                }
                if (project.extra["isReleaseVersion"] as Boolean) {
                    beforeDeployment { signing.signPom(this) }
                }
            }
        }
    }
}
