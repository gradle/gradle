/*
 * Copyright 2016 the original author or authors.
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
import org.gradle.api.internal.file.FileResolver;

public class JavaApplicationProjectInitDescriptor extends JavaProjectInitDescriptor {
    public JavaApplicationProjectInitDescriptor(BuildScriptBuilderFactory scriptBuilderFactory, TemplateOperationFactory templateOperationFactory, FileResolver fileResolver, TemplateLibraryVersionProvider libraryVersionProvider, DocumentationRegistry documentationRegistry) {
        super(scriptBuilderFactory, templateOperationFactory, fileResolver, libraryVersionProvider, documentationRegistry);
    }

    @Override
    public String getId() {
        return "java-application";
    }

    @Override
    protected void configureBuildScript(InitSettings settings, BuildScriptBuilder buildScriptBuilder) {
        super.configureBuildScript(settings, buildScriptBuilder);
        buildScriptBuilder
            .plugin(
                "Apply the application plugin to add support for building an application",
                "application")
            .conventionPropertyAssignment(
                "Define the main class for the application",
                "application", "mainClassName", withPackage(settings, "App"));
    }

    @Override
    protected TemplateOperation sourceTemplateOperation(InitSettings settings) {
        return fromClazzTemplate("javaapp/App.java.template", settings, "main");
    }

    @Override
    protected TemplateOperation testTemplateOperation(InitSettings settings) {
        switch (settings.getTestFramework()) {
            case SPOCK:
                return fromClazzTemplate("groovyapp/AppTest.groovy.template", settings, "test", "groovy");
            case TESTNG:
                return fromClazzTemplate("javaapp/testng/AppTest.java.template", settings, "test", "java");
            case JUNIT:
                return fromClazzTemplate("javaapp/AppTest.java.template", settings, "test");
            default:
                throw new IllegalArgumentException();
        }
    }
}
