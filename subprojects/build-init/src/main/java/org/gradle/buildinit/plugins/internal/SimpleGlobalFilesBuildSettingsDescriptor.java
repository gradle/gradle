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
import org.gradle.buildinit.plugins.internal.modifiers.ModularizationOption;

public class SimpleGlobalFilesBuildSettingsDescriptor implements BuildContentGenerator {
    final static String PLUGINS_BUILD_LOCATION = "build-logic";

    private final DocumentationRegistry documentationRegistry;
    private final BuildScriptBuilderFactory scriptBuilderFactory;

    public SimpleGlobalFilesBuildSettingsDescriptor(BuildScriptBuilderFactory scriptBuilderFactory, DocumentationRegistry documentationRegistry) {
        this.scriptBuilderFactory = scriptBuilderFactory;
        this.documentationRegistry = documentationRegistry;
    }

    public void generateWithoutComments(InitSettings settings) {
        builder(settings).withExternalComments().create(settings.getTarget()).generate();
    }

    @Override
    public void generate(InitSettings settings) {
        builder(settings).create(settings.getTarget()).generate();
    }

    private BuildScriptBuilder builder(InitSettings settings) {
        BuildScriptBuilder builder = scriptBuilderFactory.scriptForNewProjects(settings.getDsl(), "settings", settings.isUseIncubatingAPIs());
        builder.fileComment("The settings file is used to specify which projects to include in your build.\n\n")
            .fileComment(documentationRegistry.getDocumentationRecommendationFor("detailed information on multi-project builds", "building_swift_projects"));
        if (settings.getModularizationOption() == ModularizationOption.WITH_LIBRARY_PROJECTS && settings.isUseIncubatingAPIs()) {
            builder.includePluginsBuild();
        }

        if (settings.getJavaLanguageVersion().isPresent()) {
            builder.plugin(
                "Apply the foojay-resolver plugin to allow automatic download of JDKs",
                "org.gradle.toolchains.foojay-resolver-convention",
                "0.4.0");
        }

        builder.propertyAssignment(null, "rootProject.name", settings.getProjectName());
        if (!settings.getSubprojects().isEmpty()) {
            builder.methodInvocation(null, "include", settings.getSubprojects().toArray());
        }
        return builder;
    }
}
