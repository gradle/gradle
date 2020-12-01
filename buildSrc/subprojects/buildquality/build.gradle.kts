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
    implementation("com.github.javaparser:javaparser-symbol-solver-core")
    implementation("org.gradle.kotlin:gradle-kotlin-dsl-conventions")
    implementation(kotlin("gradle-plugin"))
    implementation("com.diffplug.spotless:spotless-plugin-gradle") {
        exclude(group = "org.codehaus.groovy", module = "groovy-xml")
    }
    implementation(kotlin("compiler-embeddable") as String) {
        because("Required by IncubatingApiReportTask")
    }
}
