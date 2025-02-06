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
package org.gradle.ide.visualstudio.tasks.internal;

import groovy.util.Node;
import org.gradle.api.Transformer;
import org.gradle.internal.xml.XmlTransformer;
import org.gradle.plugins.ide.internal.generator.XmlPersistableConfigurationObject;

import java.io.File;
import java.util.List;

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
        getSources()
            .appendNode("ClCompile", singletonMap("Include", toPath(sourceFile)))
            .appendNode("Filter", "Source Files");
    }

    public void addHeader(File headerFile) {
        getHeaders()
            .appendNode("ClInclude", singletonMap("Include", toPath(headerFile)))
            .appendNode("Filter", "Header Files");
    }

    public Node getFilters() {
        return getItemGroups()
            .stream()
            .filter(node -> node.attribute("Label").equals("Filters"))
            .findFirst().get();
    }

    private Node getSources() {
        return getItemGroups()
            .stream()
            .filter(node -> node.attribute("Label").equals("Sources"))
            .findFirst().get();
    }

    private Node getHeaders() {
        return getItemGroups()
            .stream()
            .filter(node -> node.attribute("Label").equals("Headers"))
            .findFirst().get();
    }

    private List<Node> getItemGroups() {
        return getChildren(getXml(), "ItemGroup");
    }

    private String toPath(File file) {
        return fileLocationResolver.transform(file);
    }
}
