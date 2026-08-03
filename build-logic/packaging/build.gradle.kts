plugins {
    id("gradlebuild.build-logic.kotlin-dsl-gradle-plugin")
}

description = "Provides a plugin for building Gradle distributions"

dependencies {
    implementation("gradlebuild:basics")
    implementation("gradlebuild:module-identity")

    implementation(projects.documentation) {
        // TODO turn this around: move corresponding code to this project and let docs depend on it
        because("API metadata generation is part of the DSL guide")
    }
    implementation(projects.dependencyModules)
    implementation(projects.jvm)
    implementation(projects.kotlinDsl)

    implementation(buildLibs.kgp)

    implementation(buildLibs.gson)
    implementation(libs.asm)
    implementation(libs.maven3Model)

    // The org.xdcl XDCL codegen plugin, substituted from the included sibling xdcl build, so
    // the XDCL ecosystem conventions can apply `id("xdcl-gradle-plugin")` on behalf of the modules.
    implementation("org.xdcl:xdcl-gradle-plugin:0.1.0-SNAPSHOT")

}
