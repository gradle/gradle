import com.jfrog.bintray.gradle.BintrayExtension.PackageConfig

plugins {
    id("com.jfrog.bintray") version "1.8.5"
}

// tag::closureOf[]
bintray {
    pkg(closureOf<PackageConfig> {
        // Config for the package here
    })
}
// end::closureOf[]
