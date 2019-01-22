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
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform;

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

    protected String getHostTargetMachineDefinition() {
        DefaultNativePlatform host = DefaultNativePlatform.host();
        String definition = "machines.";

        if (host.getOperatingSystem().isWindows()) {
            definition += "windows";
        } else if (host.getOperatingSystem().isMacOsX()) {
            definition += "macOS";
        } else if (host.getOperatingSystem().isLinux()) {
            definition += "linux";
        } else {
            definition += "os('" + host.getOperatingSystem().toFamilyName() + "')";
        }

        definition += ".";

        if (host.getArchitecture().isI386()) {
            definition += "x86";
        } else if (host.getArchitecture().isAmd64()) {
            definition += "x86_64";
        } else {
            definition += "architecture('" + host.getArchitecture().getName() + "')";
        }

        return definition;
    }

    TemplateOperation fromCppTemplate(String template, InitSettings settings, String sourceSetName, String sourceDir) {
        String targetFileName = template.substring(template.lastIndexOf("/") + 1).replace(".template", "");
        return fromCppTemplate(template, targetFileName, settings, sourceSetName, sourceDir);
    }

    TemplateOperation fromCppTemplate(String template, String targetFileName, InitSettings settings, String sourceSetName, String sourceDir) {
        if (settings == null || settings.getProjectName().isEmpty()) {
            throw new IllegalArgumentException("Project name cannot be empty for a C++ project");
        }

        String namespace = toNamespace(settings.getProjectName());

        return templateOperationFactory.newTemplateOperation()
            .withTemplate(template)
            .withTarget("src/" + sourceSetName + "/" + sourceDir + "/" + targetFileName)
            .withBinding("projectName", settings.getProjectName())
            .withBinding("namespace", namespace)
            .create();
    }
}
