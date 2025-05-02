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
import org.gradle.buildinit.plugins.internal.modifiers.BuildInitTestFramework;
import org.gradle.buildinit.plugins.internal.modifiers.Language;
import org.gradle.buildinit.plugins.internal.modifiers.ModularizationOption;
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;

import static org.gradle.buildinit.plugins.internal.NamespaceBuilder.toNamespace;

public abstract class CppProjectInitDescriptor extends LanguageLibraryProjectInitDescriptor {
    private final TemplateOperationFactory templateOperationFactory;
    private final DocumentationRegistry documentationRegistry;

    public CppProjectInitDescriptor(TemplateOperationFactory templateOperationFactory, DocumentationRegistry documentationRegistry) {
        this.templateOperationFactory = templateOperationFactory;
        this.documentationRegistry = documentationRegistry;
    }

    @Override
    public Language getLanguage() {
        return Language.CPP;
    }

    @Override
    public void generateProjectBuildScript(String projectName, InitSettings settings, BuildScriptBuilder buildScriptBuilder) {
        buildScriptBuilder
            .fileComment("This generated file contains a sample C++ project to get you started.")
            .fileComment(documentationRegistry.getDocumentationRecommendationFor("details on building C++ applications and libraries", "building_cpp_projects"));
        configureBuildScript(settings, buildScriptBuilder);
    }

    @Override
    public void generateConventionPluginBuildScript(String conventionPluginName, InitSettings settings, BuildScriptBuilder buildScriptBuilder) {
    }

    @Override
    public void generateSources(InitSettings settings, TemplateFactory templateFactory) {
        TemplateOperation sourceTemplate = sourceTemplateOperation(settings);
        TemplateOperation headerTemplate = headerTemplateOperation(settings);
        TemplateOperation testSourceTemplate = testTemplateOperation(settings);

        templateFactory.whenNoSourcesAvailable(sourceTemplate, headerTemplate, testSourceTemplate).generate();
    }

    @Override
    public Set<BuildInitTestFramework> getTestFrameworks(ModularizationOption modularizationOption) {
        return Collections.singleton(BuildInitTestFramework.CPPTest);
    }

    @Override
    public BuildInitTestFramework getDefaultTestFramework(ModularizationOption modularizationOption) {
        return BuildInitTestFramework.CPPTest;
    }

    @Override
    public Optional<String> getFurtherReading(InitSettings settings) {
        return Optional.of(documentationRegistry.getSampleForMessage("building_cpp_" + getComponentType().pluralName()));
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
        return buildNativeHostTargetDefinition(host);
    }

    static String buildNativeHostTargetDefinition(DefaultNativePlatform host) {
        String definition = "machines.";

        if (host.getOperatingSystem().isWindows()) {
            definition += "windows";
        } else if (host.getOperatingSystem().isMacOsX()) {
            definition += "macOS";
        } else if (host.getOperatingSystem().isLinux()) {
            definition += "linux";
        } else {
            definition += "os(\"" + host.getOperatingSystem().toFamilyName() + "\")";
        }

        definition += ".";

        if (host.getArchitecture().isI386()) {
            definition += "x86";
        } else if (host.getArchitecture().isAmd64()) {
            definition += "x86_64";
        } else {
            definition += "architecture(\"" + host.getArchitecture().getName() + "\")";
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
            .withTarget(settings.getTarget().file(settings.getSubprojects().get(0) + "/src/" + sourceSetName + "/" + sourceDir + "/" + targetFileName).getAsFile())
            .withBinding("projectName", settings.getProjectName())
            .withBinding("namespace", namespace)
            .withBinding("fileComment", settings.isWithComments() ? "This source file was generated by the Gradle 'init' task" : "")
            .create();
    }
}
