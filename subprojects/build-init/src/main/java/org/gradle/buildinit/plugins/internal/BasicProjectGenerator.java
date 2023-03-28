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
import org.gradle.buildinit.plugins.internal.modifiers.BuildInitDsl;
import org.gradle.buildinit.plugins.internal.modifiers.BuildInitTestFramework;
import org.gradle.buildinit.plugins.internal.modifiers.ComponentType;
import org.gradle.buildinit.plugins.internal.modifiers.Language;
import org.gradle.buildinit.plugins.internal.modifiers.ModularizationOption;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;

public class BasicProjectGenerator implements ProjectGenerator {
    private final BuildScriptBuilderFactory scriptBuilderFactory;
    private final DocumentationRegistry documentationRegistry;

    public BasicProjectGenerator(BuildScriptBuilderFactory scriptBuilderFactory, DocumentationRegistry documentationRegistry) {
        this.scriptBuilderFactory = scriptBuilderFactory;
        this.documentationRegistry = documentationRegistry;
    }

    @Override
    public String getId() {
        return "basic";
    }

    @Override
    public void generate(InitSettings settings) {
        scriptBuilderFactory.scriptForNewProjects(settings.getDsl(), "build", settings.isUseIncubatingAPIs())
            .fileComment("This is a general purpose Gradle build.\n"
                + "Learn more about Gradle by exploring our samples at " + documentationRegistry.getSampleIndex())
            .create(settings.getTarget())
            .generate();
    }

    @Override
    public ComponentType getComponentType() {
        return ComponentType.BASIC;
    }

    @Override
    public Language getLanguage() {
        return Language.NONE;
    }

    @Override
    public boolean isJvmLanguage() {
        return false;
    }

    @Override
    public Set<ModularizationOption> getModularizationOptions() {
        return Collections.singleton(ModularizationOption.SINGLE_PROJECT);
    }

    @Override
    public BuildInitDsl getDefaultDsl() {
        return BuildInitDsl.KOTLIN;
    }

    @Override
    public boolean supportsPackage() {
        return false;
    }

    @Override
    public BuildInitTestFramework getDefaultTestFramework() {
        return BuildInitTestFramework.NONE;
    }

    @Override
    public Set<BuildInitTestFramework> getTestFrameworks() {
        return Collections.singleton(BuildInitTestFramework.NONE);
    }

    @Override
    public Optional<String> getFurtherReading(InitSettings settings) {
        return Optional.of("Learn more about Gradle by exploring our samples at " + documentationRegistry.getSampleIndex());
    }
}
