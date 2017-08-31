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
import org.gradle.internal.Factory;

public abstract class LanguageLibraryProjectInitDescriptor implements ProjectInitDescriptor {

    protected final String language;
    protected final FileResolver fileResolver;
    protected final TemplateOperationFactory templateOperationFactory;
    protected final TemplateLibraryVersionProvider libraryVersionProvider;
    protected final ProjectInitDescriptor globalSettingsDescriptor;

    public LanguageLibraryProjectInitDescriptor(String language, TemplateOperationFactory templateOperationFactory, FileResolver fileResolver,
                                                TemplateLibraryVersionProvider libraryVersionProvider, ProjectInitDescriptor globalSettingsDescriptor){
        this.language = language;
        this.fileResolver = fileResolver;
        this.templateOperationFactory = templateOperationFactory;
        this.libraryVersionProvider = libraryVersionProvider;
        this.globalSettingsDescriptor = globalSettingsDescriptor;
    }

    protected TemplateOperation whenNoSourcesAvailable(TemplateOperation... operations) {
        return new ConditionalTemplateOperation(new Factory<Boolean>() {
            public Boolean create() {
                return fileResolver.resolveFilesAsTree("src/main/" + language).isEmpty() || fileResolver.resolveFilesAsTree("src/test/" + language).isEmpty();
            }
        }, operations);
    }

    protected TemplateOperation fromClazzTemplate(String clazzTemplate, String sourceSetName) {
        return fromClazzTemplate(clazzTemplate, sourceSetName, this.language);
    }

    protected TemplateOperation fromClazzTemplate(String clazzTemplate, String sourceSetName, String language) {
        String targetFileName = clazzTemplate.substring(clazzTemplate.lastIndexOf("/") + 1).replace(".template", "");
        return fromClazzTemplate(clazzTemplate, sourceSetName, language, targetFileName);
    }

    protected TemplateOperation fromClazzTemplate(String clazzTemplate, String sourceSetName, String language, String targetFileName) {
        return templateOperationFactory.newTemplateOperation()
                .withTemplate(clazzTemplate)
                .withTarget("src/" + sourceSetName + "/" + language + "/" + targetFileName)
                .create();
    }
}
