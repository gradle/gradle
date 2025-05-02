/*
 * Copyright 2016 the original author or authors.
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

import com.google.common.collect.ImmutableList;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.buildinit.plugins.internal.model.Description;
import org.gradle.buildinit.plugins.internal.modifiers.ComponentType;
import org.gradle.buildinit.plugins.internal.modifiers.Language;
import org.gradle.buildinit.plugins.internal.modifiers.ModularizationOption;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class JvmApplicationProjectInitDescriptor extends JvmProjectInitDescriptor {

    public JvmApplicationProjectInitDescriptor(Description description, TemplateLibraryVersionProvider libraryVersionProvider, DocumentationRegistry documentationRegistry) {
        super(description, libraryVersionProvider, documentationRegistry);
    }

    @Override
    public ComponentType getComponentType() {
        return ComponentType.APPLICATION;
    }

    @Override
    public Set<ModularizationOption> getModularizationOptions() {
        return new TreeSet<>(Arrays.asList(ModularizationOption.SINGLE_PROJECT, ModularizationOption.WITH_LIBRARY_PROJECTS));
    }

    @Override
    public void generateProjectBuildScript(String projectName, InitSettings settings, BuildScriptBuilder buildScriptBuilder) {
        super.generateProjectBuildScript(projectName, settings, buildScriptBuilder);

        if ("app".equals(projectName)) {
            buildScriptBuilder.block(null, "application", b -> {
                String mainClass = getLanguage() == Language.KOTLIN ? "AppKt" : "App";
                if (!isSingleProject(settings)) {
                    mainClass = "app." + mainClass;
                }
                b.propertyAssignment("Define the main class for the application.", "mainClass", withPackage(settings, mainClass), true);
            });
        }

        if (isSingleProject(settings)) {
            applyApplicationPlugin(buildScriptBuilder);
            buildScriptBuilder.implementationDependency("This dependency is used by the application.",
                BuildInitDependency.of("com.google.guava:guava", libraryVersionProvider.getVersion("guava")));
        } else {
            if ("app".equals(projectName)) {
                buildScriptBuilder.plugin(null, applicationConventionPlugin());
                buildScriptBuilder.dependencies().dependency("implementation", null, BuildInitDependency.of("org.apache.commons:commons-text"));
                buildScriptBuilder.dependencies().projectDependency("implementation", null, ":utilities");
            } else {
                buildScriptBuilder.plugin(null, libraryConventionPlugin());
                if ("utilities".equals(projectName)) {
                    buildScriptBuilder.dependencies().projectDependency("api", null, ":list");
                }
            }
        }
    }

    @Override
    protected List<String> getSourceTemplates(String subproject, InitSettings settings, TemplateFactory templateFactory) {
        if (isSingleProject(settings)) {
            return Collections.singletonList("App");
        }
        switch (subproject) {
            case "app":
                return ImmutableList.of("multi/app/App", "multi/app/MessageUtils");
            case "list":
                return Collections.singletonList("multi/list/LinkedList");
            case "utilities":
                return ImmutableList.of("multi/utilities/JoinUtils", "multi/utilities/SplitUtils", "multi/utilities/StringUtils");
            default:
                return Collections.emptyList();
        }
    }

    @Override
    protected List<String> getTestSourceTemplates(String subproject, InitSettings settings, TemplateFactory templateFactory) {
        if (isSingleProject(settings)) {
            return Collections.singletonList(getTestFrameWorkName(settings));
        }

        switch (subproject) {
            case "app":
                return Collections.singletonList("multi/app/junit5/MessageUtilsTest");
            case "list":
                return Collections.singletonList("multi/list/junit5/LinkedListTest");
            default:
                return Collections.emptyList();
        }
    }

    private static String getTestFrameWorkName(InitSettings settings) {
        switch (settings.getTestFramework()) {
            case SPOCK:
                return "groovy/AppTest";
            case TESTNG:
                return "testng/AppTest";
            case JUNIT:
            case KOTLINTEST:
                return "AppTest";
            case JUNIT_JUPITER:
                return "junitjupiter/AppTest";
            case SCALATEST:
                return "AppSuite";
            default:
                throw new IllegalArgumentException();
        }
    }
}
