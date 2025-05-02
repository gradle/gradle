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


class WbDependentModuleTest extends Specification {
    final static String XML_TEXT = '''
                <dependent-module archiveName="gradle-1.0.0.jar" deploy-path="/WEB-INF/lib" handle="module:/classpath/gradle-1.0.0.jar">
                    <dependency-type>uses</dependency-type>
                </dependent-module>'''

    def canReadFromXml() {
        when:
        WbDependentModule wbDependentModule = new WbDependentModule(new XmlParser().parseText(XML_TEXT))

        then:
        wbDependentModule == createWbDependentModule()
    }

    def canWriteToXml() {
        Node rootNode = new Node(null, 'root')

        when:
        createWbDependentModule().appendNode(rootNode)

        then:
        new WbDependentModule(rootNode.'dependent-module'[0]) == createWbDependentModule()
    }

    def equality() {
        WbDependentModule wbDependentModule = createWbDependentModule()
        wbDependentModule.handle += 'x'

        expect:
        wbDependentModule != createWbDependentModule()
    }

    private WbDependentModule createWbDependentModule() {
        return new WbDependentModule("gradle-1.0.0.jar", "/WEB-INF/lib", "module:/classpath/gradle-1.0.0.jar")
    }


}
