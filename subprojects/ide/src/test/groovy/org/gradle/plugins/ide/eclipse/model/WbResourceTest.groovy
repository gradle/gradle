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
package org.gradle.plugins.ide.eclipse.model

import groovy.xml.XmlParser
import spock.lang.Specification


class WbResourceTest extends Specification {
    final static String XML_TEXT = '<wb-resource deploy-path="/" source-path="src/main/webapp"/>'

    def canReadFromXml() {
        when:
        WbResource wbResource = new WbResource(new XmlParser().parseText(XML_TEXT))

        then:
        wbResource == createWbResource()
    }

    def canWriteToXml() {
        Node rootNode = new Node(null, 'root')

        when:
        createWbResource().appendNode(rootNode)

        then:
        new WbResource(rootNode.'wb-resource'[0]) == createWbResource()
    }

    def equality() {
        WbResource wbResource = createWbResource()
        wbResource.sourcePath += 'x'

        expect:
        wbResource != createWbResource()
    }

    private WbResource createWbResource() {
        return new WbResource("/", "src/main/webapp")
    }


}
