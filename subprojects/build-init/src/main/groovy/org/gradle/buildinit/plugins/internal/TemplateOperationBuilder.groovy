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

package org.gradle.buildinit.plugins.internal

import org.gradle.api.internal.DocumentationRegistry
import org.gradle.api.internal.file.FileResolver
import org.gradle.util.GradleVersion

import java.text.DateFormat


class TemplateOperationBuilder {

    private final String templatepackage
    private final FileResolver fileResolver
    private final DocumentationRegistry documentationRegistry
    private final Map defaultBindings

    private URL templateUrl = null;
    private File targetFile = null;
    private Map bindings = null;

    TemplateOperationBuilder(String templatepackage, FileResolver fileResolver, DocumentationRegistry documentationRegistry) {
        this.documentationRegistry = documentationRegistry
        this.fileResolver = fileResolver
        this.templatepackage = templatepackage
        String now = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date())
        this.defaultBindings = [genDate: now, genUser: System.getProperty("user.name"), genGradleVersion: GradleVersion.current().toString()]
    }

    TemplateOperationBuilder newTemplateOperation() {
        templateUrl = null
        targetFile = null
        bindings = [:]
        this
    }

    TemplateOperationBuilder withTemplate(String relativeTemplatePath) {
        this.templateUrl = getClass().getResource("${templatepackage}/${relativeTemplatePath}")
        this
    }

    //mainly used for testing
    TemplateOperationBuilder withTemplate(URL templateURL) {
        this.templateUrl = templateURL
        this
    }

    TemplateOperationBuilder withTarget(String targetFilePath) {
        this.targetFile = fileResolver.resolve(targetFilePath)
        this
    }

    TemplateOperationBuilder withDocumentationBindings(Map<String, String> documentationBindings) {
        documentationBindings.each { bindingKey, documentationRef ->
            bindings.put(bindingKey, documentationRegistry.getDocumentationFor(documentationRef));
        }
        this
    }

    TemplateOperationBuilder withBindings(Map<String, String> bindings) {
        this.bindings.putAll(bindings)
        this
    }

    TemplateOperation create() {
        assert templateUrl != null
        assert targetFile != null
        Map effectiveBindings = bindings + defaultBindings
        Map wrappedBindings = effectiveBindings.collectEntries { key, value -> [key, new TemplateValue(value.toString())] }
        return new SimpleTemplateOperation(templateUrl, targetFile, wrappedBindings)
    }
}