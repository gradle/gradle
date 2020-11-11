// tag::taskRegistration[]
import org.gradle.distribution.DownloadGradle

// end::taskRegistration[]

// tag::distribution[]
plugins {
    distribution
}

// end::distribution[]
version = "0.1"

// tag::taskRegistration[]
val downloadGradleDistribution = tasks.register<DownloadGradle>("downloadGradleDistribution") {
    description = "Downloads the Gradle distribution with a given version."
    gradleVersion.set("6.7")
    gradleDistributionType.set("bin")
}
// end::taskRegistration[]

// tag::distribution[]
tasks.named("distZip") {
    dependsOn(downloadGradleDistribution)
}

distributions {
    main {
        version = downloadGradleDistribution.get().getDistributionNameBase()
        distributionBaseName.set("custom")
        contents {
            from(zipTree(downloadGradleDistribution.get().destinationFile)) {
                eachFile {
                    relativePath =
                            RelativePath(true, *relativePath.segments.filter {
                                it != "gradle-${downloadGradleDistribution.get().gradleVersion.get()}"
                            }.toTypedArray())
                }
                includeEmptyDirs = false
            }
        }
    }
}
// end::distribution[]
