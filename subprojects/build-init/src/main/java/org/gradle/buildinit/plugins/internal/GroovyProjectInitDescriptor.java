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
import org.gradle.buildinit.plugins.internal.modifiers.BuildInitTestFramework;
import org.gradle.buildinit.plugins.internal.modifiers.Language;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;

import static org.gradle.buildinit.plugins.internal.modifiers.BuildInitTestFramework.SPOCK;

public abstract class GroovyProjectInitDescriptor extends JvmProjectInitDescriptor {
    private final TemplateLibraryVersionProvider libraryVersionProvider;
    private final DocumentationRegistry documentationRegistry;

    public GroovyProjectInitDescriptor(TemplateLibraryVersionProvider libraryVersionProvider, DocumentationRegistry documentationRegistry) {
        this.libraryVersionProvider = libraryVersionProvider;
        this.documentationRegistry = documentationRegistry;
    }

    @Override
    public Language getLanguage() {
        return Language.GROOVY;
    }

    @Override
    public void generate(InitSettings settings, BuildScriptBuilder buildScriptBuilder, TemplateFactory templateFactory) {
        super.generate(settings, buildScriptBuilder, templateFactory);

        buildScriptBuilder
            .fileComment("This generated file contains a sample Groovy project to get you started.")
            .fileComment("For more details take a look at the Groovy Quickstart chapter in the Gradle")
            .fileComment("User Manual available at " + documentationRegistry.getDocumentationFor("tutorial_groovy_projects"))
            .plugin("Apply the groovy plugin to add support for Groovy", "groovy")
            .implementationDependency("Use the latest Groovy version for building this library",
                "org.codehaus.groovy:groovy-all:" + libraryVersionProvider.getVersion("groovy"))
            .testImplementationDependency("Use the awesome Spock testing and specification framework",
                "org.spockframework:spock-core:" + libraryVersionProvider.getVersion("spock"));
        configureBuildScript(settings, buildScriptBuilder);

        TemplateOperation sourceTemplate = sourceTemplateOperation(templateFactory);
        TemplateOperation testSourceTemplate = testTemplateOperation(templateFactory);
        templateFactory.whenNoSourcesAvailable(sourceTemplate, testSourceTemplate).generate();
    }

    @Override
    public Optional<String> getFurtherReading() {
        return Optional.of(documentationRegistry.getDocumentationFor("tutorial_groovy_projects"));
    }

    @Override
    public BuildInitTestFramework getDefaultTestFramework() {
        return SPOCK;
    }

    @Override
    public Set<BuildInitTestFramework> getTestFrameworks() {
        return Collections.singleton(SPOCK);
    }

    protected abstract TemplateOperation sourceTemplateOperation(TemplateFactory templateFactory);

    protected abstract TemplateOperation testTemplateOperation(TemplateFactory templateFactory);

    protected abstract void configureBuildScript(InitSettings settings, BuildScriptBuilder buildScriptBuilder);
}
