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

import org.gradle.api.GradleException;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.util.GUtil;
import java.util.HashMap;

import com.google.common.base.Joiner;

public class JavaLibraryProjectInitDescriptor extends LanguageLibraryProjectInitDescriptor {
    private TemplateOperationFactory templateOperationFactory;
    private FileResolver fileResolver;
    private TemplateLibraryVersionProvider libraryVersionProvider;
    private TemplateOperation delegate;

    private HashMap<String, Boolean> supportedModifiers;
    private HashMap<String, Boolean> initModifiers;

    public JavaLibraryProjectInitDescriptor(TemplateOperationFactory templateOperationFactory,
                                            FileResolver fileResolver,
                                            TemplateLibraryVersionProvider libraryVersionProvider,
                                            TemplateOperation delegate) {
        super("java", templateOperationFactory, fileResolver);

        this.templateOperationFactory = templateOperationFactory;
        this.fileResolver = fileResolver;
        this.libraryVersionProvider = libraryVersionProvider;
        this.delegate = delegate;

        supportedModifiers = new HashMap<String, Boolean>();
        supportedModifiers.put("spock", true);

        initModifiers = new HashMap<String, Boolean>();
    }

    public ProjectInitDescriptor withModifier(String modifier) {
        if (supportedModifiers.get(modifier) == null) {
            String supportedModifiersStr = Joiner.on(", ").join(supportedModifiers.keySet());

            throw new GradleException(
                "The requested init modifier '"+modifier+"' is not supported."
                + " Supported modifiers for this build setup type are: " + supportedModifiersStr
            );
        }

        initModifiers.put(modifier, true);
        return this;
    }

    public void generate() {
        register(delegate);

        TemplateOperation javalibraryTemplateOperation = fromClazzTemplate("javalibrary/Library.java.template", "main");
        TemplateOperation javalibraryTestTemplateOperation;
        String gradleBuildTemplate;

        if (initModifiers.get("spock") != null) {
            gradleBuildTemplate = "javalibrary/spock-build.gradle.template";
            javalibraryTestTemplateOperation = fromClazzTemplate("groovylibrary/LibraryTest.groovy.template", "test", "groovy");
        } else {
            gradleBuildTemplate = "javalibrary/build.gradle.template";
            javalibraryTestTemplateOperation = fromClazzTemplate("javalibrary/LibraryTest.java.template", "test");
        }

        register(templateOperationFactory.newTemplateOperation()
                .withTemplate(gradleBuildTemplate)
                .withTarget("build.gradle")
                .withDocumentationBindings(GUtil.map("ref_userguide_java_tutorial", "tutorial_java_projects"))
                .withBindings(GUtil.map("junitVersion", libraryVersionProvider.getVersion("junit")))
                .withBindings(GUtil.map("slf4jVersion", libraryVersionProvider.getVersion("slf4j")))
                .withBindings(GUtil.map("groovyVersion", libraryVersionProvider.getVersion("groovy")))
                .withBindings(GUtil.map("spockVersion", libraryVersionProvider.getVersion("spock")))
                .create()
        );

        register(whenNoSourcesAvailable(javalibraryTemplateOperation, javalibraryTestTemplateOperation));
        super.generate();
    }
}
