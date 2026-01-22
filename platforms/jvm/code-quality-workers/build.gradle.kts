plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Implements execution of code quality tools within a separate worker process"

dependencies {
    api(projects.antWorker)
    api(projects.baseServices)
    api(projects.core)
    api(projects.coreApi)
    api(libs.jspecify)

    implementation(projects.daemonServerWorker)
    implementation(projects.logging)
    implementation(projects.stdlibJavaExtensions)
    implementation(libs.guava)
    implementation(libs.commonsIo)
    implementation(libs.groovy)
    implementation(libs.groovyXml)
    implementation(libs.slf4jApi)
}
