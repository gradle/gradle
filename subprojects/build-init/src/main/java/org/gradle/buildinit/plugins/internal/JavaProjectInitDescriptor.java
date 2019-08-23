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
import org.gradle.buildinit.plugins.internal.modifiers.BuildInitTestFramework;
import org.gradle.buildinit.plugins.internal.modifiers.Language;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import static org.gradle.buildinit.plugins.internal.modifiers.BuildInitTestFramework.JUNIT;
import static org.gradle.buildinit.plugins.internal.modifiers.BuildInitTestFramework.JUNIT_JUPITER;
import static org.gradle.buildinit.plugins.internal.modifiers.BuildInitTestFramework.SPOCK;
import static org.gradle.buildinit.plugins.internal.modifiers.BuildInitTestFramework.TESTNG;

public abstract class JavaProjectInitDescriptor extends JvmProjectInitDescriptor {
    private final static Description DESCRIPTION = new Description(
        "Java",
        "Java Quickstart",
        "tutorial_java_projects",
        "java"
    );
    private final TemplateLibraryVersionProvider libraryVersionProvider;
    private final DocumentationRegistry documentationRegistry;

    public JavaProjectInitDescriptor(TemplateLibraryVersionProvider libraryVersionProvider, DocumentationRegistry documentationRegistry) {
        this.libraryVersionProvider = libraryVersionProvider;
        this.documentationRegistry = documentationRegistry;
    }

    @Override
    public Language getLanguage() {
        return Language.JAVA;
    }

    @Override
    public void generate(InitSettings settings, BuildScriptBuilder buildScriptBuilder, TemplateFactory templateFactory) {
        super.generate(settings, buildScriptBuilder, templateFactory);

        Description desc = getDescription();
        buildScriptBuilder
            .fileComment("This generated file contains a sample " + desc.projectType + " project to get you started.")
            .fileComment("For more details take a look at the " + desc.chapterName + " chapter in the Gradle")
            .fileComment("User Manual available at " + documentationRegistry.getDocumentationFor(desc.userguideId))
            .plugin("Apply the " + desc.pluginName + " plugin to add support for " + desc.projectType, desc.pluginName);
        configureBuildScript(settings, buildScriptBuilder);
        addTestFramework(settings.getTestFramework(), buildScriptBuilder);

        TemplateOperation sourceTemplate = sourceTemplateOperation(settings, templateFactory);
        TemplateOperation testSourceTemplate = testTemplateOperation(settings, templateFactory);
        templateFactory.whenNoSourcesAvailable(sourceTemplate, testSourceTemplate).generate();
    }

    protected Description getDescription() {
        return DESCRIPTION;
    }

    @Override
    public Optional<String> getFurtherReading() {
        return Optional.of(documentationRegistry.getDocumentationFor(getDescription().userguideId));
    }

    private void addTestFramework(BuildInitTestFramework testFramework, BuildScriptBuilder buildScriptBuilder) {
        switch (testFramework) {
            case SPOCK:
                buildScriptBuilder
                    .plugin("Apply the groovy plugin to also add support for Groovy (needed for Spock)", "groovy")
                    .testImplementationDependency("Use the latest Groovy version for Spock testing",
                        "org.codehaus.groovy:groovy-all:" + libraryVersionProvider.getVersion("groovy"))
                    .testImplementationDependency("Use the awesome Spock testing and specification framework even with Java",
                        "org.spockframework:spock-core:" + libraryVersionProvider.getVersion("spock"),
                        "junit:junit:" + libraryVersionProvider.getVersion("junit"));
                break;
            case TESTNG:
                buildScriptBuilder
                    .testImplementationDependency(
                        "Use TestNG framework",
                        "org.testng:testng:" + libraryVersionProvider.getVersion("testng"));
                break;
            case JUNIT_JUPITER:
                buildScriptBuilder
                    .testImplementationDependency(
                        "Use JUnit Jupiter API for testing.",
                        "org.junit.jupiter:junit-jupiter-api:" + libraryVersionProvider.getVersion("junit-jupiter")
                    ).testRuntimeOnlyDependency(
                    "Use JUnit Jupiter Engine for testing.",
                    "org.junit.jupiter:junit-jupiter-engine:" + libraryVersionProvider.getVersion("junit-jupiter")
                );
                break;
            default:
                buildScriptBuilder.testImplementationDependency("Use JUnit test framework", "junit:junit:" + libraryVersionProvider.getVersion("junit"));
                break;
        }
    }

    protected void configureBuildScript(InitSettings settings, BuildScriptBuilder buildScriptBuilder) {
    }

    protected abstract TemplateOperation sourceTemplateOperation(InitSettings settings, TemplateFactory templateFactory);

    protected abstract TemplateOperation testTemplateOperation(InitSettings settings, TemplateFactory templateFactory);

    @Override
    public BuildInitTestFramework getDefaultTestFramework() {
        return JUNIT;
    }

    @Override
    public Set<BuildInitTestFramework> getTestFrameworks() {
        return new TreeSet<BuildInitTestFramework>(Arrays.asList(JUNIT, JUNIT_JUPITER, TESTNG, SPOCK));
    }

    protected static class Description {
        private final String projectType;
        private final String chapterName;
        private final String userguideId;
        private final String pluginName;

        public Description(String projectType, String chapterName, String userguideId, String pluginName) {
            this.projectType = projectType;
            this.chapterName = chapterName;
            this.userguideId = userguideId;
            this.pluginName = pluginName;
        }
    }
}
