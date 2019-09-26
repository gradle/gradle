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

import org.gradle.buildinit.plugins.internal.modifiers.BuildInitTestFramework;
import org.gradle.buildinit.plugins.internal.modifiers.ComponentType;
import org.gradle.buildinit.plugins.internal.modifiers.Language;

import java.util.Collections;
import java.util.Set;

public class KotlinApplicationProjectInitDescriptor extends JvmProjectInitDescriptor {
    private final TemplateLibraryVersionProvider libraryVersionProvider;

    public KotlinApplicationProjectInitDescriptor(TemplateLibraryVersionProvider libraryVersionProvider) {
        this.libraryVersionProvider = libraryVersionProvider;
    }

    @Override
    public String getId() {
        return "kotlin-application";
    }

    @Override
    public ComponentType getComponentType() {
        return ComponentType.APPLICATION;
    }

    @Override
    public Language getLanguage() {
        return Language.KOTLIN;
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
    public void generate(InitSettings settings, BuildScriptBuilder buildScriptBuilder, TemplateFactory templateFactory) {
        super.generate(settings, buildScriptBuilder, templateFactory);

        new KotlinProjectInitDescriptor(libraryVersionProvider).generate(buildScriptBuilder);

        buildScriptBuilder
            .fileComment("This generated file contains a sample Kotlin application project to get you started.")
            .plugin("Apply the application plugin to add support for building a CLI application.", "application")
            .block(null, "application", b -> {
                b.propertyAssignment("Define the main class for the application.", "mainClassName", withPackage(settings, "AppKt"));
            });

        TemplateOperation kotlinSourceTemplate = templateFactory.fromSourceTemplate("kotlinapp/App.kt.template", "main");
        TemplateOperation kotlinTestTemplate = templateFactory.fromSourceTemplate("kotlinapp/AppTest.kt.template", "test");
        templateFactory.whenNoSourcesAvailable(kotlinSourceTemplate, kotlinTestTemplate).generate();
    }
}
