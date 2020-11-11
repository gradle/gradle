import org.gradle.distribution.DownloadGradle

plugins {
    distribution
}

version = "0.1"

// This is defined in buildSrc
val downloadGradleDistribution = tasks.register<DownloadGradle>("downloadGradleDistribution") {
    description = "Downloads the Gradle distribution with a given version."
    gradleVersion.set("6.7")
    gradleDistributionType.set("bin")
}

tasks.named("distZip") {
    dependsOn(downloadGradleDistribution)
}

distributions {
    main {
        version = downloadGradleDistribution.get().getDistributionNameBase()
        distributionBaseName.set("custom")
        contents {
            from(
                    zipTree("$buildDir/tmp/${downloadGradleDistribution.name}/$version.zip")
            ) {
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
