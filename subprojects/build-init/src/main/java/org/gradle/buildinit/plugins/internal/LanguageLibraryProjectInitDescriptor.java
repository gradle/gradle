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
import org.gradle.buildinit.plugins.internal.modifiers.BuildInitDsl;
import org.gradle.internal.Factory;

public abstract class LanguageLibraryProjectInitDescriptor implements LanguageSpecificProjectGenerator {

    protected final String language;
    protected final FileResolver fileResolver;
    protected final TemplateOperationFactory templateOperationFactory;
    protected final TemplateLibraryVersionProvider libraryVersionProvider;
    protected final BuildScriptBuilderFactory scriptBuilderFactory;

    public LanguageLibraryProjectInitDescriptor(String language, BuildScriptBuilderFactory scriptBuilderFactory, TemplateOperationFactory templateOperationFactory, FileResolver fileResolver, TemplateLibraryVersionProvider libraryVersionProvider) {
        this.language = language;
        this.scriptBuilderFactory = scriptBuilderFactory;
        this.fileResolver = fileResolver;
        this.templateOperationFactory = templateOperationFactory;
        this.libraryVersionProvider = libraryVersionProvider;
    }

    @Override
    public boolean supportsPackage() {
        return true;
    }

    @Override
    public BuildInitDsl getDefaultDsl() {
        return BuildInitDsl.GROOVY;
    }

    @Override
    public void generate(InitSettings settings) {
        BuildScriptBuilder buildScriptBuilder = scriptBuilderFactory.script(settings.getDsl(), "build");
        buildScriptBuilder.repositories().jcenter("Use jcenter for resolving your dependencies.\nYou can declare any Maven/Ivy/file repository here.");
        generate(settings, buildScriptBuilder);
        buildScriptBuilder.create().generate();
    }

    protected abstract void generate(InitSettings settings, BuildScriptBuilder buildScriptBuilder);

    protected TemplateOperation whenNoSourcesAvailable(TemplateOperation... operations) {
        return new ConditionalTemplateOperation(new Factory<Boolean>() {
            public Boolean create() {
                return fileResolver.resolveFilesAsTree("src/main/" + language).isEmpty() || fileResolver.resolveFilesAsTree("src/test/" + language).isEmpty();
            }
        }, operations);
    }

    protected String withPackage(InitSettings settings, String className) {
        if (settings.getPackageName().isEmpty()) {
            return className;
        } else {
            return settings.getPackageName() + "." + className;
        }
    }

    protected TemplateOperation fromClazzTemplate(String clazzTemplate, String sourceSetName) {
        return fromClazzTemplate(clazzTemplate, sourceSetName, this.language);
    }

    protected TemplateOperation fromClazzTemplate(String clazzTemplate, InitSettings settings, String sourceSetName) {
        return fromClazzTemplate(clazzTemplate, settings, sourceSetName, this.language);
    }

    protected TemplateOperation fromClazzTemplate(String clazzTemplate, String sourceSetName, String language) {
        return fromClazzTemplate(clazzTemplate, null, sourceSetName, language);
    }

    protected TemplateOperation fromClazzTemplate(String clazzTemplate, InitSettings settings, String sourceSetName, String language) {
        String targetFileName = clazzTemplate.substring(clazzTemplate.lastIndexOf("/") + 1).replace(".template", "");
        String packageDecl = "";
        if (settings != null && !settings.getPackageName().isEmpty()) {
            packageDecl = "package " + settings.getPackageName();
            targetFileName = settings.getPackageName().replace(".", "/") + "/" + targetFileName;
        }
        return templateOperationFactory.newTemplateOperation()
            .withTemplate(clazzTemplate)
            .withTarget("src/" + sourceSetName + "/" + language + "/" + targetFileName)
            .withBinding("packageDecl", packageDecl)
            .create();
    }
}
