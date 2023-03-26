/*
 * Copyright 2013 the original author or authors.
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

import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.buildinit.plugins.internal.model.Description;
import org.gradle.buildinit.plugins.internal.modifiers.ComponentType;
import org.gradle.buildinit.plugins.internal.modifiers.ModularizationOption;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static com.google.common.collect.Lists.newArrayList;

public class JvmLibraryProjectInitDescriptor extends JvmProjectInitDescriptor {

    private final TemplateLibraryVersionProvider libraryVersionProvider;

    public JvmLibraryProjectInitDescriptor(Description description, TemplateLibraryVersionProvider libraryVersionProvider, DocumentationRegistry documentationRegistry) {
        super(description, libraryVersionProvider, documentationRegistry);
        this.libraryVersionProvider = libraryVersionProvider;
    }

    @Override
    public ComponentType getComponentType() {
        return ComponentType.LIBRARY;
    }

    @Override
    public Set<ModularizationOption> getModularizationOptions() {
        return new TreeSet<>(Arrays.asList(ModularizationOption.SINGLE_PROJECT, ModularizationOption.WITH_LIBRARY_PROJECTS));
    }

    @Override
    public void generateProjectBuildScript(String projectName, InitSettings settings, BuildScriptBuilder buildScriptBuilder) {
        super.generateProjectBuildScript(projectName, settings, buildScriptBuilder);

        if (isSingleProject(settings)) {
            applyLibraryPlugin(buildScriptBuilder);
            buildScriptBuilder.dependency(
                "api",
                "This dependency is exported to consumers, that is to say found on their compile classpath.",
                "org.apache.commons:commons-math3:" + libraryVersionProvider.getVersion("commons-math"));
            buildScriptBuilder.implementationDependency("This dependency is used internally, and not exposed to consumers on their own compile classpath.",
                "com.google.guava:guava:" + libraryVersionProvider.getVersion("guava"));
        } else {
            buildScriptBuilder.plugin(null, libraryConventionPlugin(settings));
            if (projectName.equals("lib")) {
                buildScriptBuilder.dependency(
                    "api",
                    "This dependency is exported to consumers, that is to say found on their compile classpath.",
                    "org.apache.commons:commons-math3:" + libraryVersionProvider.getVersion("commons-math"));
                buildScriptBuilder.implementationDependency("This dependency is used internally, and not exposed to consumers on their own compile classpath.",
                    "com.google.guava:guava:" + libraryVersionProvider.getVersion("guava"));
            }
        }
    }

    @Override
    protected List<String> getSourceTemplates(String subproject, InitSettings settings, TemplateFactory templateFactory) {
        return newArrayList("Library");
    }

    @Override
    protected List<String> getTestSourceTemplates(String subproject, InitSettings settings, TemplateFactory templateFactory) {
        return newArrayList(getUnitTestSourceTemplateName(settings));
    }

    private static String getUnitTestSourceTemplateName(InitSettings settings) {
        switch (settings.getTestFramework()) {
            case SPOCK:
                return "groovy/LibraryTest";
            case TESTNG:
                return "testng/LibraryTest";
            case JUNIT:
            case KOTLINTEST:
                return "LibraryTest";
            case JUNIT_JUPITER:
                return "junitjupiter/LibraryTest";
            case SCALATEST:
                return "LibrarySuite";
            default:
                throw new IllegalArgumentException();
        }
    }
}
