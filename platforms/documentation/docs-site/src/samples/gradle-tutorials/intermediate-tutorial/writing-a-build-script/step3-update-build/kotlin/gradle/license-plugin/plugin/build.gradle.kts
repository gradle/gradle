gradlePlugin {
    val license by plugins.creating {   // Update name to license
        id = "com.tutorial.license"     // Update id to com.gradle.license
        implementationClass = "license.LicensePlugin"
    }
}
