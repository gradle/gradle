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

    public void generateWithoutComments(InitSettings settings, BuildContentGenerationContext buildContentGenerationContext) {
        builder(settings, buildContentGenerationContext)
            .withComments(settings.isWithComments() ? BuildInitComments.EXTERNAL : BuildInitComments.OFF)
            .create(settings.getTarget())
            .generate();
    }

    @Override
    public void generate(InitSettings settings, BuildContentGenerationContext buildContentGenerationContext) {
        builder(settings, buildContentGenerationContext).create(settings.getTarget()).generate();
    }

    private BuildScriptBuilder builder(InitSettings settings, BuildContentGenerationContext buildContentGenerationContext) {
        BuildScriptBuilder builder = scriptBuilderFactory.scriptForNewProjectsWithoutVersionCatalog(settings.getDsl(), buildContentGenerationContext, "settings", settings.isUseIncubatingAPIs());
        builder.withComments(settings.isWithComments() ? BuildInitComments.ON : BuildInitComments.OFF);
        builder.fileComment("The settings file is used to specify which projects to include in your build.\n\n")
            .fileComment(documentationRegistry.getDocumentationRecommendationFor("detailed information on multi-project builds", "multi_project_builds"));
        if (settings.getModularizationOption() == ModularizationOption.WITH_LIBRARY_PROJECTS && settings.isUseIncubatingAPIs()) {
            builder.includePluginsBuild();
        }

        if (settings.getJavaLanguageVersion().isPresent()) {
            builder.plugin(
                "Apply the foojay-resolver plugin to allow automatic download of JDKs",
                "org.gradle.toolchains.foojay-resolver-convention",
                "0.8.0",
                null
            );
        }

        builder.propertyAssignment(null, "rootProject.name", settings.getProjectName());
        if (!settings.getSubprojects().isEmpty()) {
            builder.methodInvocation(null, "include", settings.getSubprojects().toArray());
        }
        return builder;
    }
}
