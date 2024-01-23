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

import org.gradle.buildinit.plugins.internal.modifiers.BuildInitDsl;
import org.gradle.buildinit.plugins.internal.modifiers.BuildInitTestFramework;
import org.gradle.buildinit.plugins.internal.modifiers.ComponentType;
import org.gradle.buildinit.plugins.internal.modifiers.Language;
import org.gradle.buildinit.plugins.internal.modifiers.ModularizationOption;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Generator for some software and an associated Gradle build.
 */
public class SourceGeneratingBuildGenerator extends AbstractBuildGenerator implements CompositeProjectInitDescriptor {
    private final ProjectGenerator descriptor;
    private final List<? extends BuildContentGenerator> generators;

    public SourceGeneratingBuildGenerator(ProjectGenerator projectGenerator, List<? extends BuildContentGenerator> generators) {
        super(projectGenerator, generators);
        this.generators = generators;
        this.descriptor = projectGenerator;
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
    public boolean productionCodeUses(Language language) {
        return descriptor.getLanguage().equals(language);
    }

    @Override
    public List<String> getDefaultProjectNames() {
        return getComponentType().getDefaultProjectNames();
    }

    @Override
    public boolean supportsJavaTargets() {
        return descriptor.isJvmLanguage();
    }

    @Override
    public Set<ModularizationOption> getModularizationOptions() {
        return descriptor.getModularizationOptions();
    }

    @Override
    public boolean supportsPackage() {
        return descriptor.supportsPackage();
    }

    @Override
    public BuildInitDsl getDefaultDsl() {
        return descriptor.getDefaultDsl();
    }

    @Override
    public BuildInitTestFramework getDefaultTestFramework() {
        return getDefaultTestFramework(ModularizationOption.SINGLE_PROJECT);
    }

    @Override
    public BuildInitTestFramework getDefaultTestFramework(ModularizationOption modularizationOption) {
        return descriptor.getDefaultTestFramework(modularizationOption);
    }

    @Override
    public Set<BuildInitTestFramework> getTestFrameworks() {
        return getTestFrameworks(ModularizationOption.SINGLE_PROJECT);
    }

    @Override
    public Set<BuildInitTestFramework> getTestFrameworks(ModularizationOption modularizationOption) {
        return descriptor.getTestFrameworks(modularizationOption);
    }

    @Override
    public Optional<String> getFurtherReading(InitSettings settings) {
        return descriptor.getFurtherReading(settings);
    }

    @Override
    public Map<String, List<String>> generateWithExternalComments(InitSettings settings) {
        BuildContentGenerationContext buildContentGenerationContext = new BuildContentGenerationContext(new VersionCatalogDependencyRegistry(false));
        if (!(descriptor instanceof LanguageSpecificAdaptor)) {
            throw new UnsupportedOperationException();
        }
        for (BuildContentGenerator generator : generators) {
            if (generator instanceof SimpleGlobalFilesBuildSettingsDescriptor) {
                ((SimpleGlobalFilesBuildSettingsDescriptor) generator).generateWithoutComments(settings, buildContentGenerationContext);
            } else {
                generator.generate(settings, buildContentGenerationContext);
            }
        }
        Map<String, List<String>> comments = ((LanguageSpecificAdaptor) descriptor).generateWithExternalComments(settings, buildContentGenerationContext);
        VersionCatalogGenerator.create(settings.getTarget()).generate(buildContentGenerationContext, settings.isWithComments());
        return comments;
    }
}
