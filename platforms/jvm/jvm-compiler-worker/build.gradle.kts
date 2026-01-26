plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Implements common logic for compiling JVM languages in a separate worker process"

dependencies {
    api(projects.baseCompilerWorker)
    api(projects.baseServices)
    api(projects.coreApi)
    api(projects.daemonServerWorker)
    api(projects.internalInstrumentationApi)
    api(projects.stdlibJavaExtensions)
    api(libs.inject)
    api(libs.jspecify)

    implementation(projects.classloaders)
    implementation(projects.logging)
    implementation(libs.guava)
    implementation(libs.commonsLang)

    integTestDistributionRuntimeOnly(projects.distributionsJvm)
}
