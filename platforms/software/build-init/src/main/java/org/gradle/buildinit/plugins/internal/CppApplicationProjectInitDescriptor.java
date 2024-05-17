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
import org.gradle.buildinit.plugins.internal.modifiers.ComponentType;

public class CppApplicationProjectInitDescriptor extends CppProjectInitDescriptor {
    public CppApplicationProjectInitDescriptor(TemplateOperationFactory templateOperationFactory, DocumentationRegistry documentationRegistry) {
        super(templateOperationFactory, documentationRegistry);
    }

    @Override
    public String getId() {
        return "cpp-application";
    }

    @Override
    public ComponentType getComponentType() {
        return ComponentType.APPLICATION;
    }

    @Override
    protected TemplateOperation sourceTemplateOperation(InitSettings settings) {
        return fromCppTemplate("cppapp/app.cpp.template", settings, "main", "cpp");
    }

    @Override
    protected TemplateOperation headerTemplateOperation(InitSettings settings) {
        return fromCppTemplate("cppapp/app.h.template", settings, "main", "headers");
    }

    @Override
    protected TemplateOperation testTemplateOperation(InitSettings settings) {
        return fromCppTemplate("cppapp/app_test.cpp.template", settings, "test", "cpp");
    }

    @Override
    protected void configureBuildScript(InitSettings settings, BuildScriptBuilder buildScriptBuilder) {
        buildScriptBuilder
            .plugin(
                "Apply the cpp-application plugin to add support for building C++ executables",
                "cpp-application")
            .plugin("Apply the cpp-unit-test plugin to add support for building and running C++ test executables",
                "cpp-unit-test")
            .block("Set the target operating system and architecture for this application", "application",
                b -> b.methodInvocation(null, "targetMachines.add", buildScriptBuilder.propertyExpression(getHostTargetMachineDefinition())));
    }
}
