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

public class LanguageLibraryProjectInitDescriptor extends TemplateBasedProjectInitDescriptor {

    private final String language;
    private final FileResolver fileResolver;
    private final TemplateOperationFactory templateOperationFactory;

    public LanguageLibraryProjectInitDescriptor(String language, TemplateOperationFactory templateOperationFactory, FileResolver fileResolver){
        this.language = language;
        this.fileResolver = fileResolver;
        this.templateOperationFactory = templateOperationFactory;
    }

    protected TemplateOperation whenNoSourcesAvailable(TemplateOperation... operations) {
        return new ConditionalTemplateOperation(new Factory<Boolean>() {
            public Boolean create() {
                return fileResolver.resolveFilesAsTree(String.format("src/main/%s", language)).isEmpty() || fileResolver.resolveFilesAsTree(String.format("src/test/%s", language)).isEmpty();
            }
        }, operations);
    }

    protected TemplateOperation fromClazzTemplate(String clazzTemplate, String sourceSetName) {
        String targetFileName = clazzTemplate.substring(clazzTemplate.lastIndexOf("/") + 1).replace(".template", "");
        return templateOperationFactory.newTemplateOperation()
                .withTemplate(clazzTemplate)
                .withTarget(String.format("src/%s/%s/%s", sourceSetName, language, targetFileName))
                .create();
    }
}
