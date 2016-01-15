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

import org.gradle.api.JavaVersion;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.util.GUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.gradle.buildinit.plugins.internal.BuildInitTestFramework.SPOCK;
import static org.gradle.buildinit.plugins.internal.BuildInitTestFramework.TESTNG;

public class JavaLibraryProjectInitDescriptor extends LanguageLibraryProjectInitDescriptor {

    public static final String TESTNG_JAVA6_WARNING = "Latest version of TestNG is not compatible with Java 6. Change the version of TestNG in generated build script manually or upgrade Java.";

    private static final Logger LOGGER = LoggerFactory.getLogger(JavaLibraryProjectInitDescriptor.class);

    public JavaLibraryProjectInitDescriptor(TemplateOperationFactory templateOperationFactory,
                                            FileResolver fileResolver,
                                            TemplateLibraryVersionProvider libraryVersionProvider,
                                            ProjectInitDescriptor globalSettingsDescriptor) {
        super("java", templateOperationFactory, fileResolver, libraryVersionProvider, globalSettingsDescriptor);
    }

    @Override
    public void generate(BuildInitTestFramework testFramework) {
        if (testFramework == TESTNG && JavaVersion.current().isJava6()) {
            LOGGER.warn(TESTNG_JAVA6_WARNING);
        }
        globalSettingsDescriptor.generate(testFramework);
        templateOperationFactory.newTemplateOperation()
            .withTemplate(gradleBuildTemplate(testFramework))
            .withTarget("build.gradle")
            .withDocumentationBindings(GUtil.map("ref_userguide_java_tutorial", "tutorial_java_projects"))
            .withBindings(GUtil.map("junitVersion", libraryVersionProvider.getVersion("junit")))
            .withBindings(GUtil.map("slf4jVersion", libraryVersionProvider.getVersion("slf4j")))
            .withBindings(GUtil.map("groovyVersion", libraryVersionProvider.getVersion("groovy")))
            .withBindings(GUtil.map("spockVersion", libraryVersionProvider.getVersion("spock")))
            .withBindings(GUtil.map("testngVersion", libraryVersionProvider.getVersion("testng")))
            .create().generate();
        TemplateOperation javalibraryTemplateOperation = fromClazzTemplate("javalibrary/Library.java.template", "main");
        whenNoSourcesAvailable(javalibraryTemplateOperation, testTemplateOperation(testFramework)).generate();
    }

    private String gradleBuildTemplate(BuildInitTestFramework testFramework) {
        switch (testFramework) {
            case SPOCK:
                return "javalibrary/spock-build.gradle.template";
            case TESTNG:
                return "javalibrary/testng-build.gradle.template";
            default:
                return "javalibrary/build.gradle.template";
        }
    }

    private TemplateOperation testTemplateOperation(BuildInitTestFramework testFramework) {
        switch (testFramework) {
            case SPOCK:
                return fromClazzTemplate("groovylibrary/LibraryTest.groovy.template", "test", "groovy");
            case TESTNG:
                return fromClazzTemplate("javalibrary/LibraryTestNG.java.template", "test", "java", "LibraryTest.java");
            default:
                return fromClazzTemplate("javalibrary/LibraryTest.java.template", "test");
        }
    }

    @Override
    public boolean supports(BuildInitTestFramework testFramework) {
        return testFramework == SPOCK || testFramework == TESTNG;
    }
}
