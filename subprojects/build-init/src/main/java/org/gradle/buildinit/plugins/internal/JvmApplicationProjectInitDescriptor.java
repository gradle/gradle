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
import org.gradle.buildinit.plugins.internal.model.Description;
import org.gradle.buildinit.plugins.internal.modifiers.ComponentType;
import org.gradle.buildinit.plugins.internal.modifiers.Language;

import java.util.List;

public class JvmApplicationProjectInitDescriptor extends JvmProjectInitDescriptor {

    public JvmApplicationProjectInitDescriptor(Description description, TemplateLibraryVersionProvider libraryVersionProvider, DocumentationRegistry documentationRegistry) {
        super(description, libraryVersionProvider, documentationRegistry);
    }

    @Override
    public ComponentType getComponentType() {
        return ComponentType.APPLICATION;
    }

    @Override
    public void generateProjectBuildScript(String projectName, InitSettings settings, BuildScriptBuilder buildScriptBuilder) {
        super.generateProjectBuildScript(projectName, settings, buildScriptBuilder);

        if ("app".equals(projectName)) {
            buildScriptBuilder.block(null, "application", b -> {
                String mainClass = getLanguage() == Language.KOTLIN ? "AppKt" : "App";
                b.propertyAssignment("Define the main class for the application.", "mainClass", withPackage(settings, mainClass), false);
            });
        }
        applyApplicationPlugin(buildScriptBuilder);
        buildScriptBuilder.implementationDependency("This dependency is used by the application.",
                "com.google.guava:guava:" + libraryVersionProvider.getVersion("guava"));
    }

    @Override
    protected void sourceTemplates(String subproject, InitSettings settings, TemplateFactory templateFactory, List<String> templates) {
        templates.add("App");
    }

    @Override
    protected void testSourceTemplates(String subproject, InitSettings settings, TemplateFactory templateFactory, List<String> templates) {
        switch (settings.getTestFramework()) {
            case SPOCK:
                templates.add("groovy/AppTest");
                break;
            case TESTNG:
                templates.add("testng/AppTest");
                break;
            case JUNIT:
            case KOTLINTEST:
                templates.add("AppTest");
                break;
            case JUNIT_JUPITER:
                templates.add("junitjupiter/AppTest");
                break;
            case SCALATEST:
                templates.add("AppSuite");
                break;
            default:
                throw new IllegalArgumentException();
        }
    }
}
