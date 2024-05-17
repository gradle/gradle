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
import org.gradle.buildinit.plugins.internal.modifiers.Language;
import org.gradle.buildinit.plugins.internal.modifiers.ModularizationOption;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TemplateFactory {
    private final TemplateOperationFactory templateOperationFactory;
    private final InitSettings initSettings;
    private final Language language;

    public TemplateFactory(InitSettings initSettings, Language language, TemplateOperationFactory templateOperationFactory) {
        this.initSettings = initSettings;
        this.language = language;
        this.templateOperationFactory = templateOperationFactory;
    }

    public TemplateOperation whenNoSourcesAvailable(TemplateOperation... operations) {
        return whenNoSourcesAvailable(initSettings.getSubprojects().get(0), Arrays.asList(operations));
    }

    public TemplateOperation whenNoSourcesAvailable(String subproject, List<TemplateOperation> operations) {
        return new ConditionalTemplateOperation(() -> {
            FileTree mainFiles = initSettings.getTarget().dir(subproject + "/src/main/" + language.getName()).getAsFileTree();
            FileTree testFiles = initSettings.getTarget().dir(subproject + "/src/test/" + language.getName()).getAsFileTree();
            return mainFiles.isEmpty() || testFiles.isEmpty();
        }, operations);
    }
    public TemplateOperation fromSourceTemplate(String clazzTemplate, String sourceSetName) {
        return fromSourceTemplate(clazzTemplate, sourceSetName, initSettings.getSubprojects().get(0), language);
    }

    public TemplateOperation fromSourceTemplate(String clazzTemplate, String sourceSetName, String subprojectName) {
        return fromSourceTemplate(clazzTemplate, sourceSetName, subprojectName, language);
    }

    public TemplateOperation fromSourceTemplate(String clazzTemplate, String sourceSetName, String subprojectName, Language language) {
        return fromSourceTemplate(clazzTemplate, t -> {
            t.subproject(subprojectName);
            t.sourceSet(sourceSetName);
            t.language(language);
        });
    }

    public TemplateOperation fromSourceTemplate(String sourceTemplate, Action<? super SourceFileTemplate> config) {
        String targetFileName = sourceTemplate.substring(sourceTemplate.lastIndexOf("/") + 1).replace(".template", "");

        TemplateDetails details = new TemplateDetails(language, targetFileName);
        config.execute(details);

        String basePackageName = "";
        String packageDecl = "";
        String className = details.className == null ? "" : details.className;
        if (initSettings != null && !initSettings.getPackageName().isEmpty()) {
            basePackageName = initSettings.getPackageName();
            String packageName = basePackageName;
            if (initSettings.getModularizationOption() == ModularizationOption.WITH_LIBRARY_PROJECTS) {
                packageName = packageName + "." + details.subproject;
            }
            packageDecl = "package " + packageName;
            targetFileName = packageName.replace(".", "/") + "/" + details.getTargetFileName();
        } else {
            targetFileName = details.getTargetFileName();
        }

        TemplateOperationFactory.TemplateOperationBuilder operationBuilder = templateOperationFactory.newTemplateOperation()
            .withTemplate(sourceTemplate)
            .withTarget(initSettings.getTarget().file(details.subproject + "/src/" + details.sourceSet + "/" + details.language.getName() + "/" + targetFileName).getAsFile())
            .withBinding("basePackageName", basePackageName)
            .withBinding("packageDecl", packageDecl)
            .withBinding("className", className);
        for (Map.Entry<String, String> entry : details.bindings.entrySet()) {
            operationBuilder.withBinding(entry.getKey(), entry.getValue());
        }
        return operationBuilder.create();
    }

    private static class TemplateDetails implements SourceFileTemplate {
        final Map<String, String> bindings = new HashMap<>();
        String subproject;
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
        public void subproject(String subproject) {
            this.subproject = subproject;
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

        void subproject(String subproject);
    }
}

