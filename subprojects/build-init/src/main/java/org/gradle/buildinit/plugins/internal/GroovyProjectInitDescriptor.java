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
import org.gradle.api.internal.file.FileResolver;
import org.gradle.buildinit.plugins.internal.modifiers.BuildInitTestFramework;

import java.util.Collections;
import java.util.Set;

import static org.gradle.buildinit.plugins.internal.modifiers.BuildInitTestFramework.SPOCK;

public abstract class GroovyProjectInitDescriptor extends LanguageLibraryProjectInitDescriptor {
    private final DocumentationRegistry documentationRegistry;

    public GroovyProjectInitDescriptor(BuildScriptBuilderFactory scriptBuilderFactory, TemplateOperationFactory templateOperationFactory, FileResolver fileResolver,
                                       TemplateLibraryVersionProvider libraryVersionProvider, DocumentationRegistry documentationRegistry) {
        super("groovy", scriptBuilderFactory, templateOperationFactory, fileResolver, libraryVersionProvider);
        this.documentationRegistry = documentationRegistry;
    }

    @Override
    protected void generate(InitSettings settings, BuildScriptBuilder buildScriptBuilder) {
        buildScriptBuilder
            .fileComment("This generated file contains a sample Groovy project to get you started.")
            .fileComment("For more details take a look at the Groovy Quickstart chapter in the Gradle")
            .fileComment("user guide available at " + documentationRegistry.getDocumentationFor("tutorial_groovy_projects"))
            .plugin("Apply the groovy plugin to add support for Groovy", "groovy")
            .implementationDependency("Use the latest Groovy version for building this library",
                "org.codehaus.groovy:groovy-all:" + libraryVersionProvider.getVersion("groovy"))
            .testImplementationDependency("Use the awesome Spock testing and specification framework",
                "org.spockframework:spock-core:" + libraryVersionProvider.getVersion("spock"));
        configureBuildScript(settings, buildScriptBuilder);

        TemplateOperation sourceTemplate = sourceTemplateOperation(settings);
        TemplateOperation testSourceTemplate = testTemplateOperation(settings);
        whenNoSourcesAvailable(sourceTemplate, testSourceTemplate).generate();
    }

    @Override
    public BuildInitTestFramework getDefaultTestFramework() {
        return SPOCK;
    }

    @Override
    public Set<BuildInitTestFramework> getTestFrameworks() {
        return Collections.singleton(SPOCK);
    }

    protected abstract TemplateOperation sourceTemplateOperation(InitSettings settings);

    protected abstract TemplateOperation testTemplateOperation(InitSettings settings);

    protected void configureBuildScript(InitSettings settings, BuildScriptBuilder buildScriptBuilder) {
    }
}
