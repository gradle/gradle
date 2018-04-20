/*
 * Copyright 2018 the original author or authors.
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

import org.gradle.gradlebuild.testing.integrationtests.cleanup.WhenNotEmpty
import accessors.*
import org.gradle.gradlebuild.BuildEnvironment
import org.gradle.gradlebuild.packaging.ShadedJar
import org.gradle.gradlebuild.test.integrationtests.IntegrationTest
import org.gradle.gradlebuild.unittestandcompile.ModuleType
import org.gradle.plugins.ide.eclipse.model.Classpath

val testPublishRuntime by configurations.creating

dependencies {
    compile(project(":core"))
    compile(project(":messaging"))
    compile(project(":wrapper"))
    compile(project(":baseServices"))
    publishCompile(library("slf4j_api")) { version { prefer(libraryVersion("slf4j_api")) } }
    compile(library("jcip"))

    testFixturesCompile(project(":baseServicesGroovy"))
    testFixturesCompile(project(":internalIntegTesting"))

    integTestRuntime(project(":toolingApiBuilders"))
    integTestRuntime(project(":ivy"))

    crossVersionTestRuntime("org.gradle:gradle-kotlin-dsl:${BuildEnvironment.gradleKotlinDslVersion}")
    crossVersionTestRuntime(project(":buildComparison"))
    crossVersionTestRuntime(project(":ivy"))
    crossVersionTestRuntime(project(":maven"))
}

gradlebuildJava {
    moduleType = ModuleType.ENTRY_POINT
}

testFixtures {
    from(":core")
    from(":logging")
    from(":dependencyManagement")
    from(":ide")
}

val jar: Jar by tasks

val baseVersion: String by rootProject.extra

val shadedJarWithoutVersion by tasks.creating(ShadedJar::class) {
    val outputDir = file("$buildDir/shaded-jar-without-version")
    sourceFiles = jar.outputs.files +
        files(deferred { configurations.runtimeClasspath - configurations.publishCompile })
    analysisFile = file("$outputDir/analysis.txt")
    classesDir = file("$outputDir/classes")
    jarFile = file("$outputDir/gradle-tooling-api-shaded-$baseVersion.jar")
    keepPackages = setOf("org.gradle.tooling")
    unshadedPackages = setOf("org.gradle", "org.slf4j", "sun.misc")
    ignorePackages = setOf("org.gradle.tooling.provider.model")
    shadowPackage = "org.gradle.internal.impldep"
}

val buildReceipt = tasks.getByPath(":createBuildReceipt")

val toolingApiShadedJar by tasks.creating(Zip::class) {
    destinationDir = file("$buildDir/shaded-jar")
    dependsOn(shadedJarWithoutVersion, buildReceipt)
    from(zipTree(shadedJarWithoutVersion.jarFile))
    baseName = "gradle-tooling-api-shaded"
    from(buildReceipt) {
        into("/org/gradle")
    }
    extension = "jar"
    version = baseVersion
}

apply { from("buildship.gradle") }

val sourceJar: Jar by tasks

sourceJar.run {
    configurations.compile.allDependencies.withType<ProjectDependency>().forEach {
        from(it.dependencyProject.java.sourceSets[SourceSet.MAIN_SOURCE_SET_NAME].groovy.srcDirs)
        from(it.dependencyProject.java.sourceSets[SourceSet.MAIN_SOURCE_SET_NAME].java.srcDirs)
    }
}

eclipse {
    classpath {
        file.whenMerged(Action<Classpath> {
            //**TODO
            entries.removeAll { path.contains("src/test/groovy") }
            entries.removeAll { path.contains("src/integTest/groovy") }
        })
    }
}

artifacts {
    add("publishRuntime", mapOf(
        "file" to toolingApiShadedJar.archivePath,
        "name" to base.archivesBaseName,
        "type" to "jar",
        "builtBy" to toolingApiShadedJar
    ))
}

tasks.create<Upload>("publishLocalArchives") {
    val repoBaseDir = rootProject.file("build/repo")
    configuration = configurations.publishRuntime
    isUploadDescriptor = false
    repositories {
        ivy {
            artifactPattern("$repoBaseDir/${project.group.toString().replace(".", "/")}/${base.archivesBaseName}/[revision]/[artifact]-[revision](-[classifier]).[ext]")
        }
    }

    doFirst {
        if (repoBaseDir.exists()) {
            // Make sure tooling API artifacts do not pile up
            repoBaseDir.deleteRecursively()
        }
    }
}

val integTestTasks: DomainObjectCollection<IntegrationTest> by extra

integTestTasks.configureEachLater { 
    binaryDistributions.binZipRequired = true
    libsRepository.required = true
}

testFilesCleanup {
    policy.set(WhenNotEmpty.REPORT)
}
