/*
 * Copyright 2018 the original author or authors.
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

import com.google.common.collect.Sets;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.buildinit.plugins.internal.modifiers.BuildInitTestFramework;

import java.util.Set;

public abstract class CppProjectInitDescriptor extends LanguageLibraryProjectInitDescriptor {
    public CppProjectInitDescriptor(BuildScriptBuilderFactory scriptBuilderFactory, TemplateOperationFactory templateOperationFactory, FileResolver fileResolver, TemplateLibraryVersionProvider libraryVersionProvider) {
        super("cpp", scriptBuilderFactory, templateOperationFactory, fileResolver, libraryVersionProvider);
    }

    @Override
    protected void generate(InitSettings settings, BuildScriptBuilder buildScriptBuilder) {
        buildScriptBuilder
            .fileComment("This generated file contains a sample CPP project to get you started.");
        configureBuildScript(settings, buildScriptBuilder);

        TemplateOperation sourceTemplate = sourceTemplateOperation(settings);
        TemplateOperation headerTemplate = headerTemplateOperation(settings);
        TemplateOperation testSourceTemplate = testTemplateOperation(settings);
        whenNoSourcesAvailable(sourceTemplate, headerTemplate, testSourceTemplate).generate();
    }

    @Override
    public Set<BuildInitTestFramework> getTestFrameworks() {
        return Sets.newHashSet();
    }

    @Override
    public BuildInitTestFramework getDefaultTestFramework() {
        return null;
    }

    protected abstract TemplateOperation sourceTemplateOperation(InitSettings settings);

    protected abstract TemplateOperation headerTemplateOperation(InitSettings settings);

    protected abstract TemplateOperation testTemplateOperation(InitSettings settings);

    protected void configureBuildScript(InitSettings settings, BuildScriptBuilder buildScriptBuilder) {
    }

    TemplateOperation fromCppTemplate(String template, InitSettings settings, String sourceSetName, String sourceDir) {
        String targetFileName = template.substring(template.lastIndexOf("/") + 1).replace(".template", "");
        String namespacePrefix = "";
        NamespaceDeclaration namespace = NamespaceDeclaration.empty();
        if (settings != null && !settings.getPackageName().isEmpty()) {
            namespace = NamespaceDeclaration.from(settings.getPackageName());
            namespacePrefix = settings.getPackageName() + "::";
        }
        return templateOperationFactory.newTemplateOperation()
            .withTemplate(template)
            .withTarget("src/" + sourceSetName + "/" + sourceDir + "/" + targetFileName)
            .withBinding("namespaceOpen", namespace.opening)
            .withBinding("namespaceClose", namespace.closing)
            .withBinding("namespaceIndent", namespace.indent)
            .withBinding("namespacePrefix", namespacePrefix)
            .create();
    }

    static class NamespaceDeclaration {
        static String tab = "    ";
        final String opening;
        final String closing;
        final String indent;

        public NamespaceDeclaration(String opening, String closing, String indent) {
            this.opening = opening;
            this.closing = closing;
            this.indent = indent;
        }

        static NamespaceDeclaration from(String namespace) {
            String opening = "";
            String closing = "";
            String indent = "";

            String[] components = namespace.split("::");
            for (String component : components) {
                opening += "\n" + indent + "namespace " + component + " {";
                indent += tab;
            }
            for (int i=components.length; i>0; i--) {
                closing += "\n" + indent.substring(0, (i-1)* tab.length()) + "}";
            }

            return new NamespaceDeclaration(opening, closing, indent);
        }

        static NamespaceDeclaration empty() {
            return new NamespaceDeclaration("", "", "");
        }
    }
}
