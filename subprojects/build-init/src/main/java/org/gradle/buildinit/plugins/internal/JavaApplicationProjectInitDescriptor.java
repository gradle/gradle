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

public class JavaApplicationProjectInitDescriptor extends JavaProjectInitDescriptor {
    private final TemplateLibraryVersionProvider libraryVersionProvider;

    public JavaApplicationProjectInitDescriptor(TemplateLibraryVersionProvider libraryVersionProvider, DocumentationRegistry documentationRegistry) {
        super(libraryVersionProvider, documentationRegistry);
        this.libraryVersionProvider = libraryVersionProvider;
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
        buildScriptBuilder.implementationDependency("This dependency is used by the application.",
            "com.google.guava:guava:" + libraryVersionProvider.getVersion("guava"));
    }

    @Override
    protected TemplateOperation sourceTemplateOperation(InitSettings settings, TemplateFactory templateFactory) {
        return templateFactory.fromSourceTemplate("javaapp/App.java.template", "main");
    }

    @Override
    protected TemplateOperation testTemplateOperation(InitSettings settings, TemplateFactory templateFactory) {
        switch (settings.getTestFramework()) {
            case SPOCK:
                return templateFactory.fromSourceTemplate("groovyapp/AppTest.groovy.template", "test", Language.GROOVY);
            case TESTNG:
                return templateFactory.fromSourceTemplate("javaapp/testng/AppTest.java.template", "test");
            case JUNIT:
                return templateFactory.fromSourceTemplate("javaapp/AppTest.java.template", "test");
            case JUNITJUPITER:
                return templateFactory.fromSourceTemplate("javaapp/junitjupiter/AppTest.java.template", "test");
            default:
                throw new IllegalArgumentException();
        }
    }
}
