plugins {
    id("gradlebuild.distribution.api-java")
    id("gradlebuild.publish-public-libraries")
}

description = "Common shared build cache classes"

dependencies {
    compileOnly(projects.stdlibJavaExtensions)

    api(projects.files)
    api(projects.hashing)

    testImplementation(testFixtures(projects.hashing))
}
