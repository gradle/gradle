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

import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.buildinit.plugins.internal.modifiers.BuildInitDsl;
import org.gradle.buildinit.plugins.internal.modifiers.BuildInitTestFramework;
import org.gradle.buildinit.plugins.internal.modifiers.ComponentType;
import org.gradle.buildinit.plugins.internal.modifiers.Language;

import java.util.Optional;
import java.util.Set;

public class LanguageSpecificAdaptor implements ProjectGenerator {
    private final BuildScriptBuilderFactory scriptBuilderFactory;
    private final FileCollectionFactory fileCollectionFactory;
    private final TemplateOperationFactory templateOperationFactory;
    private final LanguageSpecificProjectGenerator descriptor;

    public LanguageSpecificAdaptor(LanguageSpecificProjectGenerator descriptor, BuildScriptBuilderFactory scriptBuilderFactory, FileCollectionFactory fileCollectionFactory, TemplateOperationFactory templateOperationFactory) {
        this.scriptBuilderFactory = scriptBuilderFactory;
        this.descriptor = descriptor;
        this.fileCollectionFactory = fileCollectionFactory;
        this.templateOperationFactory = templateOperationFactory;
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
    public Optional<String> getFurtherReading() {
        return descriptor.getFurtherReading();
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

    @Override
    public void generate(InitSettings settings) {
        BuildScriptBuilder buildScriptBuilder = scriptBuilderFactory.script(settings.getDsl(), "build");
        descriptor.generate(settings, buildScriptBuilder, new TemplateFactory(settings, descriptor.getLanguage(), fileCollectionFactory, templateOperationFactory));
        buildScriptBuilder.create().generate();
    }
}
