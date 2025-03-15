plugins {
    id("gradlebuild.distribution.api-java")
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
    api(libs.jspecify)

    implementation(projects.baseAsm)
    implementation(projects.baseServicesGroovy)
    implementation(projects.classloaders)
    implementation(projects.concurrent)
    implementation(projects.stdlibJavaExtensions)

    implementation(libs.asm)
    implementation(libs.guava)

    compileOnly(libs.groovy) {
        because("The groovy implementation is provided by the user at runtime")
    }
}

// The classpath of the Groovy compiler worker is generated based on the
// runtime classpath of this project. Prevent Gradle's groovy dependency from
// leaking onto the worker classpath.
configurations.runtimeClasspath.configure {
    exclude(group=libs.groovyGroup)
}
