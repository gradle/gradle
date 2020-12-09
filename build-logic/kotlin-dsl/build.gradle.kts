plugins {
    id("gradlebuild.build-logic.kotlin-dsl-gradle-plugin")
}

dependencies {
    implementation(project(":basics"))
    implementation(project(":dependency-modules"))
    implementation(project(":jvm"))

    implementation("org.gradle.kotlin:gradle-kotlin-dsl-conventions")
    implementation(kotlin("gradle-plugin"))
    implementation(kotlin("sam-with-receiver"))
    implementation("org.ow2.asm:asm")

    testImplementation("junit:junit")
    testImplementation("com.nhaarman:mockito-kotlin")
}
