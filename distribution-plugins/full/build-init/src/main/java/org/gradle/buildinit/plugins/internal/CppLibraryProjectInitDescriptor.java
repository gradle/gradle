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

import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.buildinit.plugins.internal.modifiers.ComponentType;

public class CppLibraryProjectInitDescriptor extends CppProjectInitDescriptor {
    public CppLibraryProjectInitDescriptor(TemplateOperationFactory templateOperationFactory, DocumentationRegistry documentationRegistry) {
        super(templateOperationFactory, documentationRegistry);
    }

    @Override
    public String getId() {
        return "cpp-library";
    }

    @Override
    public ComponentType getComponentType() {
        return ComponentType.LIBRARY;
    }

    @Override
    protected TemplateOperation sourceTemplateOperation(InitSettings settings) {
        return fromCppTemplate("cpplibrary/hello.cpp.template", settings, "main", "cpp");
    }

    @Override
    protected TemplateOperation headerTemplateOperation(InitSettings settings) {
        return fromCppTemplate("cpplibrary/hello.h.template", settings.getProjectName() + ".h", settings, "main", "public");
    }

    @Override
    protected TemplateOperation testTemplateOperation(InitSettings settings) {
        return fromCppTemplate("cpplibrary/hello_test.cpp.template", settings, "test", "cpp");
    }

    @Override
    protected void configureBuildScript(InitSettings settings, BuildScriptBuilder buildScriptBuilder) {
        buildScriptBuilder
            .plugin(
                "Apply the cpp-library plugin to add support for building C++ libraries",
                "cpp-library")
            .plugin("Apply the cpp-unit-test plugin to add support for building and running C++ test executables",
                "cpp-unit-test")
            .block(null,
                "library",
                b -> b.methodInvocation("Set the target operating system and architecture for this library", "targetMachines.add", buildScriptBuilder.propertyExpression(getHostTargetMachineDefinition())));
    }
}
