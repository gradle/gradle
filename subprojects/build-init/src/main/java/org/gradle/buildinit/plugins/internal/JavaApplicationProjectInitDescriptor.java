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
    public JavaApplicationProjectInitDescriptor(TemplateOperationFactory templateOperationFactory, FileResolver fileResolver, TemplateLibraryVersionProvider libraryVersionProvider, ProjectInitDescriptor projectInitDescriptor, DocumentationRegistry documentationRegistry) {
        super(templateOperationFactory, fileResolver, libraryVersionProvider, projectInitDescriptor, documentationRegistry);
    }

    @Override
    protected void configureBuildScript(BuildScriptBuilder buildScriptBuilder) {
        super.configureBuildScript(buildScriptBuilder);
        buildScriptBuilder
            .plugin("Apply the application plugin to add support for building an application", "application")
            .configuration("Define the main class for the application", "mainClassName = 'App'");
    }

    @Override
    protected TemplateOperation sourceTemplateOperation() {
        return fromClazzTemplate("javaapp/App.java.template", "main");
    }

    @Override
    protected TemplateOperation testTemplateOperation(BuildInitTestFramework testFramework) {
        switch (testFramework) {
            case SPOCK:
                return fromClazzTemplate("javaapp/AppTest.groovy.template", "test", "groovy");
            case TESTNG:
                return fromClazzTemplate("javaapp/AppTestNG.java.template", "test", "java", "AppTest.java");
            default:
                return fromClazzTemplate("javaapp/AppTest.java.template", "test");
        }
    }
}
