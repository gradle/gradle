import gradlebuild.packaging.tasks.ExtractJavaAbi

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
    id("gradlebuild.java-library")
    id("gradlebuild.distribution-module")
    id("gradlebuild.distribution.api")
}

description = "Used for Gradle API projects that don't apply instrumentation"

val extractorClasspathConfig by configurations.creating

dependencies {
    extractorClasspathConfig("org.gradle:java-api-extractor")
}

val extractJavaAbi by tasks.registering(ExtractJavaAbi::class) {
    classesDirectories = sourceSets.main.get().output.classesDirs
    outputDirectory = layout.buildDirectory.dir("generated/java-abi")
    extractorClasspath = extractorClasspathConfig
}

configurations {
    named("apiStubElements") {
        extendsFrom(configurations.compileOnlyApi.get())
        outgoing.artifact(extractJavaAbi)
    }
}
