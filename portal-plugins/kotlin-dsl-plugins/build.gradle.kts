plugins {
    id("gradlebuild.portalplugin.kotlin")
    id("gradlebuild.kotlin-dsl-plugin-extensions")
}

description = "Kotlin DSL Gradle Plugins deployed to the Plugin Portal"

group = "org.gradle.kotlin"
version = "2.1.5"

base.archivesBaseName = "plugins"

dependencies {
    compileOnly("org.gradle:base-services")
    compileOnly("org.gradle:logging")
    compileOnly("org.gradle:core-api")
    compileOnly("org.gradle:model-core")
    compileOnly("org.gradle:core")
    compileOnly("org.gradle:language-jvm")
    compileOnly("org.gradle:language-java")
    compileOnly("org.gradle:plugins")
    compileOnly("org.gradle:plugin-development")
    compileOnly("org.gradle:kotlin-dsl")

    compileOnly(libs.slf4jApi)
    compileOnly(libs.inject)

    implementation(libs.futureKotlin("stdlib-jdk8"))
    implementation(libs.futureKotlin("gradle-plugin"))
    implementation(libs.futureKotlin("sam-with-receiver"))

    integTestImplementation("org.gradle:base-services")
    integTestImplementation("org.gradle:logging")
    integTestImplementation("org.gradle:core-api")
    integTestImplementation("org.gradle:model-core")
    integTestImplementation("org.gradle:core")
    integTestImplementation("org.gradle:plugins")

    integTestImplementation("org.gradle:platform-jvm")
    integTestImplementation("org.gradle:kotlin-dsl")
    integTestImplementation("org.gradle:internal-testing")
    integTestImplementation(testFixtures("org.gradle:kotlin-dsl"))

    integTestImplementation(libs.slf4jApi)
    integTestImplementation(libs.mockitoKotlin)

    integTestDistributionRuntimeOnly("org.gradle:distributions-basics") {
        because("KotlinDslPluginTest tests against TestKit")
    }
    integTestLocalRepository(project)
}

classycle {
    excludePatterns.add("org/gradle/kotlin/dsl/plugins/base/**")
}

testFilesCleanup.reportOnly.set(true)

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
