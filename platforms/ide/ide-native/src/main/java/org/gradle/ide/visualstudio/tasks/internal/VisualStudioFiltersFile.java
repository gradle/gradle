/*
 * Copyright 2025 the original author or authors.
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
package org.gradle.ide.visualstudio.tasks.internal;

import groovy.util.Node;
import org.gradle.api.Transformer;
import org.gradle.internal.xml.XmlTransformer;
import org.gradle.plugins.ide.internal.generator.XmlPersistableConfigurationObject;

import java.io.File;
import java.util.Objects;

import static java.util.Collections.singletonMap;

public class VisualStudioFiltersFile extends XmlPersistableConfigurationObject {

    private final Transformer<String, File> fileLocationResolver;

    public VisualStudioFiltersFile(XmlTransformer xmlTransformer, Transformer<String, File> fileLocationResolver) {
        super(xmlTransformer);
        this.fileLocationResolver = fileLocationResolver;
    }

    @Override
    protected String getDefaultResourceName() {
        return "default.vcxproj.filters";
    }

    public void addSource(File sourceFile) {
        getItemGroupForLabel("Sources")
            .appendNode("ClCompile", singletonMap("Include", toPath(sourceFile)))
            .appendNode("Filter", "Source Files");
    }

    public void addHeader(File headerFile) {
        getItemGroupForLabel("Headers")
            .appendNode("ClInclude", singletonMap("Include", toPath(headerFile)))
            .appendNode("Filter", "Header Files");
    }

    private Node getItemGroupForLabel(String label) {
        return Objects.requireNonNull(
            findFirstChildWithAttributeValue(getXml(), "ItemGroup", "Label", label),
            "No 'ItemGroup' with attribute 'Label = " + label + "' found"
        );
    }

    private String toPath(File file) {
        return fileLocationResolver.transform(file);
    }
}
