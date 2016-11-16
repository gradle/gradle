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

import org.gradle.api.internal.file.FileResolver;

import java.util.Collections;

import static org.gradle.buildinit.plugins.internal.BuildInitTestFramework.SPOCK;
import static org.gradle.buildinit.plugins.internal.BuildInitTestFramework.TESTNG;

public abstract class JavaProjectInitDescriptor extends LanguageLibraryProjectInitDescriptor {
    public JavaProjectInitDescriptor(TemplateOperationFactory templateOperationFactory,
                                     FileResolver fileResolver,
                                     TemplateLibraryVersionProvider libraryVersionProvider,
                                     ProjectInitDescriptor globalSettingsDescriptor) {
        super("java", templateOperationFactory, fileResolver, libraryVersionProvider, globalSettingsDescriptor);
    }

    @Override
    public void generate(BuildInitTestFramework testFramework) {
        globalSettingsDescriptor.generate(testFramework);
        templateOperationFactory.newTemplateOperation()
            .withTemplate(gradleBuildTemplate(testFramework))
            .withTarget("build.gradle")
            .withDocumentationBindings(Collections.singletonMap("ref_userguide_java_tutorial", "tutorial_java_projects"))
            .withBindings(Collections.singletonMap("junitVersion", libraryVersionProvider.getVersion("junit")))
            .withBindings(Collections.singletonMap("slf4jVersion", libraryVersionProvider.getVersion("slf4j")))
            .withBindings(Collections.singletonMap("groovyVersion", libraryVersionProvider.getVersion("groovy")))
            .withBindings(Collections.singletonMap("spockVersion", libraryVersionProvider.getVersion("spock")))
            .withBindings(Collections.singletonMap("testngVersion", libraryVersionProvider.getVersion("testng")))
            .create().generate();
        TemplateOperation javalibraryTemplateOperation = sourceTemplateOperation();
        whenNoSourcesAvailable(javalibraryTemplateOperation, testTemplateOperation(testFramework)).generate();
    }

    protected abstract TemplateOperation sourceTemplateOperation();

    protected abstract String gradleBuildTemplate(BuildInitTestFramework testFramework);

    protected abstract TemplateOperation testTemplateOperation(BuildInitTestFramework testFramework);

    @Override
    public boolean supports(BuildInitTestFramework testFramework) {
        return testFramework == SPOCK || testFramework == TESTNG;
    }
}
