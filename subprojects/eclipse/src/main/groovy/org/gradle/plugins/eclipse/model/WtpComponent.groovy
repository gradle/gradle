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
package org.gradle.plugins.eclipse.model

import org.gradle.api.Action
import org.gradle.plugins.eclipse.EclipseWtpComponent

/**
 * Creates the org.eclipse.wst.common.component files for WTP projects.
 *
 * @author Hans Dockter
 */
class WtpComponent {
    List wbModuleEntries = []

    String deployName

    String contextPath

    private Node xmlDocument

    private Action<Map<String, Node>> withXmlActions

    WtpComponent(EclipseWtpComponent eclipseComponent, List wbModuleEntries, Reader reader) {
        readXml(reader)

        eclipseComponent.beforeConfiguredActions.execute(this)

        this.wbModuleEntries.addAll(wbModuleEntries)
        this.wbModuleEntries.unique()
        if (eclipseComponent.deployName) { // TODO: why conditional?
            this.deployName = eclipseComponent.deployName
        }
        if (eclipseComponent.contextPath) { // TODO: why conditional?
            this.contextPath = eclipseComponent.contextPath
        }
        this.withXmlActions = eclipseComponent.withXmlActions

        eclipseComponent.whenConfiguredActions.execute(this)
    }

    private readXml(Reader reader) {
        if (!reader) {
            xmlDocument = new Node(null, 'project-modules', [id: "moduleCoreId", 'project-version': "2.0"])
            xmlDocument.appendNode('wb-module')
            return
        }

        xmlDocument = doReadXml(reader)
    }

    private doReadXml(Reader reader) {
        def rootNode = new XmlParser().parse(reader)

        deployName = rootNode.'wb-module'[0].@'deploy-name'
        rootNode.'wb-module'[0].children().each { entryNode ->
            def entry = null
            switch (entryNode.name()) {
                case 'property':
                    if (entryNode.@name == 'context-root') {
                        contextPath = entryNode.@value
                    } else {
                        entry = new WbProperty(entryNode)
                    }
                    break
                case 'wb-resource': entry = new WbResource(entryNode)
                    break
                case 'dependent-module': entry = new WbDependentModule(entryNode)
                    break
            }
            if (entry) {
                wbModuleEntries.add(entry)
            }
        }
        rootNode
    }

    void toXml(File outputFile) {
        outputFile.withWriter { toXml(it) }
    }

    private toXml(Writer writer) {
        removeConfigurableDataFromXml()
        xmlDocument.'wb-module'[0].@'deploy-name' = deployName
        if (contextPath) {
            new WbProperty('context-root', contextPath).appendNode(xmlDocument.'wb-module')
        }
        wbModuleEntries.each { it.appendNode(xmlDocument.'wb-module') }

        withXmlActions.execute(['org.eclipse.wst.commons.component': xmlDocument])

        printNode(xmlDocument, writer)
    }

    private void removeConfigurableDataFromXml() {
        ['property', 'wb-resource', 'dependent-module'].each { elementName ->
            xmlDocument.'wb-module'."$elementName".each { elementNode ->
                xmlDocument.'wb-module'[0].remove(elementNode)
            }
        }
    }

    private void printNode(Node node, Writer writer) {
        def printWriter = new PrintWriter(writer)
        def nodePrinter = new XmlNodePrinter(printWriter, "\t") // TODO: doesn't use UTF-8
        nodePrinter.preserveWhitespace = true
        nodePrinter.print(node)
        printWriter.flush()
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
