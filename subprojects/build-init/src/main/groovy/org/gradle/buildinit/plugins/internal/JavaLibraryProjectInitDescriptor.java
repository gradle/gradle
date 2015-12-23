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

import org.gradle.api.internal.file.FileResolver;
import org.gradle.util.GUtil;

import static org.gradle.buildinit.plugins.internal.BuildInitModifier.SPOCK;

public class JavaLibraryProjectInitDescriptor extends LanguageLibraryProjectInitDescriptor {

    public JavaLibraryProjectInitDescriptor(TemplateOperationFactory templateOperationFactory,
                                            FileResolver fileResolver,
                                            TemplateLibraryVersionProvider libraryVersionProvider,
                                            ProjectInitDescriptor globalSettingsDescriptor) {
        super("java", templateOperationFactory, fileResolver, libraryVersionProvider, globalSettingsDescriptor);
    }

    @Override
    public void generate(BuildInitModifier modifier) {
        globalSettingsDescriptor.generate(modifier);
        templateOperationFactory.newTemplateOperation()
            .withTemplate(gradleBuildTemplate(modifier))
            .withTarget("build.gradle")
            .withDocumentationBindings(GUtil.map("ref_userguide_java_tutorial", "tutorial_java_projects"))
            .withBindings(GUtil.map("junitVersion", libraryVersionProvider.getVersion("junit")))
            .withBindings(GUtil.map("slf4jVersion", libraryVersionProvider.getVersion("slf4j")))
            .withBindings(GUtil.map("groovyVersion", libraryVersionProvider.getVersion("groovy")))
            .withBindings(GUtil.map("spockVersion", libraryVersionProvider.getVersion("spock")))
            .create().generate();
        TemplateOperation javalibraryTemplateOperation = fromClazzTemplate("javalibrary/Library.java.template", "main");
        whenNoSourcesAvailable(javalibraryTemplateOperation, testTemplateOperation(modifier)).generate();
    }

    private String gradleBuildTemplate(BuildInitModifier modifier) {
        return modifier == SPOCK ? "javalibrary/spock-build.gradle.template" : "javalibrary/build.gradle.template";
    }

    private TemplateOperation testTemplateOperation(BuildInitModifier modifier) {
        return modifier == SPOCK ? fromClazzTemplate("groovylibrary/LibraryTest.groovy.template", "test", "groovy") : fromClazzTemplate("javalibrary/LibraryTest.java.template", "test");
    }

    @Override
    public boolean supports(BuildInitModifier modifier) {
        return modifier == SPOCK;
    }
}
