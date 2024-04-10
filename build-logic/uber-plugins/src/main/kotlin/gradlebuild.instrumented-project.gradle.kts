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

import gradlebuild.modules.extension.ExternalModulesExtension

plugins {
    id("java-library")
    id("gradlebuild.strict-compile")
}

val libs = project.the<ExternalModulesExtension>()

dependencies {
    api(project(":internal-instrumentation-api"))
    implementation(project(":base-asm"))
    compileOnly(libs.asm)
    compileOnly(libs.asmUtil)
    compileOnly(libs.asmTree)
    annotationProcessor(project(":internal-instrumentation-processor"))
    annotationProcessor(platform(project(":distributions-dependencies")))
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-Aorg.gradle.annotation.processing.instrumented.project=${project.name}")
}

strictCompile {
    ignoreAnnotationProcessing() // Without this, javac will complain about unclaimed annotations
}
