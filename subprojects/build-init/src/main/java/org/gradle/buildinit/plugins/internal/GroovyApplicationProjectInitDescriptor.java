/*
 * Copyright 2017 the original author or authors.
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

public class GroovyApplicationProjectInitDescriptor extends GroovyProjectInitDescriptor {
    public GroovyApplicationProjectInitDescriptor(TemplateLibraryVersionProvider libraryVersionProvider, DocumentationRegistry documentationRegistry) {
        super(libraryVersionProvider, documentationRegistry);
    }

    @Override
    public String getId() {
        return "groovy-application";
    }

    @Override
    public ComponentType getComponentType() {
        return ComponentType.APPLICATION;
    }

    @Override
    protected void configureBuildScript(InitSettings settings, BuildScriptBuilder buildScriptBuilder) {
        buildScriptBuilder
            .plugin(
                "Apply the application plugin to add support for building a CLI application.",
                "application")
            .block(null, "application", b -> {
                b.propertyAssignment("Define the main class for the application.", "mainClassName", withPackage(settings, "App"));
            });
    }

    @Override
    protected TemplateOperation sourceTemplateOperation(TemplateFactory templateFactory) {
        return templateFactory.fromSourceTemplate("groovyapp/App.groovy.template", "main");
    }

    @Override
    protected TemplateOperation testTemplateOperation(TemplateFactory templateFactory) {
        return templateFactory.fromSourceTemplate("groovyapp/AppTest.groovy.template", "test");
    }
}
