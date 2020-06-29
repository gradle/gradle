dependencies {
    implementation(project(":basics"))
    implementation(project(":binaryCompatibility"))
    implementation(project(":buildquality"))
    implementation(project(":cleanup"))
    implementation(project(":dependencyModules"))
    implementation(project(":kotlinDsl"))
    implementation(project(":jvm"))
    implementation(project(":packaging"))
    implementation(project(":profiling"))
    implementation(project(":publishing"))

    implementation("org.gradle.kotlin:gradle-kotlin-dsl-conventions")
    implementation(kotlin("gradle-plugin"))
}
