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

import org.gradle.api.Action;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.buildinit.plugins.internal.modifiers.BuildInitDsl;
import org.gradle.internal.Factory;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

public abstract class LanguageLibraryProjectInitDescriptor implements LanguageSpecificProjectGenerator {
    protected final FileResolver fileResolver;
    protected final TemplateOperationFactory templateOperationFactory;
    protected final TemplateLibraryVersionProvider libraryVersionProvider;
    protected final BuildScriptBuilderFactory scriptBuilderFactory;

    public LanguageLibraryProjectInitDescriptor(BuildScriptBuilderFactory scriptBuilderFactory, TemplateOperationFactory templateOperationFactory, FileResolver fileResolver, TemplateLibraryVersionProvider libraryVersionProvider) {
        this.scriptBuilderFactory = scriptBuilderFactory;
        this.fileResolver = fileResolver;
        this.templateOperationFactory = templateOperationFactory;
        this.libraryVersionProvider = libraryVersionProvider;
    }

    abstract public Language getLanguage();

    @Override
    public BuildInitDsl getDefaultDsl() {
        return BuildInitDsl.GROOVY;
    }

    @Override
    public void generate(InitSettings settings) {
        BuildScriptBuilder buildScriptBuilder = scriptBuilderFactory.script(settings.getDsl(), "build");
        generate(settings, buildScriptBuilder);
        buildScriptBuilder.create().generate();
    }

    protected abstract void generate(InitSettings settings, BuildScriptBuilder buildScriptBuilder);

    protected TemplateOperation whenNoSourcesAvailable(TemplateOperation... operations) {
        return new ConditionalTemplateOperation(new Factory<Boolean>() {
            public Boolean create() {
                return fileResolver.resolveFilesAsTree("src/main/" + getLanguage().getName()).isEmpty() || fileResolver.resolveFilesAsTree("src/test/" + getLanguage().getName()).isEmpty();
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

    protected TemplateOperation fromSourceTemplate(String clazzTemplate, String sourceSetName) {
        return fromSourceTemplate(clazzTemplate, sourceSetName, getLanguage());
    }

    protected TemplateOperation fromSourceTemplate(String clazzTemplate, InitSettings settings, String sourceSetName) {
        return fromSourceTemplate(clazzTemplate, settings, sourceSetName, getLanguage());
    }

    protected TemplateOperation fromSourceTemplate(String clazzTemplate, String sourceSetName, Language language) {
        return fromSourceTemplate(clazzTemplate, null, sourceSetName, language);
    }

    protected TemplateOperation fromSourceTemplate(String clazzTemplate, InitSettings settings, String sourceSetName, Language language) {
        return fromSourceTemplate(clazzTemplate, settings, t -> {
            t.sourceSet(sourceSetName);
            t.language(language);
        });
    }

    protected TemplateOperation fromSourceTemplate(String sourceTemplate, InitSettings settings, Action<? super SourceFileTemplate> config) {
        String targetFileName = sourceTemplate.substring(sourceTemplate.lastIndexOf("/") + 1).replace(".template", "");

        TemplateDetails details = new TemplateDetails(getLanguage(), targetFileName);
        config.execute(details);

        String packageDecl = "";
        String className = details.className == null ? "" : details.className;
        if (settings != null && !settings.getPackageName().isEmpty()) {
            packageDecl = "package " + settings.getPackageName();
            targetFileName = settings.getPackageName().replace(".", "/") + "/" + details.getTargetFileName();
        } else {
            targetFileName = details.getTargetFileName();
        }

        TemplateOperationFactory.TemplateOperationBuilder operationBuilder = templateOperationFactory.newTemplateOperation()
            .withTemplate(sourceTemplate)
            .withTarget("src/" + details.sourceSet + "/" + details.language.getName() + "/" + targetFileName)
            .withBinding("packageDecl", packageDecl)
            .withBinding("className", className);
        for (Map.Entry<String, String> entry : details.bindings.entrySet()) {
            operationBuilder.withBinding(entry.getKey(), entry.getValue());
        }
        return operationBuilder.create();
    }

    private static class TemplateDetails implements SourceFileTemplate {
        final Map<String, String> bindings = new HashMap<>();
        String sourceSet = "main";
        Language language;
        String fileName;
        @Nullable
        String className;

        TemplateDetails(Language language, String fileName) {
            this.language = language;
            this.fileName = fileName;
        }

        @Override
        public void sourceSet(String name) {
            this.sourceSet = name;
        }

        @Override
        public void language(Language language) {
            this.language = language;
        }

        @Override
        public void className(String name) {
            this.className = name;
        }

        @Override
        public void binding(String name, String value) {
            this.bindings.put(name, value);
        }

        public String getTargetFileName() {
            if (className != null) {
                return className + "." + language.getExtension();
            }
            return fileName;
        }
    }

    protected interface SourceFileTemplate {
        void sourceSet(String name);

        void language(Language language);

        void className(String name);

        void binding(String name, String value);
    }
}
