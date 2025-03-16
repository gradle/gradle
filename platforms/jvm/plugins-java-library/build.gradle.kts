/*
 * Copyright 2024 the original author or authors.
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

description = "Contains the java-library plugin, and its supporting classes.  This plugin is used to build java libraries."

dependencies {
    api(projects.coreApi)

    api(libs.inject)

    implementation(projects.baseServices)
    implementation(projects.languageJava)
    implementation(projects.languageJvm)
    implementation(projects.platformJvm)
    implementation(projects.pluginsDistribution)
    implementation(projects.pluginsJava)
    implementation(projects.pluginsJvmTestSuite)

    runtimeOnly(projects.core)
    runtimeOnly(projects.platformBase)

    testImplementation(projects.pluginsJavaBase)

    testImplementation(testFixtures(projects.core))

    testRuntimeOnly(projects.distributionsCore) {
        because("ProjectBuilder tests load services from a Gradle distribution.")
    }

    integTestImplementation(testFixtures(projects.resourcesHttp))

    integTestDistributionRuntimeOnly(projects.distributionsJvm)
}

dependencyAnalysis {
    issues {
        onRuntimeOnly {
            // The plugin will suggest moving this to runtimeOnly, but it is needed during :plugins-java-library:compileJava
            // to avoid "class file for org.gradle.api.tasks.bundling.Jar not found"
            exclude(":language-jvm")
        }
    }
}
