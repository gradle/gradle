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

dependencies {
    api(project(":internal-instrumentation-api"))

    api(libs.asm)
    api(libs.javaPoet)
    api(libs.jsr305)

    implementation(libs.asmTree)
    implementation(libs.jacksonAnnotations)
    implementation(libs.jacksonDatabind)

    implementation(projects.javaLanguageExtensions)
    implementation(project(":base-services"))
    implementation(project(":base-asm"))

    testCompileOnly(libs.jetbrainsAnnotations)

    testImplementation(libs.compileTesting)
    testImplementation(project(":core"))
    // TODO remove this
    testImplementation(libs.jetbrainsAnnotations)
}

tasks.named<Test>("test").configure {
    if (!javaVersion.isJava9Compatible) {
        // For Java8 tools.jar is needed for com.google.testing.compile:compile-testing
        classpath += javaLauncher.get().metadata.installationPath.files("lib/tools.jar")
    } else {
        // Needed for Java19 for com.google.testing.compile:compile-testing
        jvmArgs(
            "--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED",
            "--add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
            "--add-exports=jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED"
        )
    }
}
