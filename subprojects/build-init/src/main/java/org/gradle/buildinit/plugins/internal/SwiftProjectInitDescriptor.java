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

import com.google.common.collect.Sets;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.buildinit.plugins.internal.modifiers.BuildInitTestFramework;
import org.gradle.buildinit.plugins.internal.modifiers.Language;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform;

import java.util.Optional;
import java.util.Set;

import static org.gradle.buildinit.plugins.internal.ModuleNameBuilder.toModuleName;

public abstract class SwiftProjectInitDescriptor extends LanguageLibraryProjectInitDescriptor {
    private final TemplateOperationFactory templateOperationFactory;
    private final DocumentationRegistry documentationRegistry;

    public SwiftProjectInitDescriptor(TemplateOperationFactory templateOperationFactory, DocumentationRegistry documentationRegistry) {
        this.templateOperationFactory = templateOperationFactory;
        this.documentationRegistry = documentationRegistry;
    }

    @Override
    public Language getLanguage() {
        return Language.SWIFT;
    }

    @Override
    public void generateProjectBuildScript(String projectName, InitSettings settings, BuildScriptBuilder buildScriptBuilder) {
        buildScriptBuilder
            .fileComment("This generated file contains a sample Swift project to get you started.")
            .fileComment(documentationRegistry.getDocumentationRecommendationFor("details on building Swift applications and libraries", "building_swift_projects"));
        configureBuildScript(settings, buildScriptBuilder);
    }

    @Override
    public void generateConventionPluginBuildScript(String conventionPluginName, InitSettings settings, BuildScriptBuilder buildScriptBuilder) {
    }

    @Override
    public void generateSources(InitSettings settings, TemplateFactory templateFactory) {
        TemplateOperation sourceTemplate = sourceTemplateOperation(settings);
        TemplateOperation testSourceTemplate = testTemplateOperation(settings);
        TemplateOperation testEntryPointTemplate = testEntryPointTemplateOperation(settings);
        templateFactory.whenNoSourcesAvailable(sourceTemplate, testSourceTemplate, testEntryPointTemplate).generate();
    }

    @Override
    public Set<BuildInitTestFramework> getTestFrameworks() {
        return Sets.newHashSet();
    }

    @Override
    public BuildInitTestFramework getDefaultTestFramework() {
        return null;
    }

    @Override
    public Optional<String> getFurtherReading(InitSettings settings) {
        return Optional.of(documentationRegistry.getSampleForMessage("building_swift_" + getComponentType().pluralName()));
    }

    protected abstract TemplateOperation sourceTemplateOperation(InitSettings settings);

    protected abstract TemplateOperation testTemplateOperation(InitSettings settings);

    protected abstract TemplateOperation testEntryPointTemplateOperation(InitSettings settings);

    protected void configureBuildScript(InitSettings settings, BuildScriptBuilder buildScriptBuilder) {
    }

    @Override
    public boolean supportsPackage() {
        return false;
    }

    protected String getHostTargetMachineDefinition() {
        DefaultNativePlatform host = DefaultNativePlatform.host();
        assert !host.getOperatingSystem().isWindows();
        return CppProjectInitDescriptor.buildNativeHostTargetDefinition(host);
    }

    protected void configureTargetMachineDefinition(ScriptBlockBuilder buildScriptBuilder) {
        if (OperatingSystem.current().isWindows()) {
            buildScriptBuilder.methodInvocation("Swift tool chain does not support Windows. The following targets macOS and Linux:", "targetMachines.add", buildScriptBuilder.propertyExpression("machines.macOS.x86_64"));
            buildScriptBuilder.methodInvocation(null, "targetMachines.add", buildScriptBuilder.propertyExpression("machines.linux.x86_64"));
        } else {
            buildScriptBuilder.methodInvocation("Set the target operating system and architecture for this library", "targetMachines.add", buildScriptBuilder.propertyExpression(getHostTargetMachineDefinition()));
        }
    }

    TemplateOperation fromSwiftTemplate(String template, InitSettings settings, String sourceSetName, @SuppressWarnings("SameParameterValue") String sourceDir) {
        String targetFileName = template.substring(template.lastIndexOf("/") + 1).replace(".template", "");
        return fromSwiftTemplate(template, targetFileName, settings, sourceSetName, sourceDir);
    }

    TemplateOperation fromSwiftTemplate(String template, String targetFileName, InitSettings settings, String sourceSetName, String sourceDir) {
        if (settings == null || settings.getProjectName().isEmpty()) {
            throw new IllegalArgumentException("Project name cannot be empty for a Swift project");
        }

        String moduleName = toModuleName(settings.getSubprojects().get(0));

        return templateOperationFactory.newTemplateOperation()
            .withTemplate(template)
            .withTarget(settings.getTarget().file(settings.getSubprojects().get(0) + "/src/" + sourceSetName + "/" + sourceDir + "/" + targetFileName).getAsFile())
            .withBinding("projectName", settings.getProjectName())
            .withBinding("moduleName", moduleName)
            .create();
    }
}
