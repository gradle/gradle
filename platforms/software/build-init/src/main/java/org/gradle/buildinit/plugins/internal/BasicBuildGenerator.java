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

import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.buildinit.plugins.internal.modifiers.BuildInitDsl;
import org.gradle.buildinit.plugins.internal.modifiers.BuildInitTestFramework;
import org.gradle.buildinit.plugins.internal.modifiers.ComponentType;
import org.gradle.buildinit.plugins.internal.modifiers.Language;
import org.gradle.buildinit.plugins.internal.modifiers.ModularizationOption;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static java.util.Collections.singleton;
import static org.gradle.buildinit.plugins.internal.modifiers.BuildInitDsl.KOTLIN;
import static org.gradle.buildinit.plugins.internal.modifiers.BuildInitTestFramework.NONE;
import static org.gradle.buildinit.plugins.internal.modifiers.ModularizationOption.SINGLE_PROJECT;

/**
 * Generator for a "basic" Gradle build.
 */
public class BasicBuildGenerator extends AbstractBuildGenerator {
    private final DocumentationRegistry documentationRegistry;

    public BasicBuildGenerator(BuildScriptBuilderFactory scriptBuilderFactory, DocumentationRegistry documentationRegistry, List<? extends BuildContentGenerator> generators) {
        super(new BasicProjectGenerator(scriptBuilderFactory, documentationRegistry), generators);
        this.documentationRegistry = documentationRegistry;
    }

    @Override
    public String getId() {
        return "basic";
    }

    @Override
    public ComponentType getComponentType() {
        return ComponentType.BASIC;
    }

    @Override
    public boolean productionCodeUses(Language language) {
        return false;
    }

    @Override
    public List<String> getDefaultProjectNames() {
        return getComponentType().getDefaultProjectNames();
    }

    @Override
    public boolean supportsJavaTargets() {
        return false;
    }

    @Override
    public Set<ModularizationOption> getModularizationOptions() {
        return singleton(SINGLE_PROJECT);
    }

    @Override
    public boolean supportsPackage() {
        return false;
    }

    @Override
    public BuildInitDsl getDefaultDsl() {
        return KOTLIN;
    }

    @Override
    public BuildInitTestFramework getDefaultTestFramework(ModularizationOption modularizationOption) {
        return NONE;
    }

    @Override
    public Set<BuildInitTestFramework> getTestFrameworks(ModularizationOption modularizationOption) {
        return singleton(NONE);
    }

    @Override
    public Optional<String> getFurtherReading(InitSettings settings) {
        return Optional.of(documentationRegistry.getSampleForMessage());
    }
}
