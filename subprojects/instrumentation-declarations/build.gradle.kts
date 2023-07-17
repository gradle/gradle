plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Contains declarations for instrumentation of plugins. Adds interceptors, bytecode upgrades etc."

dependencies {
    // All dependencies should be compileOnly, since this project is added also to worker classpath, so we don't pollute it.
    // If we need some dependency also at runtime we need to build a separate classpath and add it to :launcher project or :distributions-core project directly.
    compileOnly(project(":core"))
    compileOnly(project(":base-services"))
    compileOnly(project(":core-api"))
    compileOnly(project(":model-core"))
    compileOnly(project(":reporting"))
    compileOnly(libs.groovy)
    compileOnly(project(":code-quality"))
    implementation(project(":language-jvm"))
    implementation(project(":language-java"))

    // Instrumentation dependencies
    compileOnly(project(":internal-instrumentation-api"))
    compileOnly(libs.asm)
    compileOnly(libs.asmUtil)
    compileOnly(libs.asmTree)
    annotationProcessor(project(":internal-instrumentation-processor"))
    annotationProcessor(platform(project(":distributions-dependencies")))
}

tasks.named<JavaCompile>("compileJava") {
    // Without this, javac will complain about unclaimed org.gradle.api.NonNullApi annotation
    options.compilerArgs.add("-Xlint:-processing")
}
