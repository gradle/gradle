plugins {
    id("gradlebuild.distribution.api-java")
    id("gradlebuild.instrumented-java-project")
}

description = "Plugins for building Scala code with Gradle."

errorprone {
    disabledChecks.addAll(
        "UnusedMethod", // 2 occurrences
    )
}

dependencies {
    api(projects.stdlibJavaExtensions)
    api(projects.baseServices)
    api(projects.core)
    api(projects.coreApi)
    api(projects.files)
    api(projects.hashing)
    api(projects.languageJava)
    api(projects.languageJvm)
    api(projects.loggingApi)
    api(projects.modelCore)
    api(projects.platformBase)
    api(projects.platformJvm)
    api(projects.toolchainsJvm)
    api(projects.toolchainsJvmShared)
    api(projects.workers)
    api(projects.buildProcessServices)

    api(libs.groovy)
    api(libs.inject)
    api(libs.jsr305)

    implementation(projects.time)
    implementation(projects.serviceLookup)
    implementation(projects.dependencyManagement)
    implementation(projects.fileCollections)
    implementation(projects.logging)
    implementation(projects.persistentCache)
    implementation(projects.pluginsJava)
    implementation(projects.pluginsJavaBase)
    implementation(projects.reporting)
    implementation(projects.workerMain)

    implementation(libs.guava)

    compileOnly(libs.zinc) {
        // Because not needed and was vulnerable
        exclude(module="log4j-core")
        exclude(module="log4j-api")
    }

    testImplementation(projects.baseServicesGroovy)
    testImplementation(projects.files)
    testImplementation(projects.resources)
    testImplementation(libs.slf4jApi)
    testImplementation(libs.commonsIo)
    testImplementation(testFixtures(projects.core))
    testImplementation(testFixtures(projects.pluginsJava))
    testImplementation(testFixtures(projects.languageJvm))
    testImplementation(testFixtures(projects.languageJava))

    integTestImplementation(projects.jvmServices)

    testFixturesImplementation(testFixtures(projects.languageJvm))

    testRuntimeOnly(projects.distributionsCore) {
        because("ProjectBuilder tests load services from a Gradle distribution.")
    }
    integTestDistributionRuntimeOnly(projects.distributionsJvm)
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

packageCycles {
    excludePatterns.add("org/gradle/api/internal/tasks/scala/**")
    excludePatterns.add("org/gradle/api/tasks/*")
    excludePatterns.add("org/gradle/api/tasks/scala/internal/*")
    excludePatterns.add("org/gradle/language/scala/tasks/*")
}

integTest.usesJavadocCodeSnippets = true
