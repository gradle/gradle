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

public abstract class GroovyProjectInitDescriptor extends LanguageLibraryProjectInitDescriptor {

    private final DocumentationRegistry documentationRegistry;

    public GroovyProjectInitDescriptor(TemplateOperationFactory templateOperationFactory, FileResolver fileResolver,
                                       TemplateLibraryVersionProvider libraryVersionProvider, ProjectInitDescriptor globalSettingsDescriptor, DocumentationRegistry documentationRegistry) {
        super("groovy", templateOperationFactory, fileResolver, libraryVersionProvider, globalSettingsDescriptor);
        this.documentationRegistry = documentationRegistry;
    }

    @Override
    public void generate(BuildInitTestFramework testFramework) {
        globalSettingsDescriptor.generate(testFramework);

        BuildScriptBuilder buildScriptBuilder = new BuildScriptBuilder(fileResolver.resolve("build.gradle"))
            .fileComment("This generated file contains a sample Groovy project to get you started.")
            .fileComment("For more details take a look at the Groovy Quickstart chapter in the Gradle")
            .fileComment("user guide available at " + documentationRegistry.getDocumentationFor("tutorial_groovy_projects"))
            .plugin("Apply the groovy plugin to add support for Groovy", "groovy")
            .compileDependency("Use the latest Groovy version for building this library",
                "org.codehaus.groovy:groovy-all:" + libraryVersionProvider.getVersion("groovy"))
            .testCompileDependency("Use the awesome Spock testing and specification framework",
                "org.spockframework:spock-core:" + libraryVersionProvider.getVersion("spock"));
        configureBuildScript(buildScriptBuilder);
        buildScriptBuilder.create().generate();

        TemplateOperation groovySourceTemplate = sourceTemplateOperation();
        whenNoSourcesAvailable(groovySourceTemplate, testTemplateOperation(testFramework)).generate();
    }

    @Override
    public boolean supports(BuildInitTestFramework testFramework) {
        return testFramework == BuildInitTestFramework.SPOCK;
    }

    protected abstract TemplateOperation sourceTemplateOperation();

    protected abstract TemplateOperation testTemplateOperation(BuildInitTestFramework testFramework);

    protected void configureBuildScript(BuildScriptBuilder buildScriptBuilder) {}
}
