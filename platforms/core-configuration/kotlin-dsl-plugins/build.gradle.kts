plugins {
    id("gradlebuild.portalplugin.kotlin")
    id("gradlebuild.kotlin-dsl-plugin-extensions")
}

description = "Kotlin DSL Gradle Plugins deployed to the Plugin Portal"

group = "org.gradle.kotlin"
version = "5.1.1"

base.archivesName = "plugins"

dependencies {
    compileOnly(projects.baseServices)
    compileOnly(projects.logging)
    compileOnly(projects.coreApi)
    compileOnly(projects.modelCore)
    compileOnly(projects.core)
    compileOnly(projects.languageJvm)
    compileOnly(projects.languageJava)
    compileOnly(projects.loggingApi)
    compileOnly(projects.platformJvm)
    compileOnly(projects.pluginDevelopment)
    compileOnly(projects.kotlinDsl)
    compileOnly(projects.serviceLookup)
    compileOnly(projects.stdlibJavaExtensions)

    compileOnly(libs.slf4jApi)
    compileOnly(libs.inject)

    api(libs.kotlinStdlib)


    implementation(libs.futureKotlin("gradle-plugin"))
    implementation(libs.futureKotlin("gradle-plugin-api"))
    implementation(libs.futureKotlin("sam-with-receiver"))
    implementation(libs.futureKotlin("assignment"))


    testImplementation(projects.logging)
    testImplementation(testFixtures(projects.kotlinDsl))
    testImplementation(libs.slf4jApi)
    testImplementation(libs.kotlinReflect) {
        because("mockito-kotlin 1.6 requires kotlin-reflect in 1.0.7, we want to overrule that")
    }
    testImplementation(libs.mockitoKotlin)
}

packageCycles {
    excludePatterns.add("org/gradle/kotlin/dsl/plugins/base/**")
    excludePatterns.add("org/gradle/kotlin/dsl/plugins/precompiled/**")
}

testFilesCleanup.reportOnly = true

pluginPublish {
    bundledGradlePlugin(
        name = "embeddedKotlin",
        shortDescription = "Embedded Kotlin Gradle Plugin",
        pluginId = "org.gradle.kotlin.embedded-kotlin",
        pluginClass = "org.gradle.kotlin.dsl.plugins.embedded.EmbeddedKotlinPlugin"
    )

    bundledGradlePlugin(
        name = "kotlinDsl",
        shortDescription = "Gradle Kotlin DSL Plugin",
        pluginId = "org.gradle.kotlin.kotlin-dsl",
        pluginClass = "org.gradle.kotlin.dsl.plugins.dsl.KotlinDslPlugin"
    )

    bundledGradlePlugin(
        name = "kotlinDslBase",
        shortDescription = "Gradle Kotlin DSL Base Plugin",
        pluginId = "org.gradle.kotlin.kotlin-dsl.base",
        pluginClass = "org.gradle.kotlin.dsl.plugins.base.KotlinDslBasePlugin"
    )

    bundledGradlePlugin(
        name = "kotlinDslCompilerSettings",
        shortDescription = "Gradle Kotlin DSL Compiler Settings",
        pluginId = "org.gradle.kotlin.kotlin-dsl.compiler-settings",
        pluginClass = "org.gradle.kotlin.dsl.plugins.dsl.KotlinDslCompilerPlugins"
    )

    bundledGradlePlugin(
        name = "kotlinDslPrecompiledScriptPlugins",
        shortDescription = "Gradle Kotlin DSL Precompiled Script Plugins",
        pluginId = "org.gradle.kotlin.kotlin-dsl.precompiled-script-plugins",
        pluginClass = "org.gradle.kotlin.dsl.plugins.precompiled.PrecompiledScriptPlugins"
    )
}
