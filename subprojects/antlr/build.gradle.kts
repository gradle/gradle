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

plugins {
    id("gradlebuild.portalplugin.java")
}

pluginBundle {
    tags = listOf("Gradle", "Antlr")
    website = "https://docs.gradle.org/current/userguide/antlr_plugin.html"
    vcsUrl = "https://github.com/gradle/gradle"
}

pluginPublish {
    bundledGradlePlugin(
        name = "antlr",
        shortDescription = "Antlr Gradle Plugin",
        pluginId = "org.gradle.antlr",
        pluginClass = "org.gradle.api.plugins.antlr.AntlrPlugin"
    )
}

dependencies {
    compileOnly(project(":base-services"))
    compileOnly(project(":logging"))
    compileOnly(project(":process-services"))
    compileOnly(project(":core-api"))
    compileOnly(project(":model-core"))
    compileOnly(project(":core"))
    compileOnly(project(":plugins"))
    compileOnly(project(":workers"))
    compileOnly(project(":files"))

    implementation(libs.guava)

    compileOnly(libs.slf4jApi)
    compileOnly(libs.groovy)
    compileOnly(libs.inject)

    compileOnly("antlr:antlr:2.7.7") {
        because("this dependency is downloaded by the antlr plugin")
    }

    testImplementation(project(":base-services"))
    testImplementation(project(":logging"))
    testImplementation(project(":process-services"))
    testImplementation(project(":core-api"))
    testImplementation(project(":model-core"))
    testImplementation(project(":core"))
    testImplementation(project(":plugins"))
    testImplementation(project(":workers"))
    testImplementation(project(":files"))

    testImplementation(libs.slf4jApi)
    testImplementation(libs.groovy)
    testImplementation(libs.inject)

    testImplementation(project(":base-services-groovy"))
    testImplementation(project(":file-collections"))
    testImplementation(testFixtures(project(":core")))

    testRuntimeOnly(project(":distributions-core")) {
        because("ProjectBuilder tests load services from a Gradle distribution.")
    }
    integTestDistributionRuntimeOnly(project(":distributions-full"))
    integTestLocalRepository(project)
}

classycle {
    excludePatterns.set(listOf("org/gradle/api/plugins/antlr/internal/*"))
}
