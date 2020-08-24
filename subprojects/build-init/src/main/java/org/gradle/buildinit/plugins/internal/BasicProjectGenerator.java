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
        scriptBuilderFactory.script(settings.getDsl(), "build")
            .fileComment("This is a general purpose Gradle build.\n"
                + "Learn how to create Gradle builds at " + documentationRegistry.getGuideFor("creating-new-gradle-builds"))
            .create()
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
    public BuildInitDsl getDefaultDsl() {
        return BuildInitDsl.GROOVY;
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
    public Optional<String> getFurtherReading() {
        return Optional.of(documentationRegistry.getGuideFor("creating-new-gradle-builds"));
    }
}
