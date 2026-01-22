plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Implements execution of code quality tools within a separate worker process"

dependencies {
    api(projects.antWorker)
    api(projects.baseServices)
    api(projects.core)
    api(projects.coreApi)
    api(projects.stdlibJavaExtensions)
    api("org.jspecify:jspecify")

    implementation(projects.daemonServerWorker)
    implementation(projects.logging)
    implementation("com.google.guava:guava")
    implementation("commons-io:commons-io")
    implementation("org.apache.groovy:groovy")
    implementation("org.apache.groovy:groovy-xml")
    implementation("org.slf4j:slf4j-api")
}
