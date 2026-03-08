plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Process execution public API types."

dependencies {
    api(projects.stdlibJavaExtensions)

    api(libs.jspecify)

    compileOnly(projects.internalInstrumentationApi)
}
