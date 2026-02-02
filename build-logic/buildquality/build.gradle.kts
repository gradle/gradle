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
        because("Required by IncubatingApiReportTask")
    }
    implementation(buildLibs.develocityPlugin) {
        because("Arch-test plugin configures the PTS extension")
    }

    testImplementation(testLibs.junit5JupiterEngine)
    testImplementation(buildLibs.commonsLang3)
}
