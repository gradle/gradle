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

import org.apache.commons.lang.StringUtils;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.internal.file.FileResolver;

import static org.gradle.buildinit.plugins.internal.BuildInitTestFramework.SPOCK;
import static org.gradle.buildinit.plugins.internal.BuildInitTestFramework.TESTNG;

public abstract class JavaProjectInitDescriptor extends LanguageLibraryProjectInitDescriptor {
    private final static Description DESCRIPTION = new Description(
        "Java",
        "Java Quickstart",
        "tutorial_java_projects",
        "java"
    );
    private final DocumentationRegistry documentationRegistry;

    public JavaProjectInitDescriptor(TemplateOperationFactory templateOperationFactory,
                                     FileResolver fileResolver,
                                     TemplateLibraryVersionProvider libraryVersionProvider,
                                     ProjectInitDescriptor globalSettingsDescriptor,
                                     DocumentationRegistry documentationRegistry) {
        super("java", templateOperationFactory, fileResolver, libraryVersionProvider, globalSettingsDescriptor);
        this.documentationRegistry = documentationRegistry;
    }

    @Override
    public void generate(BuildInitTestFramework testFramework) {
        globalSettingsDescriptor.generate(testFramework);
        Description desc = getDescription();
        BuildScriptBuilder buildScriptBuilder = new BuildScriptBuilder(fileResolver.resolve("build.gradle"))
            .fileComment("This generated file contains a sample " + desc.projectType + " project to get you started.")
            .fileComment("For more details take a look at the " + desc.chapterName + " chapter in the Gradle")
            .fileComment("user guide available at " + documentationRegistry.getDocumentationFor(desc.userguideId))
            .plugin("Apply the " + desc.pluginName +" plugin to add support for " + desc.projectType, desc.pluginName);
        configureBuildScript(buildScriptBuilder);
        addTestFramework(testFramework, buildScriptBuilder);
        buildScriptBuilder.create().generate();

        TemplateOperation javaSourceTemplate = sourceTemplateOperation();
        whenNoSourcesAvailable(javaSourceTemplate, testTemplateOperation(testFramework)).generate();
    }

    protected Description getDescription() {
        return DESCRIPTION;
    }

    protected String getImplementationConfigurationName() {
        return "compile";
    }

    protected String getTestImplementationConfigurationName() {
        return "test" + StringUtils.capitalize(getImplementationConfigurationName());
    }

    private void addTestFramework(BuildInitTestFramework testFramework, BuildScriptBuilder buildScriptBuilder) {
        switch (testFramework) {
            case SPOCK:
                buildScriptBuilder
                    .plugin("Apply the groovy plugin to also add support for Groovy (needed for Spock)", "groovy")
                    .dependency(getTestImplementationConfigurationName(), "Use the latest Groovy version for Spock testing",
                        "org.codehaus.groovy:groovy-all:" + libraryVersionProvider.getVersion("groovy"))
                    .dependency(getTestImplementationConfigurationName(), "Use the awesome Spock testing and specification framework even with Java",
                        "org.spockframework:spock-core:" + libraryVersionProvider.getVersion("spock"),
                        "junit:junit:" + libraryVersionProvider.getVersion("junit"));
                break;
            case TESTNG:
                buildScriptBuilder
                    .dependency(getTestImplementationConfigurationName(), "Use TestNG framework, also requires calling test.useTestNG() below",
                        "org.testng:testng:" + libraryVersionProvider.getVersion("testng"))
                    .configuration("Use TestNG for unit tests", "test.useTestNG()");
                break;
            default:
                buildScriptBuilder
                    .dependency(getTestImplementationConfigurationName(), "Use JUnit test framework", "junit:junit:" + libraryVersionProvider.getVersion("junit"));
                break;
        }
    }

    protected void configureBuildScript(BuildScriptBuilder buildScriptBuilder) {
        // todo: once we use "implementation" for Java projects too, we need to change the comment
        buildScriptBuilder.dependency(getImplementationConfigurationName(),
            "This dependency is found on compile classpath of this component and consumers.",
            "com.google.guava:guava:" + libraryVersionProvider.getVersion("guava"));
    }

    protected abstract TemplateOperation sourceTemplateOperation();

    protected abstract TemplateOperation testTemplateOperation(BuildInitTestFramework testFramework);

    @Override
    public boolean supports(BuildInitTestFramework testFramework) {
        return testFramework == SPOCK || testFramework == TESTNG;
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
