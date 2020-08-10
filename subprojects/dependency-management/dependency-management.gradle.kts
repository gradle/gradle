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

import gradlebuild.cleanup.WhenNotEmpty

plugins {
    id("gradlebuild.distribution.implementation-java")
}

dependencies {
    implementation(project(":base-services"))
    implementation(project(":messaging"))
    implementation(project(":native"))
    implementation(project(":logging"))
    implementation(project(":files"))
    implementation(project(":fileCollections"))
    implementation(project(":persistentCache"))
    implementation(project(":coreApi"))
    implementation(project(":modelCore"))
    implementation(project(":base-services-groovy"))
    implementation(project(":buildCache"))
    implementation(project(":core"))
    implementation(project(":resources"))
    implementation(project(":resources-http"))
    implementation(project(":snapshots"))
    implementation(project(":execution"))
    implementation(project(":security"))

    implementation(libs.slf4jApi)
    implementation(libs.groovy)
    implementation(libs.asm)
    implementation(libs.asmCommons)
    implementation(libs.asmUtil)
    implementation(libs.guava)
    implementation(libs.commonsLang)
    implementation(libs.commonsIo)
    implementation(libs.commonsHttpclient)
    implementation(libs.inject)
    implementation(libs.gson)
    implementation(libs.ant)
    implementation(libs.ivy)
    implementation(libs.maven3)

    testImplementation(project(":process-services"))
    testImplementation(project(":diagnostics"))
    testImplementation(project(":buildCachePackaging"))
    testImplementation(libs.nekohtml)
    testImplementation(testFixtures(project(":core")))
    testImplementation(testFixtures(project(":messaging")))
    testImplementation(testFixtures(project(":coreApi")))
    testImplementation(testFixtures(project(":versionControl")))
    testImplementation(testFixtures(project(":resources-http")))
    testImplementation(testFixtures(project(":base-services")))
    testImplementation(testFixtures(project(":snapshots")))
    testImplementation(testFixtures(project(":execution")))

    integTestImplementation(project(":buildOption"))
    integTestImplementation(libs.jansi)
    integTestImplementation(libs.ansiControlSequenceUtil)
    integTestImplementation(libs.jetty) {
        because("tests use HttpServlet directly")
    }
    integTestImplementation(testFixtures(project(":security")))

    testFixturesApi(project(":base-services")) {
        because("Test fixtures export the Action class")
    }
    testFixturesApi(project(":persistentCache")) {
        because("Test fixtures export the CacheAccess class")
    }

    testFixturesApi(libs.jetty)
    testFixturesImplementation(project(":core"))
    testFixturesImplementation(testFixtures(project(":core")))
    testFixturesImplementation(testFixtures(project(":resources-http")))
    testFixturesImplementation(project(":coreApi"))
    testFixturesImplementation(project(":messaging"))
    testFixturesImplementation(project(":internalIntegTesting"))
    testFixturesImplementation(libs.slf4jApi)
    testFixturesImplementation(libs.inject)
    testFixturesImplementation(libs.guava) {
        because("Groovy compiler reflects on private field on TextUtil")
    }
    testFixturesImplementation(libs.bouncycastlePgp)
    testFixturesApi(libs.testcontainersSpock) {
        because("API because of Groovy compiler bug leaking internals")
    }
    testFixturesImplementation(project(":jvm-services")) {
        because("Groovy compiler bug leaks internals")
    }

    testRuntimeOnly(project(":distributions-core")) {
        because("ProjectBuilder tests load services from a Gradle distribution.")
    }
    integTestDistributionRuntimeOnly(project(":distributions-basics"))
    crossVersionTestDistributionRuntimeOnly(project(":distributions-core"))
}

classycle {
    excludePatterns.set(listOf("org/gradle/**"))
}

testFilesCleanup {
    policy.set(WhenNotEmpty.REPORT)
}

tasks.clean {
    val testFiles = layout.buildDirectory.dir("tmp/test files")
    doFirst {
        // On daemon crash, read-only cache tests can leave read-only files around.
        // clean now takes care of those files as well
        testFiles.get().asFileTree.matching {
            include("**/read-only-cache/**")
        }.visit { this.file.setWritable(true) }
    }
}

afterEvaluate {
    // This is a workaround for the validate plugins task trying to inspect classes which
    // have changed but are NOT tasks
    tasks.withType<ValidatePlugins>().configureEach {
        val main = sourceSets.main.get()
        classes.setFrom(main.output.classesDirs.asFileTree.filter { !it.isInternal(main) })
    }
}

fun File.isInternal(sourceSet: SourceSet) = isInternal(sourceSet.output.classesDirs.files)

fun File.isInternal(roots: Set<File>): Boolean = name == "internal" ||
    !roots.contains(parentFile) && parentFile.isInternal(roots)
