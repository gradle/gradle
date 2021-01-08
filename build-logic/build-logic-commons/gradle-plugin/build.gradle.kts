plugins {
    `kotlin-dsl`
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

dependencies {
    implementation(project(":code-quality"))

    implementation("org.gradle.kotlin.kotlin-dsl:org.gradle.kotlin.kotlin-dsl.gradle.plugin:2.1.4")
    implementation("org.gradle.kotlin:gradle-kotlin-dsl-conventions:0.7.0")
}
