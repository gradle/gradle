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
import org.gradle.plugins.eclipse.EclipseWtpFacet

/**
 * Creates the org.eclipse.wst.common.project.facet.core.xml file for WTP projects.
 *
 * @author Hans Dockter
 */
class WtpFacet {
    List facets = []

    private Node xmlDocument

    private Action<Map<String, Node>> withXmlActions

    WtpFacet(EclipseWtpFacet eclipseFacet, Reader reader) {
        readXml(reader)

        eclipseFacet.beforeConfiguredActions.execute(this)

        this.facets.addAll(eclipseFacet.facets)
        this.facets.unique()

        this.withXmlActions = eclipseFacet.withXmlActions

        eclipseFacet.whenConfiguredActions.execute(this)
    }

    void toXml(File outputFile) {
        outputFile.withWriter { toXml(it) }
    }

    private void readXml(Reader reader) {
        if (!reader) {
            xmlDocument = new Node(null, 'faceted-project')
            xmlDocument.appendNode('fixed', [facet: 'jst.java'])
            xmlDocument.appendNode('fixed', [facet: 'jst.web'])
            return
        }

        xmlDocument = doReadXml(reader)
    }

    private Node doReadXml(Reader reader) {
        def rootNode = new XmlParser().parse(reader)
        rootNode.installed.each { facets.add(new Facet(it)) }
        rootNode
    }

    private void toXml(Writer writer) {
        removeConfigurableDataFromXml()

        facets.each { it.appendNode(xmlDocument) }
        withXmlActions.execute(['org.eclipse.wst.commons.project.facet.core': xmlDocument])

        printNode(xmlDocument, writer)
    }

    private void removeConfigurableDataFromXml() {
        xmlDocument.installed.each { xmlDocument.remove(it) }
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

        WtpFacet wtp = (WtpFacet) o;

        if (facets != wtp.facets) { return false }

        return true
    }

    int hashCode() {
        int result;

        result = facets.hashCode();
        return result;
    }

    String toString() {
        return "WtpFacet{" +
                ", facets=" + facets +
                '}';
    }
}
