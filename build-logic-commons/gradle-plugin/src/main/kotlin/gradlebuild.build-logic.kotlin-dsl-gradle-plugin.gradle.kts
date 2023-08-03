/*
 * Copyright 2020 the original author or authors.
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
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("java-library")
    id("org.gradle.kotlin.kotlin-dsl") // this is 'kotlin-dsl' without version
    id("gradlebuild.code-quality")
    id("gradlebuild.ktlint")
    id("gradlebuild.ci-reporting")
    id("gradlebuild.test-retry")
}

java.configureJavaToolChain()

dependencies {
    api(platform(project(":build-platform")))
    implementation("gradlebuild:gradle-plugin")

    testImplementation("org.junit.vintage:junit-vintage-engine")
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        allWarningsAsErrors = true
    }
}

ktlint {
    filter {
        exclude("gradle/kotlin/dsl/accessors/_*/**")
    }
}

tasks.runKtlintCheckOverKotlinScripts {
    // Only check the build files, not all *.kts files in the project
    includes += listOf("*.gradle.kts")
}

tasks.named("codeQuality") {
    dependsOn("ktlintCheck")
}

tasks.validatePlugins {
    failOnWarning = true
    enableStricterValidation = true
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
