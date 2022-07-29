plugins {
    id("gradlebuild.distribution.api-java")
    id("gradlebuild.publish-public-libraries")
    id("info.solidsoft.pitest").version("1.7.4")
}

description = "Tools for creating secure hashes for files and other content"

gradlebuildJava.usedInWorkers() // org.gradle.internal.nativeintegration.filesystem.Stat is used in workers

dependencies {
    implementation(project(":base-annotations"))
    implementation(libs.guava)
}

pitest {
    junit5PluginVersion.set("1.0.0")
    targetClasses.set(setOf("org.gradle.internal.hash.*"))  //by default "${project.group}.*"
    pitestVersion.set("1.9.3") //not needed when a default PIT version should be used
    threads.set(8)
    outputFormats.set(setOf("XML", "HTML"))

    exportLineCoverage.set(true)
    timestampedReports.set(false) // disable placing PIT reports in time-based subfolders for reproducibility

    // Allows for incrementatlly running mutation testing
    historyInputLocation.set(project.layout.buildDirectory.file("pit-history"))
    historyOutputLocation.set(project.layout.buildDirectory.file("pit-history"))
}
