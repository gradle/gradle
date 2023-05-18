plugins {
    id("gradlebuild.build-logic.kotlin-dsl-gradle-plugin")
}

description = "Provides plugins to configure quality checks (incubating report, CodeNarc, et al)"

dependencies {
    implementation(project(":basics"))
    implementation(project(":cleanup"))
    implementation(project(":documentation"))
    implementation(project(":integration-testing"))
    implementation(project(":performance-testing"))
    implementation(project(":profiling"))
    implementation(project(":binary-compatibility"))

    implementation("org.codenarc:CodeNarc") {
        exclude(group = "org.apache.groovy")
        exclude(group = "org.codehaus.groovy")
    }
    implementation("com.github.javaparser:javaparser-symbol-solver-core") {
        exclude(group = "com.google.guava")
    }
    implementation("org.gradle.kotlin:gradle-kotlin-dsl-conventions")
    implementation(kotlin("gradle-plugin"))
    implementation(kotlin("compiler-embeddable") as String) {
        because("Required by IncubatingApiReportTask")
    }
    implementation("com.gradle:gradle-enterprise-gradle-plugin") {
        because("Arch-test plugin configures the PTS extension")
    }

    testImplementation("org.junit.jupiter:junit-jupiter-engine")
}
