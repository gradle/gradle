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

description = "Contains the Application plugin, and its supporting classes.  This plugin is used for creating runnable Java application projects."

dependencies {
    api(project(":core"))
    api(project(":core-api"))
    api(project(":language-java"))
    api(project(":logging"))
    api(project(":plugins-distribution"))
    api(project(":plugins-java"))
    api(project(":plugins-jvm-test-suite"))
    api(project(":publish"))

    api(libs.inject)

    implementation(project(":base-services"))
    implementation(project(":plugins-java-base"))
    implementation(project(":tooling-api"))

    implementation(libs.commonsLang)
    implementation(libs.groovy)
    implementation(libs.groovyTemplates)
}

strictCompile {
    //ignoreRawTypes() // raw types used in public API
    ignoreDeprecations() // TODO: uses deprecated software model types, suppress these warnings and remove ignore
}

packageCycles {
    excludePatterns.add("org/gradle/api/plugins/**")
}
