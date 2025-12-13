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

    implementation("org.codenarc:CodeNarc") {
        exclude(group = "org.apache.groovy")
        exclude(group = "org.codehaus.groovy")
    }
    implementation("com.github.javaparser:javaparser-symbol-solver-core") {
        exclude(group = "com.google.guava")
    }
    implementation(kotlin("gradle-plugin"))
    compileOnly(kotlin("compiler-embeddable") as String) {
        because("Required by IncubatingApiReportTask")
    }
    implementation("com.gradle:develocity-gradle-plugin") {
        because("Arch-test plugin configures the PTS extension")
    }

    testImplementation("org.junit.jupiter:junit-jupiter-engine")
    testImplementation("org.apache.commons:commons-lang3")
}
