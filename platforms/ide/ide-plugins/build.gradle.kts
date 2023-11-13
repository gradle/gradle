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

dependencies {
    implementation(project(":base-services"))
    implementation(project(":base-ide-plugins"))
    implementation(project(":core"))
    implementation(project(":core-api"))
    implementation(project(":dependency-management"))
    implementation(project(":ear"))
    implementation(project(":file-collections"))
    implementation(project(":functional"))
    implementation(project(":ide"))
    implementation(project(":language-java"))
    implementation(project(":language-jvm"))
    implementation(project(":logging"))
    implementation(project(":model-core"))
    implementation(project(":platform-jvm"))
    implementation(project(":plugins"))
    implementation(project(":plugins-groovy"))
    implementation(project(":plugins-java"))
    implementation(project(":plugins-java-base"))
    implementation(project(":plugins-jvm-test-fixtures"))
    implementation(project(":plugins-jvm-test-suite"))
    implementation(project(":scala"))
    implementation(project(":testing-base"))
    implementation(project(":test-suites-base"))
    implementation(project(":tooling-api"))
    implementation(project(":war"))

    implementation(libs.commonsLang)
    implementation(libs.groovy)
    implementation(libs.guava)
    implementation(libs.inject)

    testImplementation(testFixtures(project(":core")))
    testImplementation(testFixtures(project(":dependency-management")))
    testImplementation(testFixtures(project(":ide")))
    testImplementation(testFixtures(project(":tooling-api")))

    testRuntimeOnly(project(":distributions-jvm")) {
        because("ProjectBuilder tests load services from a Gradle distribution.")
    }

    testImplementation(libs.xmlunit)


    integTestImplementation(project(":internal-integ-testing"))

    integTestDistributionRuntimeOnly(project(":distributions-jvm")) {
        because("ProjectBuilder tests load services from a Gradle distribution.")
    }

    crossVersionTestDistributionRuntimeOnly(project(":distributions-jvm"))
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
