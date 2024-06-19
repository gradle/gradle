plugins {
    id("gradlebuild.distribution.implementation-java")
}

description = "Implementation of the internal Service Provider framework"

gradlebuildJava.usedInWorkers()

// TODO: check is this is required for DefaultServiceRegistry tests
/**
 * Use Java 8 compatibility for Unit tests, so we can test Java 8 features as well
 */
//tasks.named<JavaCompile>("compileTestJava") {
//    options.release = 8
//}

dependencies {
    api(projects.concurrent)
    api(projects.serviceProvider)
    api(projects.stdlibJavaExtensions)

    api(libs.errorProneAnnotations)
}

