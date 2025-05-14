plugins {
    id("gradlebuild.distribution.implementation-java")
}

description = "Internal API to declare services provided by Gradle modules"

gradleModule {
    usedInWorkers = true
}

dependencies {
    api(projects.serviceLookup)
    api(projects.stdlibJavaExtensions)

    api(libs.jspecify)
    api(libs.errorProneAnnotations)
}
