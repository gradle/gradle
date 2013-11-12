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
package org.gradle.ide.visualstudio.model
import org.gradle.api.internal.xml.XmlTransformer
import org.gradle.plugins.ide.internal.generator.XmlPersistableConfigurationObject

class FiltersFile extends XmlPersistableConfigurationObject {

    FiltersFile() {
       super(new XmlTransformer())
   }

   protected String getDefaultResourceName() {
       'default.vcxproj.filters'
   }

    def addSource(String sourceFile) {
        sources.appendNode("ClCompile", [Include: sourceFile]).appendNode('Filter', 'Source Files')
    }

    def addHeader(String headerFile) {
        headers.appendNode("ClInclude", [Include: headerFile]).appendNode('Filter', 'Header Files')
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

}