import build.futureKotlin
import plugins.bundledGradlePlugin
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.internal.hash.Hashing

plugins {
    `kotlin-dsl-plugin-bundle`
    id("com.github.johnrengelman.shadow") version "2.0.4" apply false
}

base {
    archivesBaseName = "gradle-kotlin-dsl-plugins-experiments"
}

repositories {
    gradlePluginPortal()
}

dependencies {
    compileOnly(project(":provider"))

    implementation("gradle.plugin.org.jlleitschuh.gradle:ktlint-gradle:5.0.0")
    implementation(futureKotlin("stdlib-jdk8"))

    testImplementation(project(":test-fixtures"))
}


// plugins ------------------------------------------------------------

bundledGradlePlugin(
    name = "ktlintConvention",
    shortDescription = "Gradle Kotlin DSL ktlint convention plugin (experimental)",
    pluginId = "org.gradle.kotlin.ktlint-convention",
    pluginClass = "org.gradle.kotlin.dsl.experiments.plugins.GradleKotlinDslKtlintConventionPlugin")


// default versions ---------------------------------------------------

val ktlintVersion = "0.28.0"

val basePackagePath = "org/gradle/kotlin/dsl/experiments/plugins"
val processResources by tasks.existing(ProcessResources::class)
val writeDefaultVersionsProperties by tasks.registering(WriteProperties::class) {
    outputFile = processResources.get().destinationDir.resolve("$basePackagePath/default-versions.properties")
    property("ktlint", ktlintVersion)
}
processResources {
    dependsOn(writeDefaultVersionsProperties)
}


// ktlint custom ruleset ----------------------------------------------

val ruleset by sourceSets.creating
val rulesetShaded by configurations.creating
val rulesetCompileOnly by configurations.getting {
    extendsFrom(rulesetShaded)
}

val generatedResourcesRulesetJarDir = file("$buildDir/generated-resources/ruleset/resources")
val rulesetJar by tasks.registering(ShadowJar::class) {
    archiveName = "gradle-kotlin-dsl-ruleset.jar"
    destinationDir = generatedResourcesRulesetJarDir.resolve(basePackagePath)
    configurations = listOf(rulesetShaded)
    from(ruleset.output)
}
val rulesetChecksum by tasks.registering {
    dependsOn(rulesetJar)
    val rulesetChecksumFile = generatedResourcesRulesetJarDir
        .resolve(basePackagePath)
        .resolve("gradle-kotlin-dsl-ruleset.md5")
    val archivePath = rulesetJar.get().archivePath
    inputs.file(archivePath)
    outputs.file(rulesetChecksumFile)
    doLast {
        rulesetChecksumFile.parentFile.mkdirs()
        rulesetChecksumFile.writeText(Hashing.md5().hashBytes(archivePath.readBytes()).toString())
    }
}
sourceSets["main"].output.dir(generatedResourcesRulesetJarDir, "builtBy" to listOf(rulesetJar, rulesetChecksum))

dependencies {
    rulesetShaded("com.github.shyiko.ktlint:ktlint-ruleset-standard:$ktlintVersion") {
        isTransitive = false
    }
    rulesetCompileOnly("com.github.shyiko.ktlint:ktlint-core:$ktlintVersion")
    rulesetCompileOnly(futureKotlin("reflect"))
}
