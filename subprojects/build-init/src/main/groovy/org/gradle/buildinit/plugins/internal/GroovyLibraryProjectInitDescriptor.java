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

public class GroovyLibraryProjectInitDescriptor extends LanguageLibraryProjectInitDescriptor{

    public GroovyLibraryProjectInitDescriptor(TemplateOperationFactory templateOperationFactory, final FileResolver fileResolver, TemplateLibraryVersionProvider libraryVersionProvider, TemplateOperation delegate) {
        super("groovy", templateOperationFactory, fileResolver);
        register(delegate);

        register(templateOperationFactory.newTemplateOperation()
                .withTemplate("groovylibrary/build.gradle.template")
                .withTarget("build.gradle")
                .withDocumentationBindings(GUtil.map("ref_userguide_groovy_tutorial", "tutorial_groovy_projects"))
                .withBindings(GUtil.map("groovyVersion", libraryVersionProvider.getVersion("groovy")))
                .withBindings(GUtil.map("junitVersion", libraryVersionProvider.getVersion("junit")))
                .create()
        );

        TemplateOperation groovyLibTemplateOperation = fromClazzTemplate("groovylibrary/Library.groovy.template", "main");
        TemplateOperation groovyTestTemplateOperation = fromClazzTemplate("groovylibrary/LibraryTest.groovy.template", "test");

        register(whenNoSourcesAvailable(groovyLibTemplateOperation, groovyTestTemplateOperation));
    }
}