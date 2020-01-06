/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.buildinit.plugins.internal;

public class KotlinProjectInitDescriptor {
    private final TemplateLibraryVersionProvider libraryVersionProvider;

    public KotlinProjectInitDescriptor(TemplateLibraryVersionProvider libraryVersionProvider) {
        this.libraryVersionProvider = libraryVersionProvider;
    }

    public void generate(BuildScriptBuilder buildScriptBuilder) {
        String kotlinVersion = libraryVersionProvider.getVersion("kotlin");
        buildScriptBuilder.plugin("Apply the Kotlin JVM plugin to add support for Kotlin.", "org.jetbrains.kotlin.jvm", kotlinVersion);
        buildScriptBuilder.dependencies().platformDependency(
            "implementation", "Align versions of all Kotlin components", "org.jetbrains.kotlin:kotlin-bom"
        );
        buildScriptBuilder.
            implementationDependency("Use the Kotlin JDK 8 standard library.", "org.jetbrains.kotlin:kotlin-stdlib-jdk8");

        buildScriptBuilder
            .testImplementationDependency("Use the Kotlin test library.", "org.jetbrains.kotlin:kotlin-test")
            .testImplementationDependency("Use the Kotlin JUnit integration.", "org.jetbrains.kotlin:kotlin-test-junit");
    }
}
