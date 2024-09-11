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

import org.gradle.buildinit.plugins.internal.modifiers.BuildInitDsl;
import org.gradle.buildinit.plugins.internal.modifiers.BuildInitTestFramework;
import org.gradle.buildinit.plugins.internal.modifiers.ComponentType;
import org.gradle.buildinit.plugins.internal.modifiers.Language;
import org.gradle.buildinit.plugins.internal.modifiers.ModularizationOption;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.gradle.buildinit.plugins.internal.SimpleGlobalFilesBuildSettingsDescriptor.PLUGINS_BUILD_LOCATION;

public class LanguageSpecificAdaptor implements ProjectGenerator {
    private static final List<String> SAMPLE_CONVENTION_PLUGINS = Arrays.asList("common", "application", "library");

    private final BuildScriptBuilderFactory scriptBuilderFactory;
    private final TemplateOperationFactory templateOperationFactory;
    private final LanguageSpecificProjectGenerator descriptor;
    private final TemplateLibraryVersionProvider libraryVersionProvider;

    public LanguageSpecificAdaptor(LanguageSpecificProjectGenerator descriptor, BuildScriptBuilderFactory scriptBuilderFactory, TemplateOperationFactory templateOperationFactory, TemplateLibraryVersionProvider libraryVersionProvider) {
        this.scriptBuilderFactory = scriptBuilderFactory;
        this.descriptor = descriptor;
        this.templateOperationFactory = templateOperationFactory;
        this.libraryVersionProvider = libraryVersionProvider;
    }

    @Override
    public String getId() {
        return descriptor.getId();
    }

    @Override
    public ComponentType getComponentType() {
        return descriptor.getComponentType();
    }

    @Override
    public Language getLanguage() {
        return descriptor.getLanguage();
    }

    @Override
    public boolean isJvmLanguage() {
        return descriptor.isJvmLanguage();
    }

    @Override
    public Set<ModularizationOption> getModularizationOptions() {
        return descriptor.getModularizationOptions();
    }

    @Override
    public Optional<String> getFurtherReading(InitSettings settings) {
        return descriptor.getFurtherReading(settings);
    }

    @Override
    public BuildInitDsl getDefaultDsl() {
        if (descriptor.getLanguage().equals(Language.GROOVY)) {
            return BuildInitDsl.GROOVY;
        }
        return BuildInitDsl.KOTLIN;
    }

    @Override
    public Set<BuildInitTestFramework> getTestFrameworks(ModularizationOption modularizationOption) {
        return descriptor.getTestFrameworks(modularizationOption);
    }

    @Override
    public BuildInitTestFramework getDefaultTestFramework(ModularizationOption modularizationOption) {
        return descriptor.getDefaultTestFramework(modularizationOption);
    }

    @Override
    public boolean supportsPackage() {
        return descriptor.supportsPackage();
    }

    public Map<String, List<String>> generateWithExternalComments(InitSettings settings, BuildContentGenerationContext buildContentGenerationContext) {
        HashMap<String, List<String>> comments = new HashMap<>();
        for (BuildScriptBuilder buildScriptBuilder : allBuildScriptBuilder(settings, buildContentGenerationContext)) {
            buildScriptBuilder.withComments(settings.isWithComments() ? BuildInitComments.EXTERNAL : BuildInitComments.OFF)
                .create(settings.getTarget())
                .generate();

            if (settings.isWithComments()) {
                comments.put(buildScriptBuilder.getFileNameWithoutExtension(), buildScriptBuilder.extractComments());
            }
        }
        return comments;
    }

    @Override
    public void generate(InitSettings settings, BuildContentGenerationContext buildContentGenerationContext) {
        for (BuildScriptBuilder buildScriptBuilder : allBuildScriptBuilder(settings, buildContentGenerationContext)) {
            buildScriptBuilder.create(settings.getTarget()).generate();
        }
    }

    private List<BuildScriptBuilder> allBuildScriptBuilder(InitSettings settings, BuildContentGenerationContext buildContentGenerationContext) {
        List<BuildScriptBuilder> builder = new ArrayList<>();

        if (settings.getModularizationOption() == ModularizationOption.WITH_LIBRARY_PROJECTS) {
            builder.add(pluginsBuildSettingsScriptBuilder(settings, buildContentGenerationContext));
            builder.add(pluginsBuildBuildScriptBuilder(settings, buildContentGenerationContext));
            for (String conventionPluginName : SAMPLE_CONVENTION_PLUGINS) {
                builder.add(conventionPluginScriptBuilder(conventionPluginName, settings, buildContentGenerationContext));
            }
        }

        for (String subproject : settings.getSubprojects()) {
            builder.add(projectBuildScriptBuilder(subproject, settings, buildContentGenerationContext, subproject + "/build"));
        }

        TemplateFactory templateFactory = new TemplateFactory(settings, descriptor.getLanguage(), templateOperationFactory);
        descriptor.generateSources(settings, templateFactory);

        return builder;
    }

    private BuildScriptBuilder pluginsBuildSettingsScriptBuilder(InitSettings settings, BuildContentGenerationContext buildContentGenerationContext) {
        BuildScriptBuilder builder = scriptBuilderFactory.scriptForNewProjectsWithoutVersionCatalog(settings.getDsl(), buildContentGenerationContext, pluginsBuildLocation(settings) + "/settings", settings.isUseIncubatingAPIs());
        builder.withComments(settings.isWithComments() ? BuildInitComments.ON : BuildInitComments.OFF);
        builder.fileComment("This settings file is used to specify which projects to include in your build-logic build.");
        builder.propertyAssignment(null, "rootProject.name", settings.isUseIncubatingAPIs() ? "build-logic" : "buildSrc");
        builder.useVersionCatalogFromOuterBuild("Reuse version catalog from the main build.");
        return builder;
    }

    private BuildScriptBuilder pluginsBuildBuildScriptBuilder(InitSettings settings, BuildContentGenerationContext buildContentGenerationContext) {
        BuildScriptBuilder pluginsBuildScriptBuilder = scriptBuilderFactory.scriptForNewProjects(settings.getDsl(), buildContentGenerationContext, pluginsBuildLocation(settings) + "/build", settings.isUseIncubatingAPIs());
        pluginsBuildScriptBuilder.withComments(settings.isWithComments() ? BuildInitComments.ON : BuildInitComments.OFF);
        pluginsBuildScriptBuilder.conventionPluginSupport("Support convention plugins written in " + settings.getDsl().toString() + ". Convention plugins are build scripts in 'src/main' that automatically become available as plugins in the main build.");
        if (getLanguage() == Language.KOTLIN) {
            pluginsBuildScriptBuilder.implementationDependency(null, BuildInitDependency.of("org.jetbrains.kotlin:kotlin-gradle-plugin", libraryVersionProvider.getVersion("kotlin")));
        }
        return pluginsBuildScriptBuilder;
    }

    private BuildScriptBuilder projectBuildScriptBuilder(String projectName, InitSettings settings, BuildContentGenerationContext buildContentGenerationContext, String buildFile) {
        BuildScriptBuilder buildScriptBuilder = scriptBuilderFactory.scriptForNewProjects(settings.getDsl(), buildContentGenerationContext, buildFile, settings.isUseIncubatingAPIs());
        buildScriptBuilder.withComments(settings.isWithComments() ? BuildInitComments.ON : BuildInitComments.OFF);
        descriptor.generateProjectBuildScript(projectName, settings, buildScriptBuilder);
        return buildScriptBuilder;
    }

    private BuildScriptBuilder conventionPluginScriptBuilder(String conventionPluginName, InitSettings settings, BuildContentGenerationContext buildContentGenerationContext) {
        BuildScriptBuilder buildScriptBuilder = scriptBuilderFactory.scriptForNewProjectsWithoutVersionCatalog(settings.getDsl(), buildContentGenerationContext,
            pluginsBuildLocation(settings) + "/src/main/" + settings.getDsl().name().toLowerCase(Locale.ROOT) + "/"
                + InitSettings.CONVENTION_PLUGIN_NAME_PREFIX + "." + getLanguage().getName() + "-" + conventionPluginName + "-conventions",
            settings.isUseIncubatingAPIs());
        buildScriptBuilder.withComments(settings.isWithComments() ? BuildInitComments.ON : BuildInitComments.OFF);
        descriptor.generateConventionPluginBuildScript(conventionPluginName, settings, buildScriptBuilder);
        return buildScriptBuilder;
    }

    private String pluginsBuildLocation(InitSettings settings) {
        if (settings.isUseIncubatingAPIs()) {
            return PLUGINS_BUILD_LOCATION;
        } else {
            return "buildSrc";
        }
    }
}
