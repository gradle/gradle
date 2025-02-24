plugins {
    id("gradlebuild.distribution.implementation-java")
}

description = "Classes required to implement Groovy compiler workers. " +
    "These classes are loaded in a separate worker daemon process and should have a minimal dependency set."

dependencies {
    api(projects.baseServices)
    api(projects.javaCompilerWorker)
    api(projects.jvmCompilerWorker)
    api(projects.problemsApi)

    api(projects.coreApi) {
        because("Compiler and WorkResult. We should migrate away from these interfaces.")
    }

    api(libs.inject)
    api(libs.jsr305)

    implementation(projects.baseAsm)
    implementation(projects.classloaders)
    implementation(projects.concurrent)
    implementation(projects.stdlibJavaExtensions)

    implementation(libs.asm)
    implementation(libs.guava)

    compileOnly(libs.groovy) {
        because("The groovy implementation is provided by the user at runtime")
    }
}
