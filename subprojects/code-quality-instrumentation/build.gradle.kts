plugins {
    id("gradlebuild.distribution.api-java")
}

description = "A code quality instrumentation for property upgrades"

dependencies {
    compileOnly(project(":core"))
    compileOnly(project(":core-api"))
    compileOnly(project(":model-core"))
    compileOnly(project(":reporting"))
    compileOnly(project(":code-quality"))

    // Instrumentation dependencies
    compileOnly(project(":internal-instrumentation-api"))
    compileOnly(libs.asm)
    annotationProcessor(project(":internal-instrumentation-processor"))
    annotationProcessor(platform(project(":distributions-dependencies")))
}

tasks.named<JavaCompile>("compileJava") {
    // Without this, javac will complain about unclaimed annotations
    options.compilerArgs.add("-Xlint:-processing")
}
