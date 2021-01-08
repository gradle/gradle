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
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
    public Set<ModularizationOption> getModularizationOptions() {
        return descriptor.getModularizationOptions();
    }

    @Override
    public Optional<String> getFurtherReading(InitSettings settings) {
        return descriptor.getFurtherReading(settings);
    }

    @Override
    public BuildInitDsl getDefaultDsl() {
        if (descriptor.getLanguage().equals(Language.KOTLIN)) {
            return BuildInitDsl.KOTLIN;
        }
        return BuildInitDsl.GROOVY;
    }

    @Override
    public Set<BuildInitTestFramework> getTestFrameworks() {
        return descriptor.getTestFrameworks();
    }

    @Override
    public BuildInitTestFramework getDefaultTestFramework() {
        return descriptor.getDefaultTestFramework();
    }

    @Override
    public boolean supportsPackage() {
        return descriptor.supportsPackage();
    }

    public Map<String, List<String>> generateWithExternalComments(InitSettings settings) {
        HashMap<String, List<String>> comments = new HashMap<>();
        for(BuildScriptBuilder buildScriptBuilder : allBuildScriptBuilder(settings)) {
            buildScriptBuilder.withExternalComments().create(settings.getTarget()).generate();
            comments.put(buildScriptBuilder.getFileNameWithoutExtension(), buildScriptBuilder.extractComments());
        }
        return comments;
    }

    @Override
    public void generate(InitSettings settings) {
        for(BuildScriptBuilder buildScriptBuilder : allBuildScriptBuilder(settings)) {
            buildScriptBuilder.create(settings.getTarget()).generate();
        }
    }

    private List<BuildScriptBuilder> allBuildScriptBuilder(InitSettings settings) {
        List<BuildScriptBuilder> builder = new ArrayList<>();

        if (settings.getModularizationOption() == ModularizationOption.WITH_LIBRARY_PROJECTS) {
            builder.add(buildSrcSetup(settings));
            for(String conventionPluginName: SAMPLE_CONVENTION_PLUGINS) {
                builder.add(conventionPluginScriptBuilder(conventionPluginName, settings));
            }
        }

        for (String subproject : settings.getSubprojects()) {
            builder.add(projectBuildScriptBuilder(subproject, settings, subproject + "/build"));
        }

        TemplateFactory templateFactory = new TemplateFactory(settings, descriptor.getLanguage(), templateOperationFactory);
        descriptor.generateSources(settings, templateFactory);

        return builder;
    }

    private BuildScriptBuilder buildSrcSetup(InitSettings settings) {
        BuildScriptBuilder buildSrcScriptBuilder = scriptBuilderFactory.script(settings.getDsl(), "buildSrc/build");
        buildSrcScriptBuilder.conventionPluginSupport("Support convention plugins written in " + settings.getDsl().toString() + ". Convention plugins are build scripts in 'src/main' that automatically become available as plugins in the main build.");
        if (getLanguage() == Language.KOTLIN) {
            String kotlinPluginCoordinates = "org.jetbrains.kotlin:kotlin-gradle-plugin";
            if (settings.getDsl() == BuildInitDsl.GROOVY) {
                // we don't get a Kotlin version from context without the 'kotlin-dsl' plugin
                kotlinPluginCoordinates = kotlinPluginCoordinates + ":" + libraryVersionProvider.getVersion("kotlin");
            }
            buildSrcScriptBuilder.implementationDependency(null, kotlinPluginCoordinates);
        }
        return buildSrcScriptBuilder;
    }

    private BuildScriptBuilder projectBuildScriptBuilder(String projectName, InitSettings settings, String buildFile) {
        BuildScriptBuilder buildScriptBuilder = scriptBuilderFactory.script(settings.getDsl(), buildFile);
        descriptor.generateProjectBuildScript(projectName, settings, buildScriptBuilder);
        return buildScriptBuilder;
    }

    private BuildScriptBuilder conventionPluginScriptBuilder(String conventionPluginName, InitSettings settings) {
        BuildScriptBuilder buildScriptBuilder = scriptBuilderFactory.script(settings.getDsl(),
            "buildSrc/src/main/" + settings.getDsl().name().toLowerCase() + "/" + settings.getPackageName() + "." + getLanguage().getName() + "-" + conventionPluginName + "-conventions");
        descriptor.generateConventionPluginBuildScript(conventionPluginName, settings, buildScriptBuilder);
        return buildScriptBuilder;
    }
}
