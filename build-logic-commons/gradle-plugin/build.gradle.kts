import org.gradle.kotlin.dsl.support.expectedKotlinDslPluginsVersion

plugins {
    `kotlin-dsl`
}

group = "gradlebuild"

description = "Provides plugins used to create a Gradle plugin with Groovy or Kotlin DSL within build-logic builds"

dependencies {
    compileOnly(buildLibs.develocityPlugin)

    api(platform(projects.buildPlatform))

    implementation(projects.basics)
    implementation(projects.moduleIdentity)

    implementation(buildLibs.errorPronePlugin)
    implementation(buildLibs.nullawayPlugin)
    implementation("org.gradle.kotlin.kotlin-dsl:org.gradle.kotlin.kotlin-dsl.gradle.plugin:$expectedKotlinDslPluginsVersion")
    implementation(buildLibs.kgp)
    implementation(buildLibs.testRetryPlugin)
    implementation(buildLibs.detektPlugin)
}
