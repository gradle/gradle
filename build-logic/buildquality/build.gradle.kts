plugins {
    id("gradlebuild.build-logic.kotlin-dsl-gradle-plugin")
}

description = "Provides plugins to configure quality checks (incubating report, CodeNarc, et al)"

dependencies {
    implementation("gradlebuild:basics")

    implementation(projects.cleanup)
    implementation(projects.documentation)
    implementation(projects.integrationTesting)
    implementation(projects.jvm)
    implementation(projects.performanceTesting)
    implementation(projects.profiling)
    implementation(projects.binaryCompatibility)
    implementation(projects.dependencyModules)

    implementation(buildLibs.codenarc) {
        exclude(group = "org.apache.groovy")
        exclude(group = "org.codehaus.groovy")
    }
    implementation(buildLibs.javaParserSymbolSolver) {
        exclude(group = "com.google.guava")
    }
    implementation(buildLibs.kgp)
    compileOnly(buildLibs.kotlinCompilerEmbeddable) {
        because("Required by IncubatingApiReportTask and Gradle10RemovalReportTask")
    }
    implementation(buildLibs.develocityPlugin) {
        because("Arch-test plugin configures the PTS extension")
    }

    testImplementation(buildLibs.commonsLang3)
    testImplementation(buildLibs.kotlinCompilerEmbeddable) {
        because("RemovalReportWorkActionTest parses Kotlin sources via KotlinSourceParser")
    }
}

configurations.testRuntimeClasspath {
    // https://youtrack.jetbrains.com/issue/KT-87492/KGP-jar-bundles-a-partial-and-unshaded-copy-of-org.jetbrains.kotlin.com.intellij.-classes-that-collide-with-kotlin-compiler
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-gradle-plugin")
}
