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

import org.gradle.internal.xml.XmlTransformer
import org.gradle.plugins.ide.internal.generator.XmlPersistableConfigurationObject

/**
 * Creates the .settings/org.eclipse.wst.common.project.facet.core.xml file for WTP projects.
 */
class WtpFacet extends XmlPersistableConfigurationObject {
    List facets = [] // TODO: turn into Set?

    WtpFacet(XmlTransformer xmlTransformer) {
        super(xmlTransformer)
    }

    @Override protected void load(Node xml) {
        xml.fixed.each { facets.add(new Facet(it)) }
        xml.installed.each { facets.add(new Facet(it)) }
    }

    @Override protected void store(Node xml) {
        removeConfigurableDataFromXml()

        facets.each { it.appendNode(xml) }
    }

    @Override protected String getDefaultResourceName() {
        "defaultWtpFacet.xml"
    }

    void configure(List<Facet> facets) {
        this.facets.addAll(facets)
        this.facets.unique()
    }

    private void removeConfigurableDataFromXml() {
        xml.fixed.each { xml.remove(it) }
        xml.installed.each { xml.remove(it) }
    }

    boolean equals(o) {
        if (this.is(o)) { return true }

        if (getClass() != o.class) { return false }

        WtpFacet wtp = (WtpFacet) o

        if (facets != wtp.facets) { return false }

        return true
    }

    int hashCode() {
        facets.hashCode()
    }

    String toString() {
        return "WtpFacet{" +
                ", facets=" + facets +
                '}'
    }
}
