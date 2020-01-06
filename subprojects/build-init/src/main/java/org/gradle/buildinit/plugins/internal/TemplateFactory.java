/*
 * Copyright 2019 the original author or authors.
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
import org.gradle.api.file.FileTree;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.buildinit.plugins.internal.modifiers.Language;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

public class TemplateFactory {
    private final FileCollectionFactory fileCollectionFactory;
    private final TemplateOperationFactory templateOperationFactory;
    private final InitSettings initSettings;
    private final Language language;

    public TemplateFactory(InitSettings initSettings, Language language, FileCollectionFactory fileCollectionFactory, TemplateOperationFactory templateOperationFactory) {
        this.initSettings = initSettings;
        this.language = language;
        this.fileCollectionFactory = fileCollectionFactory;
        this.templateOperationFactory = templateOperationFactory;
    }

    public TemplateOperation whenNoSourcesAvailable(TemplateOperation... operations) {
        return new ConditionalTemplateOperation(() -> {
            FileTree mainFiles = fileCollectionFactory.resolving("main files", "src/main/" + language.getName()).getAsFileTree();
            FileTree testFiles = fileCollectionFactory.resolving("test files", "src/test/" + language.getName()).getAsFileTree();
            return mainFiles.isEmpty() || testFiles.isEmpty();
        }, operations);
    }

    public TemplateOperation fromSourceTemplate(String clazzTemplate, String sourceSetName) {
        return fromSourceTemplate(clazzTemplate, sourceSetName, language);
    }

    public TemplateOperation fromSourceTemplate(String clazzTemplate, String sourceSetName, Language language) {
        return fromSourceTemplate(clazzTemplate, t -> {
            t.sourceSet(sourceSetName);
            t.language(language);
        });
    }

    public TemplateOperation fromSourceTemplate(String sourceTemplate, Action<? super SourceFileTemplate> config) {
        String targetFileName = sourceTemplate.substring(sourceTemplate.lastIndexOf("/") + 1).replace(".template", "");

        TemplateDetails details = new TemplateDetails(language, targetFileName);
        config.execute(details);

        String packageDecl = "";
        String className = details.className == null ? "" : details.className;
        if (initSettings != null && !initSettings.getPackageName().isEmpty()) {
            packageDecl = "package " + initSettings.getPackageName();
            targetFileName = initSettings.getPackageName().replace(".", "/") + "/" + details.getTargetFileName();
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

