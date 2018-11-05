/*
 * Copyright 2018 the original author or authors.
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

import org.gradle.api.internal.file.FileResolver;
import org.gradle.buildinit.plugins.internal.modifiers.BuildInitDsl;
import org.gradle.buildinit.plugins.internal.modifiers.BuildInitTestFramework;

import java.util.Collections;
import java.util.Set;

public class KotlinApplicationProjectInitDescriptor extends LanguageLibraryProjectInitDescriptor {
    public KotlinApplicationProjectInitDescriptor(BuildScriptBuilderFactory scriptBuilderFactory, TemplateOperationFactory templateOperationFactory, FileResolver fileResolver, DefaultTemplateLibraryVersionProvider versionProvider) {
        super("kotlin", scriptBuilderFactory, templateOperationFactory, fileResolver, versionProvider);
    }

    @Override
    public String getId() {
        return "kotlin-application";
    }

    @Override
    public BuildInitDsl getDefaultDsl() {
        return BuildInitDsl.KOTLIN;
    }

    @Override
    public BuildInitTestFramework getDefaultTestFramework() {
        return BuildInitTestFramework.KOTLINTEST;
    }

    @Override
    public Set<BuildInitTestFramework> getTestFrameworks() {
        return Collections.singleton(BuildInitTestFramework.KOTLINTEST);
    }

    @Override
    protected void generate(InitSettings settings, BuildScriptBuilder buildScriptBuilder) {
        String kotlinVersion = libraryVersionProvider.getVersion("kotlin");
        buildScriptBuilder
            .fileComment("This generated file contains a sample Kotlin application project to get you started.")
            .plugin("Apply the Kotlin JVM plugin to add support for Kotlin on the JVM.", "org.jetbrains.kotlin.jvm", kotlinVersion)
            .plugin("Apply the application plugin to add support for building a CLI application.", "application")
            .implementationDependency("Use the Kotlin JDK 8 standard library.", "org.jetbrains.kotlin:kotlin-stdlib-jdk8")
            .testImplementationDependency("Use the Kotlin test library.", "org.jetbrains.kotlin:kotlin-test")
            .testImplementationDependency("Use the Kotlin JUnit integration.", "org.jetbrains.kotlin:kotlin-test-junit")
            .conventionPropertyAssignment(
                "Define the main class for the application.",
                "application", "mainClassName", withPackage(settings, "AppKt"));

        TemplateOperation kotlinSourceTemplate = fromClazzTemplate("kotlinapp/App.kt.template", settings, "main");
        TemplateOperation kotlinTestTemplate = fromClazzTemplate("kotlinapp/AppTest.kt.template", settings, "test");
        whenNoSourcesAvailable(kotlinSourceTemplate, kotlinTestTemplate).generate();
    }
}
