plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Contains declarations for instrumentation code, like interceptors etc."

dependencies {
    compileOnly(project(":core"))
    compileOnly(project(":base-services"))
    compileOnly(project(":core-api"))
    compileOnly(project(":model-core"))
    compileOnly(project(":reporting"))
    compileOnly(libs.groovy)
    implementation(project(":code-quality"))

    // Instrumentation dependencies
    compileOnly(project(":internal-instrumentation-api"))
    compileOnly(libs.asm)
    compileOnly(libs.asmUtil)
    compileOnly(libs.asmTree)
    annotationProcessor(project(":internal-instrumentation-processor"))
    annotationProcessor(platform(project(":distributions-dependencies")))
}

tasks.named<JavaCompile>("compileJava") {
    // Without this, javac will complain about unclaimed annotations
    options.compilerArgs.add("-Xlint:-processing")
}
