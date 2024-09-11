/*
 * Copyright 2023 the original author or authors.
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

plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Plugins that add support for generating IDE project files used for importing Gradle projects into IDEs"

errorprone {
    disabledChecks.addAll(
        "MixedMutabilityReturnType", // 2 occurrences
    )
}

dependencies {
    api(projects.stdlibJavaExtensions)
    api(projects.serviceProvider)
    api(projects.baseIdePlugins)
    api(projects.baseServices)
    api(projects.core)
    api(projects.coreApi)
    api(projects.ide)
    api(projects.platformJvm)
    api(projects.toolingApi)

    api(libs.groovy)
    api(libs.guava)
    api(libs.inject)
    api(libs.jsr305)

    implementation(projects.dependencyManagement)
    implementation(projects.ear)
    implementation(projects.fileCollections)
    implementation(projects.languageJava)
    implementation(projects.modelCore)
    implementation(projects.pluginsGroovy)
    implementation(projects.pluginsJava)
    implementation(projects.pluginsJavaBase)
    implementation(projects.pluginsJvmTestFixtures)
    implementation(projects.pluginsJvmTestSuite)
    implementation(projects.scala)
    implementation(projects.serviceLookup)
    implementation(projects.testSuitesBase)
    implementation(projects.war)

    implementation(libs.commonsLang)

    runtimeOnly(projects.languageJvm)
    runtimeOnly(projects.testingBase)

    testImplementation(testFixtures(projects.core))
    testImplementation(testFixtures(projects.dependencyManagement))
    testImplementation(testFixtures(projects.ide))
    testImplementation(testFixtures(projects.toolingApi))

    testRuntimeOnly(projects.distributionsJvm) {
        because("ProjectBuilder tests load services from a Gradle distribution.")
    }

    testImplementation(libs.xmlunit)


    integTestImplementation(projects.internalIntegTesting)

    integTestDistributionRuntimeOnly(projects.distributionsJvm) {
        because("ProjectBuilder tests load services from a Gradle distribution.")
    }

    crossVersionTestDistributionRuntimeOnly(projects.distributionsJvm)
}

packageCycles {
    excludePatterns.add("org/gradle/plugins/ide/idea/**")
}

/*
 * This is needed to avoid CI failures like this:
 *
 * Failed to stop service 'testFilesCleanupBuildService'.
   > Found non-empty test files dir:
    /home/tcagent1/agent/work/f63322e10dd6b396/platforms/ide/ide-plugins/build/tmp/teŝt files/EclipseInte.Test:
     canHandleCi.cies/xinjd/build/reports/configuration-cache/f5dmqyt5fw1qx1u5ylf7c1p2p/f1jute3awhw927kq95bbyi89k/configuration-cache-report.html
     canHandleCi.cies/xinjd/.project
     canHandleCi.cies/xinjd/settings.gradle
     canHandleCi.cies/xinjd/.classpath
 */
testFilesCleanup.reportOnly = true

integTest.usesJavadocCodeSnippets = true
tasks.isolatedProjectsIntegTest {
    enabled = false
}
