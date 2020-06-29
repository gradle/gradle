dependencies {
    implementation(project(":basics"))
    implementation(project(":binaryCompatibility"))
    implementation(project(":cleanup"))
    implementation(project(":docs"))
    implementation(project(":integrationTesting"))
    implementation(project(":performance"))
    implementation(project(":profiling"))

    implementation("me.champeau.gradle:japicmp-gradle-plugin")
    implementation("org.codenarc:CodeNarc") {
        exclude(group = "org.codehaus.groovy")
    }
    implementation("com.github.javaparser:javaparser-symbol-solver-core")
    implementation("org.gradle.kotlin:gradle-kotlin-dsl-conventions")
    implementation(kotlin("compiler-embeddable") as String) {
        because("Required by IncubatingApiReportTask")
    }
}
