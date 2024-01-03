plugins {
    id("gradlebuild.build-logic.kotlin-dsl-gradle-plugin")
}

description = "Provides plugins that configure profiling tools (jmh and build scans)"

dependencies {
    implementation("com.gradle:gradle-enterprise-gradle-plugin")

    implementation("gradlebuild:basics")
    implementation("gradlebuild:module-identity")

    implementation(project(":documentation"))

    implementation("me.champeau.jmh:jmh-gradle-plugin")
}
