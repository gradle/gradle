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
package org.gradle.ide.visualstudio.tasks.internal

import org.gradle.api.Transformer
import org.gradle.internal.xml.XmlTransformer
import org.gradle.plugins.ide.internal.generator.XmlPersistableConfigurationObject

class VisualStudioFiltersFile extends XmlPersistableConfigurationObject {
    private final Transformer<String, File> fileLocationResolver

    VisualStudioFiltersFile(XmlTransformer xmlTransformer, Transformer<String, File> fileLocationResolver) {
        super(xmlTransformer)
        this.fileLocationResolver = fileLocationResolver
    }

    protected String getDefaultResourceName() {
        'default.vcxproj.filters'
    }

    def addSource(File sourceFile) {
        sources.appendNode("ClCompile", [Include: toPath(sourceFile)]).appendNode('Filter', 'Source Files')
    }

    def addHeader(File headerFile) {
        headers.appendNode("ClInclude", [Include: toPath(headerFile)]).appendNode('Filter', 'Header Files')
    }

    def getFilters() {
        return xml.ItemGroup.findAll({ it.'@Label' == 'Filters' })[0]
    }

    private Node getSources() {
        return xml.ItemGroup.find({ it.'@Label' == 'Sources' }) as Node
    }

    private Node getHeaders() {
        return xml.ItemGroup.find({ it.'@Label' == 'Headers' }) as Node
    }

    private String toPath(File it) {
        fileLocationResolver.transform(it)
    }

}