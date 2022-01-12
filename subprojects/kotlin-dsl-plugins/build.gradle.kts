plugins {
    id("gradlebuild.portalplugin.kotlin")
    id("gradlebuild.kotlin-dsl-plugin-extensions")
}

description = "Kotlin DSL Gradle Plugins deployed to the Plugin Portal"

group = "org.gradle.kotlin"
version = "2.2.1"

base.archivesBaseName = "plugins"

dependencies {
    compileOnly(project(":base-services"))
    compileOnly(project(":logging"))
    compileOnly(project(":core-api"))
    compileOnly(project(":model-core"))
    compileOnly(project(":core"))
    compileOnly(project(":language-jvm"))
    compileOnly(project(":language-java"))
    compileOnly(project(":plugins"))
    compileOnly(project(":plugin-development"))
    compileOnly(project(":kotlin-dsl"))

    compileOnly(libs.slf4jApi)
    compileOnly(libs.inject)

    implementation(libs.futureKotlin("stdlib-jdk8"))
    implementation(libs.futureKotlin("gradle-plugin"))
    implementation(libs.futureKotlin("sam-with-receiver"))

    integTestImplementation(project(":base-services"))
    integTestImplementation(project(":logging"))
    integTestImplementation(project(":core-api"))
    integTestImplementation(project(":model-core"))
    integTestImplementation(project(":core"))
    integTestImplementation(project(":plugins"))

    integTestImplementation(project(":platform-jvm"))
    integTestImplementation(project(":kotlin-dsl"))
    integTestImplementation(project(":internal-testing"))
    integTestImplementation(testFixtures(project(":kotlin-dsl")))

    integTestImplementation(libs.slf4jApi)
    integTestImplementation(libs.mockitoKotlin)

    integTestDistributionRuntimeOnly(project(":distributions-basics")) {
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
