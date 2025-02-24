plugins {
    id("gradlebuild.distribution.implementation-java")
}

description = "Classes required to implement Scala compiler workers. " +
    "These classes are loaded in a separate worker daemon process and should have a minimal dependency set."

dependencies {
    api(projects.classloaders)
    api(projects.hashing)
    api(projects.javaCompilerWorker)
    api(projects.jvmCompilerWorker)
    api(projects.persistentCache)

    api(projects.coreApi) {
        because("Compiler and WorkResult. We should migrate away from these interfaces.")
    }

    api(libs.inject)
    api(libs.jsr305)

    implementation(projects.baseServices)
    implementation(projects.daemonServerWorker)
    implementation(projects.stdlibJavaExtensions)
    implementation(projects.time)

    implementation(libs.guava)
    implementation(libs.slf4jApi)

    compileOnly(libs.zinc) {
        because("The scala implementation is provided by the user at runtime")

        // TODO: Use component metadata rules instead.
        //       See gradlebuild.dependency-modules.gradle.kts
        exclude(module="log4j-core") // Because not needed and was vulnerable
        exclude(module="log4j-api") // Because not needed and was vulnerable
    }
}

dependencyAnalysis {
    issues {
        onUsedTransitiveDependencies {
            // These are compileOnly transitive dependencies that are needed by the Scala compiler
            exclude("org.scala-sbt:compiler-interface")
            exclude("org.scala-sbt:util-interface")
            exclude("org.scala-sbt:zinc-classpath_2.13")
            exclude("org.scala-lang:scala-library")
            exclude("org.scala-sbt:io_2.13")
            exclude("org.scala-sbt:util-logging_2.13")
            exclude("org.scala-sbt:util-relation_2.13")
            exclude("org.scala-sbt:zinc-compile-core_2.13")
            exclude("org.scala-sbt:zinc-core_2.13")
            exclude("org.scala-sbt:zinc-persist_2.13")
        }
    }
}
