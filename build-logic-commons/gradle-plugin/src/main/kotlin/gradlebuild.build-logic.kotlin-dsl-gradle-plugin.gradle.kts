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
import gradlebuild.commons.configureJavaToolChain
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("java-library")
    id("org.gradle.kotlin.kotlin-dsl") // this is 'kotlin-dsl' without version
    id("gradlebuild.code-quality")
    id("gradlebuild.detekt")
    id("gradlebuild.ci-reporting")
    id("gradlebuild.test-retry")
    id("gradlebuild.private-javadoc")
}

java.configureJavaToolChain()

dependencies {
    api(platform("gradlebuild:build-platform"))
    implementation("gradlebuild:gradle-plugin")

    testImplementation("org.junit.vintage:junit-vintage-engine")
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        allWarningsAsErrors = true
    }
}

detekt {
    // overwrite the config file's location
    config.convention(project.isolated.rootProject.projectDirectory.file("../gradle/detekt.yml"))
}

tasks.named("codeQuality") {
    dependsOn("detekt")
}

tasks.validatePlugins {
    failOnWarning = true
    enableStricterValidation = true
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
