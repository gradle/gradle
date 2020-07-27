dependencies {
    implementation(project(":basics"))
    implementation(project(":binaryCompatibility"))
    implementation(project(":buildquality"))
    implementation(project(":cleanup"))
    implementation(project(":dependencyModules"))
    implementation(project(":jvm"))
    implementation(project(":profiling"))
    implementation(project(":publishing"))

    implementation("org.gradle.kotlin:gradle-kotlin-dsl-conventions")
    implementation(kotlin("gradle-plugin"))
}
