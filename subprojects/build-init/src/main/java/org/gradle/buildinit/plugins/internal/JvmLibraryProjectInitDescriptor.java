/*
 * Copyright 2013 the original author or authors.
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

import java.util.List;

public class JvmLibraryProjectInitDescriptor extends JvmProjectInitDescriptor {

    private final TemplateLibraryVersionProvider libraryVersionProvider;

    public JvmLibraryProjectInitDescriptor(Description description, TemplateLibraryVersionProvider libraryVersionProvider, DocumentationRegistry documentationRegistry) {
        super(description, libraryVersionProvider, documentationRegistry);
        this.libraryVersionProvider = libraryVersionProvider;
    }

    @Override
    public ComponentType getComponentType() {
        return ComponentType.LIBRARY;
    }

    @Override
    public void generateProjectBuildScript(String projectName, InitSettings settings, BuildScriptBuilder buildScriptBuilder) {
        super.generateProjectBuildScript(projectName, settings, buildScriptBuilder);

        applyLibraryPlugin(buildScriptBuilder);
        buildScriptBuilder.dependency(
            "api",
            "This dependency is exported to consumers, that is to say found on their compile classpath.",
            "org.apache.commons:commons-math3:" + libraryVersionProvider.getVersion("commons-math"));
        buildScriptBuilder.implementationDependency("This dependency is used internally, and not exposed to consumers on their own compile classpath.",
            "com.google.guava:guava:" + libraryVersionProvider.getVersion("guava"));
    }

    @Override
    protected void sourceTemplates(String subproject, InitSettings settings, TemplateFactory templateFactory, List<String> templates) {
        templates.add("Library");
    }

    @Override
    protected void testSourceTemplates(String subproject, InitSettings settings, TemplateFactory templateFactory, List<String> templates) {
        switch (settings.getTestFramework()) {
            case SPOCK:
                templates.add("groovy/LibraryTest");
                break;
            case TESTNG:
                templates.add("testng/LibraryTest");
                break;
            case JUNIT:
            case KOTLINTEST:
                templates.add("LibraryTest");
                break;
            case JUNIT_JUPITER:
                templates.add("junitjupiter/LibraryTest");
                break;
            case SCALATEST:
                templates.add("LibrarySuite");
                break;
            default:
                throw new IllegalArgumentException();
        }
    }
}
