plugins {
    id("gradlebuild.distribution.implementation-java")
    id("gradlebuild.publish-public-libraries")
}

description = "Tools to take immutable, comparable snapshots of files and other things"

gradlebuildJava.usedInWorkers()

dependencies {
    api(projects.hashing)
    api(projects.stdlibJavaExtensions)

    api(libs.jspecify)
}
