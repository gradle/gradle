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

import org.gradle.api.internal.xml.XmlTransformer
import org.gradle.plugins.ide.internal.generator.XmlPersistableConfigurationObject

/**
 * Creates the .settings/org.eclipse.wst.common.component file for WTP projects.
 */
class WtpComponent extends XmlPersistableConfigurationObject {
    String deployName

    String contextPath

    List wbModuleEntries = [] // TODO: change to Set? introduce common base class?

    WtpComponent(XmlTransformer xmlTransformer) {
        super(xmlTransformer)
    }

    @Override protected void load(Node xml) {
        deployName = xml.'wb-module'[0].@'deploy-name'

        xml.'wb-module'[0].children().each { node ->
            switch (node.name()) {
                case 'property':
                    if (node.@name == 'context-root') {
                        contextPath = node.@value
                    } else {
                        wbModuleEntries << new WbProperty(node)
                    }
                    break
                case 'wb-resource':
                    wbModuleEntries << new WbResource(node)
                    break
                case 'dependent-module':
                    wbModuleEntries << new WbDependentModule(node)
                    break
            }
        }
    }

    @Override protected void store(Node xml) {
        removeConfigurableDataFromXml()

        xml.'wb-module'[0].@'deploy-name' = deployName
        if (contextPath) {
            new WbProperty('context-root', contextPath).appendNode(xml.'wb-module')
        }
        wbModuleEntries.each { it.appendNode(xml.'wb-module') }
    }

    @Override protected String getDefaultResourceName() {
        "defaultWtpComponent.xml"
    }

    void configure(String deployName, String contextPath, List newEntries) {
        def entriesToBeKept = this.wbModuleEntries.findAll { !(it instanceof WbDependentModule) }
        this.wbModuleEntries = (entriesToBeKept + newEntries).unique()
        if (deployName) {
            this.deployName = deployName
        }
        if (contextPath) {
            this.contextPath = contextPath
        }
    }

    private void removeConfigurableDataFromXml() {
        ['property', 'wb-resource', 'dependent-module'].each { elementName ->
            xml.'wb-module'."$elementName".each { elementNode ->
                xml.'wb-module'[0].remove(elementNode)
            }
        }
    }

    boolean equals(o) {
        if (this.is(o)) { return true }

        if (getClass() != o.class) { return false }

        WtpComponent wtp = (WtpComponent) o;

        if (deployName != wtp.deployName) { return false }
        if (contextPath != wtp.contextPath) { return false }
        if (wbModuleEntries != wtp.wbModuleEntries) { return false }

        return true
    }

    int hashCode() {
        int result

        result = wbModuleEntries.hashCode()
        result = 31 * result + deployName.hashCode()
        return result
    }

    String toString() {
        return "WtpComponent{" +
                "wbModuleEntries=" + wbModuleEntries +
                ", deployName='" + deployName + '\'' +
                ", contextPath='" + contextPath + '\'' +
                '}'
    }
}
