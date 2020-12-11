plugins {
    id("gradlebuild.internal.java")
    application
}

dependencies {
    implementation("org.gradle:base-services")
    implementation("org.gradle:tooling-api")
    api("com.android.tools.build:gradle:3.0.0")
}

application {
    mainClass.set("org.gradle.performance.android.Main")
    applicationName = "android-test-app"
}

listOf(tasks.distZip, tasks.distTar).forEach {
    it { archiveBaseName.set("android-test-app") }
}

tasks.register("buildDists") {
    dependsOn(tasks.distZip)
}
