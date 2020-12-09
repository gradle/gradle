plugins {
    `kotlin-dsl`
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

group = "gradlebuild"

kotlinDslPluginOptions.experimentalWarning.set(false)

dependencies {
    implementation("org.gradle.kotlin.kotlin-dsl:org.gradle.kotlin.kotlin-dsl.gradle.plugin:1.4.4")
    implementation("org.gradle.kotlin:gradle-kotlin-dsl-conventions:0.6.0")
}
