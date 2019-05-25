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
import org.gradle.build.BuildReceipt
import org.gradle.gradlebuild.test.integrationtests.IntegrationTest
import org.gradle.gradlebuild.unittestandcompile.ModuleType
import org.gradle.plugins.ide.eclipse.model.Classpath
import org.gradle.plugins.ide.eclipse.model.SourceFolder

plugins {
    `java-library`
    gradlebuild.`shaded-jar`
}

val testPublishRuntime by configurations.creating

val buildReceipt: Provider<RegularFile> = rootProject.tasks.withType<BuildReceipt>().named("createBuildReceipt").map { layout.file(provider { it.receiptFile }).get() }

shadedJar {
    shadedConfiguration.exclude(mapOf("group" to "org.slf4j", "module" to "slf4j-api"))
    keepPackages.set(listOf("org.gradle.tooling"))
    unshadedPackages.set(listOf("org.gradle", "org.slf4j", "sun.misc"))
    ignoredPackages.set(setOf("org.gradle.tooling.provider.model"))
    buildReceiptFile.set(buildReceipt)
}

dependencies {
    implementation(project(":baseServices"))
    implementation(project(":messaging"))
    implementation(project(":logging"))
    implementation(project(":coreApi"))
    implementation(project(":core"))
    implementation(project(":wrapper"))

    implementation(library("guava"))

    publishImplementation(library("slf4j_api")) { version { prefer(libraryVersion("slf4j_api")) } }

    testFixturesImplementation(project(":modelCore"))
    testFixturesImplementation(project(":baseServicesGroovy"))
    testFixturesImplementation(project(":internalTesting"))
    testFixturesImplementation(project(":internalIntegTesting"))
    testFixturesImplementation(library("commons_io"))

    integTestImplementation(project(":jvmServices"))
    integTestImplementation(project(":persistentCache"))
    integTestRuntimeOnly(project(":toolingApiBuilders"))
    integTestRuntimeOnly(project(":ivy"))

    crossVersionTestImplementation(project(":jvmServices"))
    crossVersionTestImplementation(testLibrary("jetty"))

    crossVersionTestRuntimeOnly(project(":kotlinDsl"))
    crossVersionTestRuntimeOnly(project(":buildComparison"))
    crossVersionTestRuntimeOnly(project(":ivy"))
    crossVersionTestRuntimeOnly(project(":maven"))
    crossVersionTestRuntimeOnly(project(":apiMetadata"))
}

gradlebuildJava {
    moduleType = ModuleType.CORE
}

testFixtures {
    from(":core")
    from(":logging")
    from(":dependencyManagement")
    from(":ide")
    from(":workers")
}

apply(from = "buildship.gradle")

tasks.sourceJar {
    configurations.compile.allDependencies.withType<ProjectDependency>().forEach {
        val sourceSet = it.dependencyProject.java.sourceSets[SourceSet.MAIN_SOURCE_SET_NAME]
        from(sourceSet.groovy.srcDirs)
        from(sourceSet.java.srcDirs)
    }
}

eclipse {
    classpath {
        file.whenMerged(Action<Classpath> {
            //**TODO
            entries.removeAll { it is SourceFolder && it.path.contains("src/test/groovy") }
            entries.removeAll { it is SourceFolder && it.path.contains("src/integTest/groovy") }
        })
    }
}

tasks.register<Upload>("publishLocalArchives") {
    val repoBaseDir = rootProject.file("build/repo")
    configuration = configurations.publishRuntime.get()
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

integTestTasks.configureEach {
    binaryDistributions.binZipRequired = true
    libsRepository.required = true
}

testFilesCleanup {
    policy.set(WhenNotEmpty.REPORT)
}
