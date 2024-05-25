plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Plugins for building Scala code with Gradle."

errorprone {
    disabledChecks.addAll(
        "UnusedMethod", // 2 occurrences
    )
}

dependencies {
    api(projects.javaLanguageExtensions)
    api(project(":base-services"))
    api(project(":core"))
    api(project(":core-api"))
    api(project(":files"))
    api(project(":hashing"))
    api(project(":language-java"))
    api(project(":language-jvm"))
    api(project(":logging-api"))
    api(project(":model-core"))
    api(project(":platform-base"))
    api(project(":platform-jvm"))
    api(project(":toolchains-jvm"))
    api(project(":toolchains-jvm-shared"))
    api(project(":workers"))
    api(project(":build-process-services"))

    api(libs.groovy)
    api(libs.inject)
    api(libs.jsr305)

    implementation(projects.time)
    implementation(project(":dependency-management"))
    implementation(project(":file-collections"))
    implementation(project(":logging"))
    implementation(project(":persistent-cache"))
    implementation(project(":plugins-java"))
    implementation(project(":plugins-java-base"))
    implementation(project(":reporting"))
    implementation(project(":worker-main"))

    implementation(libs.guava)

    compileOnly(libs.zinc) {
        // Because not needed and was vulnerable
        exclude(module="log4j-core")
        exclude(module="log4j-api")
    }

    testImplementation(project(":base-services-groovy"))
    testImplementation(project(":files"))
    testImplementation(project(":resources"))
    testImplementation(libs.slf4jApi)
    testImplementation(libs.commonsIo)
    testImplementation(testFixtures(project(":core")))
    testImplementation(testFixtures(project(":plugins-java")))
    testImplementation(testFixtures(project(":language-jvm")))
    testImplementation(testFixtures(project(":language-java")))

    integTestImplementation(project(":jvm-services"))

    testFixturesImplementation(testFixtures(project(":language-jvm")))

    testRuntimeOnly(project(":distributions-core")) {
        because("ProjectBuilder tests load services from a Gradle distribution.")
    }
    integTestDistributionRuntimeOnly(project(":distributions-jvm"))
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

// Remove as part of fixing https://github.com/gradle/configuration-cache/issues/585
tasks.configCacheIntegTest {
    systemProperties["org.gradle.configuration-cache.internal.test-disable-load-after-store"] = "true"
}
