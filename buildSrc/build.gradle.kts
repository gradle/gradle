/*
 * Copyright 2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.util.*

plugins {
    `java`
    `kotlin-dsl` apply false
    id("org.gradle.kotlin-dsl.ktlint-convention") version "0.4.1" apply false
}

subprojects {
    if (name != "buildPlatform") {
        apply(plugin = "java-library")


        if (file("src/main/groovy").isDirectory || file("src/test/groovy").isDirectory) {
            applyGroovyProjectConventions()
        }

        if (file("src/main/kotlin").isDirectory || file("src/test/kotlin").isDirectory) {
            applyKotlinProjectConventions()
        }

        java {
            sourceCompatibility = JavaVersion.VERSION_1_8
            targetCompatibility = JavaVersion.VERSION_1_8
        }

        dependencies {
            "api"(platform(project(":buildPlatform")))
            implementation(gradleApi())
        }

        afterEvaluate {
            if (tasks.withType<ValidatePlugins>().isEmpty()) {
                val validatePlugins by tasks.registering(ValidatePlugins::class) {
                    outputFile.set(project.reporting.baseDirectory.file("task-properties/report.txt"))

                    val mainSourceSet = project.sourceSets.main.get()
                    classes.setFrom(mainSourceSet.output.classesDirs)
                    dependsOn(mainSourceSet.output)
                    classpath.setFrom(mainSourceSet.runtimeClasspath)
                }
                tasks.check { dependsOn(validatePlugins) }
            }
        }

        tasks.withType<ValidatePlugins> {
            failOnWarning.set(true)
            enableStricterValidation.set(true)
        }

        apply(from = "../../../gradle/shared-with-buildSrc/code-quality-configuration.gradle.kts")
    }
    apply(plugin = "eclipse")
}

allprojects {
    repositories {
        maven {
            name = "Gradle libs"
            url = uri("https://repo.gradle.org/gradle/libs")
            mavenContent {
                // This repository contains an older version which has been overwritten in Central
                excludeModule("com.google.j2objc", "j2objc-annotations")
            }
        }
        gradlePluginPortal()
        maven {
            name = "Gradle snapshot libs"
            url = uri("https://repo.gradle.org/gradle/libs-snapshots")
            mavenContent {
                // This repository contains an older version which has been overwritten in Central
                excludeModule("com.google.j2objc", "j2objc-annotations")
            }
        }
        maven {
            name = "kotlinx"
            url = uri("https://dl.bintray.com/kotlin/kotlinx")
        }
        maven {
            name = "kotlin-dev"
            url = uri("https://dl.bintray.com/kotlin/kotlin-dev")
        }
    }
}

dependencies {
    subprojects.forEach {
        runtimeOnly(project(it.path))
    }
}


// TODO Avoid duplication of what defines a CI Server with BuildEnvironment
val isCiServer: Boolean by extra { "CI" in System.getenv() }


/**
 * Controls whether verification tasks are skipped.
 *
 * Set the `buildSrcCheck` Gradle property to `true` to run the verification tasks.
 * Set it to `false` to skip the verification tasks.
 *
 * When that property is unset, defaults to `false` on CI, to `true` otherwise.
 */
val isSkipBuildSrcVerification: Boolean =
    (findProperty("buildSrcCheck") as String?)
        ?.let { it == "false" }
        ?: !isCiServer

if (isSkipBuildSrcVerification) {
    allprojects {
        afterEvaluate {
            plugins.withId("lifecycle-base") {
                tasks.named("check") {
                    setDependsOn(listOf("assemble"))
                }
            }
        }
    }
}

if (isCiServer) {
    gradle.buildFinished {
        allprojects.forEach { project ->
            project.tasks.all {
                if (this is Reporting<*> && state.failure != null) {
                    prepareReportForCIPublishing(project.name, this.reports["html"].destination)
                }
            }
        }
    }
}

fun Project.prepareReportForCIPublishing(projectName: String, report: File) {
    if (report.isDirectory) {
        val destFile = File("${rootProject.buildDir}/report-$projectName-${report.name}.zip")
        ant.withGroovyBuilder {
            "zip"("destFile" to destFile) {
                "fileset"("dir" to report)
            }
        }
    } else {
        copy {
            from(report)
            into(rootProject.buildDir)
            rename { "report-$projectName-${report.parentFile.name}-${report.name}" }
        }
    }
}

fun readProperties(propertiesFile: File) = Properties().apply {
    propertiesFile.inputStream().use { fis ->
        load(fis)
    }
}

val checkSameDaemonArgs by tasks.registering {
    doLast {
        val buildSrcProperties = readProperties(File(project.rootDir, "gradle.properties"))
        val rootProperties = readProperties(File(project.rootDir, "../gradle.properties"))
        val jvmArgs = listOf(buildSrcProperties, rootProperties).map { it.getProperty("org.gradle.jvmargs") }.toSet()
        if (jvmArgs.size > 1) {
            throw GradleException("gradle.properties and buildSrc/gradle.properties have different org.gradle.jvmargs " +
                "which may cause two daemons to be spawned on CI and in IDEA. " +
                "Use the same org.gradle.jvmargs for both builds.")
        }
    }
}

tasks.build { dependsOn(checkSameDaemonArgs) }

fun Project.applyGroovyProjectConventions() {
    apply(plugin = "java-gradle-plugin")
    apply(plugin = "groovy")

    dependencies {
        implementation(localGroovy())
        testImplementation("org.spockframework:spock-core:1.3-groovy-2.5") {
            exclude(group = "org.codehaus.groovy")
        }
        testImplementation("net.bytebuddy:byte-buddy:1.8.21")
        testImplementation("org.objenesis:objenesis:2.6")
    }

    tasks.withType<GroovyCompile>().configureEach {
        groovyOptions.apply {
            encoding = "utf-8"
        }
        options.apply {
            isFork = true
            encoding = "utf-8"
            compilerArgs = mutableListOf("-Xlint:-options", "-Xlint:-path")
        }
        val vendor = System.getProperty("java.vendor")
        inputs.property("javaInstallation", "$vendor ${JavaVersion.current()}")
    }

    tasks.withType<Test>().configureEach {
        if (JavaVersion.current().isJava9Compatible) {
            //allow ProjectBuilder to inject legacy types into the system classloader
            jvmArgs("--add-opens", "java.base/java.lang=ALL-UNNAMED")
            jvmArgs("--illegal-access=deny")
        }
    }

    val compileGroovy by tasks.existing(GroovyCompile::class)

    configurations {
        apiElements {
            outgoing.variants["classes"].artifact(
                mapOf(
                    "file" to compileGroovy.get().destinationDir,
                    "type" to ArtifactTypeDefinition.JVM_CLASS_DIRECTORY,
                    "builtBy" to compileGroovy
                ))
        }
    }
}

fun Project.applyKotlinProjectConventions() {
    apply(plugin = "org.gradle.kotlin.kotlin-dsl")
    apply(plugin = "org.gradle.kotlin-dsl.ktlint-convention")

    plugins.withType<KotlinDslPlugin> {
        configure<KotlinDslPluginOptions> {
            experimentalWarning.set(false)
        }
    }

    configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        // TODO:kotlin-dsl remove precompiled script plugins accessors exclusion from ktlint checks
        filter {
            exclude("gradle/kotlin/dsl/accessors/_*/**")
        }
    }
}

