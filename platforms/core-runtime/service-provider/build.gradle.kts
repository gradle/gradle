plugins {
    id("gradlebuild.distribution.implementation-java")
}

description = "Internal API to declare services provided by Gradle modules"

gradleModule {
    targetRuntimes {
        usedInWorkers = true
    }
}

dependencies {
    api(projects.serviceLookup)
    api(projects.stdlibJavaExtensions)

    api(libs.errorProneAnnotations)

    compileOnly(libs.jspecify)
}

errorprone {
    nullawayEnabled = true
}
