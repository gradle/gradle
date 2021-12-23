plugins {
    id("gradlebuild.build-logic.kotlin-dsl-gradle-plugin")
}

description = "Provides plugins to configure quality checks (incubating report, CodeNarc, et al)"

dependencies {
    implementation(project(":basics"))
    implementation(project(":binary-compatibility"))
    implementation(project(":cleanup"))
    implementation(project(":documentation"))
    implementation(project(":integration-testing"))
    implementation(project(":performance-testing"))
    implementation(project(":profiling"))

    implementation("me.champeau.gradle:japicmp-gradle-plugin")
    implementation("org.codenarc:CodeNarc") {
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

    testImplementation("org.junit.jupiter:junit-jupiter-engine")
}
