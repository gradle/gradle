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

import org.gradle.plugins.ide.idea.model.IdeaModel

import org.gradle.kotlin.dsl.plugins.dsl.KotlinDslPlugin

import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

import java.io.File
import java.util.Properties

plugins {
    `kotlin-dsl`
    id("org.gradle.kotlin.ktlint-convention") version "0.1.15" apply false
}

subprojects {

    apply(plugin = "java-library")

    if (file("src/main/groovy").isDirectory || file("src/test/groovy").isDirectory) {

        applyGroovyProjectConventions()
    }

    if (file("src/main/kotlin").isDirectory || file("src/test/kotlin").isDirectory) {

        applyKotlinProjectConventions()
    }

    configure<JavaPluginExtension> {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    
    apply(plugin = "idea")
    apply(plugin = "eclipse")

    configure<IdeaModel> {
        module.name = "buildSrc-${this@subprojects.name}"
    }

    dependencies {
        compile(gradleApi())
    }

    afterEvaluate {
        if (tasks.withType<ValidateTaskProperties>().isEmpty()) {
            val validateTaskProperties = tasks.register("validateTaskProperties", ValidateTaskProperties::class.java) {
                outputFile.set(project.the<ReportingExtension>().baseDirectory.file("task-properties/report.txt"))

                val mainSourceSet = project.sourceSets[SourceSet.MAIN_SOURCE_SET_NAME]
                classes = mainSourceSet.output.classesDirs
                classpath = mainSourceSet.compileClasspath
                dependsOn(mainSourceSet.output)
            }
            tasks.named("check").configure { dependsOn(validateTaskProperties) }
        }
    }

    tasks.withType<ValidateTaskProperties> {
        failOnWarning = true
    }
}

allprojects {
    repositories {
        gradlePluginPortal()
        maven {
            name = "Gradle libs"
            url = uri("https://repo.gradle.org/gradle/libs")
        }
        maven {
            name = "Gradle snapshot libs"
            url = uri("https://repo.gradle.org/gradle/libs-snapshots")
        }
        maven {
            name = "kotlin-eap"
            url = uri("https://dl.bintray.com/kotlin/kotlin-eap")
        }
        maven {
            name = "kotlin-dev"
            url = uri("https://dl.bintray.com/kotlin/kotlin-dev")
        }
    }
}

dependencies {
    subprojects.forEach {
        "runtime"(project(it.path))
    }
}

// TODO Avoid duplication of what defines a CI Server with BuildEnvironment
val isCiServer: Boolean by extra { "CI" in System.getenv() }
if (!isCiServer || System.getProperty("enableCodeQuality")?.toLowerCase() == "true") {
    apply(from = "../gradle/shared-with-buildSrc/code-quality-configuration.gradle.kts")
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

val checkSameDaemonArgs = tasks.register("checkSameDaemonArgs") {
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

tasks.named("build").configure { dependsOn(checkSameDaemonArgs) }

fun Project.applyGroovyProjectConventions() {
    apply(plugin = "groovy")

    dependencies {
        compile(localGroovy())
        testCompile("org.spockframework:spock-core:1.2-groovy-2.5") {
            exclude(group = "org.codehaus.groovy")
        }
        testCompile("net.bytebuddy:byte-buddy:1.8.21")
        testCompile("org.objenesis:objenesis:2.6")
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
        if (JavaVersion.current().isJava9Compatible()) {
            //allow ProjectBuilder to inject legacy types into the system classloader
            jvmArgs("--add-opens", "java.base/java.lang=ALL-UNNAMED")
            jvmArgs("--illegal-access=deny")
        }
        
    }

    val compileGroovy: TaskProvider<GroovyCompile> = tasks.withType(GroovyCompile::class.java).named("compileGroovy")

    configurations {
        "apiElements" {
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
    apply(plugin = "kotlin")

    apply(plugin = "org.gradle.kotlin.ktlint-convention")

    tasks.withType<KotlinCompile>().configureEach {
        kotlinOptions {
            freeCompilerArgs += listOf("-Xjsr305=strict")
        }
    }

    plugins.withType<KotlinDslPlugin> {
        kotlinDslPluginOptions {
            experimentalWarning.set(false)
        }
    }
}
