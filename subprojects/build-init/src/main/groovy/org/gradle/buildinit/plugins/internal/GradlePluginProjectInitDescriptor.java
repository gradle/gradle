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
import org.gradle.util.GUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.gradle.buildinit.plugins.internal.BuildInitTestFramework.SPOCK;

public class GradlePluginProjectInitDescriptor extends LanguageLibraryProjectInitDescriptor {
    private static final Logger LOGGER = LoggerFactory.getLogger(GradlePluginProjectInitDescriptor.class);

    public GradlePluginProjectInitDescriptor(TemplateOperationFactory templateOperationFactory,
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
            .withDocumentationBindings(GUtil.map("ref_userguide_testkit", "test_kit"))
            .withBindings(GUtil.map("spockVersion", libraryVersionProvider.getVersion("spock")))
            .create().generate();
        whenNoSourcesAvailable(getPluginSourceTemplate(), getPluginTaskSourceTemplate(), testTemplateOperation(testFramework)).generate();
    }

    private TemplateOperation getPluginSourceTemplate() {
        return fromClazzTemplate("gradleplugin/HelloWorldPlugin.java.template", "main");
    }

    private TemplateOperation getPluginTaskSourceTemplate() {
        return fromClazzTemplate("gradleplugin/HelloWorld.java.template", "main");
    }
    private String gradleBuildTemplate(BuildInitTestFramework testFramework) {
        return "gradleplugin/build.gradle.template";
    }

    private TemplateOperation testTemplateOperation(BuildInitTestFramework testFramework) {
        return fromClazzTemplate("gradleplugin/FunctionalTest.groovy.template", "functionalTest", "groovy");
    }

    @Override
    public boolean supports(BuildInitTestFramework testFramework) {
        return testFramework == SPOCK;
    }
}
