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

import com.google.common.collect.Sets;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.buildinit.plugins.internal.modifiers.BuildInitTestFramework;

import java.util.Set;

import static org.gradle.buildinit.plugins.internal.NamespaceBuilder.toNamespace;

public abstract class CppProjectInitDescriptor extends LanguageLibraryProjectInitDescriptor {
    public CppProjectInitDescriptor(BuildScriptBuilderFactory scriptBuilderFactory, TemplateOperationFactory templateOperationFactory, FileResolver fileResolver, TemplateLibraryVersionProvider libraryVersionProvider) {
        super("cpp", scriptBuilderFactory, templateOperationFactory, fileResolver, libraryVersionProvider);
    }

    @Override
    protected void generate(InitSettings settings, BuildScriptBuilder buildScriptBuilder) {
        buildScriptBuilder
            .fileComment("This generated file contains a sample CPP project to get you started.");
        configureBuildScript(settings, buildScriptBuilder);

        TemplateOperation sourceTemplate = sourceTemplateOperation(settings);
        TemplateOperation headerTemplate = headerTemplateOperation(settings);
        TemplateOperation testSourceTemplate = testTemplateOperation(settings);
        whenNoSourcesAvailable(sourceTemplate, headerTemplate, testSourceTemplate).generate();
    }

    @Override
    public Set<BuildInitTestFramework> getTestFrameworks() {
        return Sets.newHashSet();
    }

    @Override
    public BuildInitTestFramework getDefaultTestFramework() {
        return null;
    }

    protected abstract TemplateOperation sourceTemplateOperation(InitSettings settings);

    protected abstract TemplateOperation headerTemplateOperation(InitSettings settings);

    protected abstract TemplateOperation testTemplateOperation(InitSettings settings);

    protected void configureBuildScript(InitSettings settings, BuildScriptBuilder buildScriptBuilder) {
    }

    @Override
    public boolean supportsPackage() {
        return false;
    }

    TemplateOperation fromCppTemplate(String template, InitSettings settings, String sourceSetName, String sourceDir) {
        String targetFileName = template.substring(template.lastIndexOf("/") + 1).replace(".template", "");
        String namespace = "";
        if (settings != null && !settings.getProjectName().isEmpty()) {
            namespace = toNamespace(settings.getProjectName());
        }
        return templateOperationFactory.newTemplateOperation()
            .withTemplate(template)
            .withTarget("src/" + sourceSetName + "/" + sourceDir + "/" + targetFileName)
            .withBinding("namespace", namespace)
            .create();
    }
}
