plugins {
    id("gradlebuild.build-logic.kotlin-dsl-gradle-plugin")
}

description = "Provides plugins that configure profiling tools (jmh and Build Scan)"

dependencies {
    implementation(buildLibs.develocityPlugin)

    implementation("gradlebuild:basics")
    implementation("gradlebuild:module-identity")

    implementation(projects.documentation)
    implementation(projects.jvm)

    implementation(buildLibs.jmhPlugin)
}
