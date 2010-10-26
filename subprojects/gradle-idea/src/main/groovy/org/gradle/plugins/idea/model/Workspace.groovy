/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.plugins.idea.model

import org.gradle.api.internal.XmlTransformer

/**
 * Represents the customizable elements of an ipr (via XML hooks everything of the ipr is customizable).
 *
 * @author Hans Dockter
 */

class Workspace {
    private Node xml

    private XmlTransformer withXmlActions

    def Workspace(Reader inputXml, XmlTransformer withXmlActions) {
        initFromXml(inputXml)

        this.withXmlActions = withXmlActions
    }

    private def initFromXml(Reader inputXml) {
        Reader reader = inputXml ?: new InputStreamReader(getClass().getResourceAsStream('defaultWorkspace.xml'))
        xml = new XmlParser().parse(reader)
    }

    def toXml(Writer writer) {
        withXmlActions.transform(xml, writer)
    }
}