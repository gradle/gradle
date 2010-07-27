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

import org.gradle.api.tasks.ide.eclipse.EclipseWtp
import org.gradle.listener.ListenerBroadcast

/**
 * @author Hans Dockter
 */
class Wtp {
    List wbModuleEntries = []

    List facets = []

    String deployName

    private Node orgEclipseWstCommonComponentXml
    private Node orgEclipseWstCommonProjectFacetCoreXml

    private ListenerBroadcast withXmlActions

    Wtp(EclipseWtp eclipseWtp, List wbModuleEntries, Reader inputOrgEclipseWstCommonComponentXml,
        Reader inputOrgEclipseWstCommonProjectFacetCoreXml) {
        initFromXml(inputOrgEclipseWstCommonComponentXml, inputOrgEclipseWstCommonProjectFacetCoreXml)

        eclipseWtp.beforeConfiguredActions.source.execute(this)

        this.wbModuleEntries.addAll(wbModuleEntries)
        this.wbModuleEntries.unique()
        this.facets.addAll(eclipseWtp.facets)
        this.facets.unique()
        if (eclipseWtp.deployName) {
            this.deployName = eclipseWtp.deployName
        }
        this.withXmlActions = eclipseWtp.withXmlActions

        eclipseWtp.whenConfiguredActions.source.execute(this)
    }

    private def initFromXml(Reader inputOrgEclipseWstCommonComponentXml, Reader inputOrgEclipseWstCommonProjectFacetCoreXml) {
        if (!inputOrgEclipseWstCommonComponentXml) {
            orgEclipseWstCommonComponentXml =
                new Node(null, 'project-modules', [id: "moduleCoreId", 'project-version': "2.0"])
            orgEclipseWstCommonComponentXml.appendNode('wb-module')
            orgEclipseWstCommonProjectFacetCoreXml = new Node(null, 'faceted-project')
            orgEclipseWstCommonProjectFacetCoreXml.appendNode('fixed', [facet: 'jst.java'])
            orgEclipseWstCommonProjectFacetCoreXml.appendNode('fixed', [facet: 'jst.web'])
            return
        }

        orgEclipseWstCommonComponentXml = readOrgEclipseWstCommonComponentXml(inputOrgEclipseWstCommonComponentXml)
        orgEclipseWstCommonProjectFacetCoreXml = readOrgEclipseWstCommonProjectFacetCoreXml(inputOrgEclipseWstCommonProjectFacetCoreXml)
    }

    private def readOrgEclipseWstCommonComponentXml(Reader inputXml) {
        def rootNode = new XmlParser().parse(inputXml)

        deployName = rootNode.'wb-module'[0].@'deploy-name' 
        rootNode.'wb-module'[0].children().each { entryNode ->
            def entry = null
            switch (entryNode.name()) {
                case 'property': entry = new WbProperty(entryNode)
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

    private def readOrgEclipseWstCommonProjectFacetCoreXml(Reader inputXml) {
        def rootNode = new XmlParser().parse(inputXml)

        rootNode.installed.each { entryNode ->
            facets.add(new Facet(entryNode))
        }
        rootNode
    }

    void toXml(File orgEclipseWstCommonComponentXmlFile, File orgEclipseWstCommonProjectFacetCoreXmlFile) {
        toXml(new FileWriter(orgEclipseWstCommonComponentXmlFile), new FileWriter(orgEclipseWstCommonProjectFacetCoreXmlFile))
    }

    def toXml(Writer orgEclipseWstCommonComponentXmlWriter, Writer orgEclipseWstCommonProjectFacetCoreXmlWriter) {
        removeConfigurableDataFromXml()
        orgEclipseWstCommonComponentXml.'wb-module'[0].@'deploy-name' = deployName
        wbModuleEntries.each { entry ->
            entry.appendNode(orgEclipseWstCommonComponentXml.'wb-module')
        }
        facets.each { facet ->
            facet.appendNode(orgEclipseWstCommonProjectFacetCoreXml)
        }
        withXmlActions.source.execute([
                'org.eclipse.wst.commons.component': orgEclipseWstCommonComponentXml,
                'org.eclipse.wst.commons.project.facet.core': orgEclipseWstCommonProjectFacetCoreXml])

        new XmlNodePrinter(new PrintWriter(orgEclipseWstCommonComponentXmlWriter)).print(orgEclipseWstCommonComponentXml)
        new XmlNodePrinter(new PrintWriter(orgEclipseWstCommonProjectFacetCoreXmlWriter)).print(orgEclipseWstCommonProjectFacetCoreXml)
    }

    private def removeConfigurableDataFromXml() {
        ['property', 'wb-resource', 'dependent-module'].each { elementName ->
            orgEclipseWstCommonComponentXml.'wb-module'."$elementName".each { elementNode ->
                orgEclipseWstCommonComponentXml.'wb-module'[0].remove(elementNode)
            }
        }
        orgEclipseWstCommonProjectFacetCoreXml.installed.each { orgEclipseWstCommonProjectFacetCoreXml.remove(it) }
    }

    boolean equals(o) {
        if (this.is(o)) { return true }

        if (getClass() != o.class) { return false }

        Wtp wtp = (Wtp) o;

        if (deployName != wtp.deployName) { return false }
        if (facets != wtp.facets) { return false }
        if (wbModuleEntries != wtp.wbModuleEntries) { return false }

        return true
    }

    int hashCode() {
        int result;

        result = wbModuleEntries.hashCode();
        result = 31 * result + facets.hashCode();
        result = 31 * result + deployName.hashCode();
        return result;
    }

    public String toString() {
        return "Wtp{" +
                "wbModuleEntries=" + wbModuleEntries +
                ", facets=" + facets +
                ", deployName='" + deployName + '\'' +
                '}';
    }
}
