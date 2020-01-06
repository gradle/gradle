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
    public void generate(InitSettings settings, BuildScriptBuilder buildScriptBuilder, TemplateFactory templateFactory) {
        buildScriptBuilder
            .fileComment("This generated file contains a sample Swift project to get you started.")
            .fileComment("For more details take a look at the Building Swift applications and libraries chapter in the Gradle")
            .fileComment("User Manual available at " + documentationRegistry.getDocumentationFor("building_swift_projects"));
        configureBuildScript(settings, buildScriptBuilder);

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
    public Optional<String> getFurtherReading() {
        return Optional.of(documentationRegistry.getDocumentationFor("building_swift_projects"));
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

        String definition = "machines.";

        if (host.getOperatingSystem().isMacOsX()) {
            definition += "macOS";
        } else if (host.getOperatingSystem().isLinux()) {
            definition += "linux";
        } else {
            definition += "os('" + host.getOperatingSystem().toFamilyName() + "')";
        }

        definition += ".x86_64";

        return definition;
    }

    protected void configureTargetMachineDefinition(ScriptBlockBuilder buildScriptBuilder) {
        if (OperatingSystem.current().isWindows()) {
            buildScriptBuilder.methodInvocation("Swift tool chain does not support Windows. The following targets macOS and Linux:", "targetMachines.add", buildScriptBuilder.propertyExpression("machines.macOS.x86_64"));
            buildScriptBuilder.methodInvocation(null, "targetMachines.add", buildScriptBuilder.propertyExpression("machines.linux.x86_64"));
        } else {
            buildScriptBuilder.methodInvocation(null, "targetMachines.add", buildScriptBuilder.propertyExpression(getHostTargetMachineDefinition()));
        }
    }

    TemplateOperation fromSwiftTemplate(String template, InitSettings settings, String sourceSetName, String sourceDir) {
        String targetFileName = template.substring(template.lastIndexOf("/") + 1).replace(".template", "");
        return fromSwiftTemplate(template, targetFileName, settings, sourceSetName, sourceDir);
    }

    TemplateOperation fromSwiftTemplate(String template, String targetFileName, InitSettings settings, String sourceSetName, String sourceDir) {
        if (settings == null || settings.getProjectName().isEmpty()) {
            throw new IllegalArgumentException("Project name cannot be empty for a Swift project");
        }

        String moduleName = toModuleName(settings.getProjectName());

        return templateOperationFactory.newTemplateOperation()
            .withTemplate(template)
            .withTarget("src/" + sourceSetName + "/" + sourceDir + "/" + targetFileName)
            .withBinding("projectName", settings.getProjectName())
            .withBinding("moduleName", moduleName)
            .create();
    }
}
