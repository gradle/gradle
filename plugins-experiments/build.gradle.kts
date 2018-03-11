import build.futureKotlin
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("kotlin-dsl-plugin-bundle")
    id("com.github.johnrengelman.shadow") version "2.0.2"
}

base {
    archivesBaseName = "gradle-kotlin-dsl-plugins-experiments"
}

repositories {
    gradlePluginPortal()
}

dependencies {
    compileOnly(gradleKotlinDsl())

    implementation("gradle.plugin.org.jlleitschuh.gradle:ktlint-gradle:3.1.0")
    implementation(futureKotlin("stdlib-jdk8"))

    testImplementation(project(":test-fixtures"))
}


// plugins ------------------------------------------------------------

kotlinDslPlugins {
    create("ktlintConvention") {
        displayName = "Gradle Kotlin DSL ktlint convention plugin (experimental)"
        id = "org.gradle.kotlin.ktlint-convention"
        implementationClass = "org.gradle.kotlin.dsl.experiments.plugins.GradleKotlinDslKtlintConventionPlugin"
    }
}


// default versions ---------------------------------------------------

val ktlintVersion = "0.19.0"

val basePackagePath = "org/gradle/kotlin/dsl/experiments/plugins"
val processResources: ProcessResources by tasks
val writeDefaultVersionsProperties by tasks.creating(WriteProperties::class) {
    outputFile = processResources.destinationDir.resolve("$basePackagePath/default-versions.properties")
    property("ktlint", ktlintVersion)
}
processResources.dependsOn(writeDefaultVersionsProperties)


// ktlint custom ruleset ----------------------------------------------

java.sourceSets {
    "ruleset"()
}

val rulesetShaded by configurations.creating
val rulesetCompileOnly by configurations.getting {
    extendsFrom(rulesetShaded)
}

val generatedResourcesRulesetJarDir = file("$buildDir/generated-resources/ruleset/resources")
val rulesetJar by tasks.creating(ShadowJar::class) {
    archiveName = "gradle-kotlin-dsl-ruleset.jar"
    destinationDir = generatedResourcesRulesetJarDir.resolve(basePackagePath)
    configurations = listOf(rulesetShaded)
    from(java.sourceSets["ruleset"].output)
}
java.sourceSets["main"].output.dir(mapOf("builtBy" to rulesetJar), generatedResourcesRulesetJarDir)

artifacts {
    add("archives", rulesetJar)
}

dependencies {
    rulesetShaded("com.github.shyiko.ktlint:ktlint-ruleset-standard:$ktlintVersion") {
        isTransitive = false
    }
    rulesetCompileOnly("com.github.shyiko.ktlint:ktlint-core:$ktlintVersion")
}
