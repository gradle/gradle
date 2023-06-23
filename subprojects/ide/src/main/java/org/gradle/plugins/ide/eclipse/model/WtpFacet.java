/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.plugins.ide.eclipse.model;


import com.google.common.base.Objects;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import groovy.util.Node;
import groovy.util.NodeList;
import org.gradle.api.Incubating;
import org.gradle.internal.xml.XmlTransformer;
import org.gradle.plugins.ide.internal.generator.XmlPersistableConfigurationObject;

import java.util.List;

/**
 * Creates the .settings/org.eclipse.wst.common.project.facet.core.xml file for WTP projects.
 */
public class WtpFacet extends XmlPersistableConfigurationObject {
    /**
     * The id of the Eclipse J2EE Web Library container.
     *
     * @since 8.3
     */
    @Incubating
    public static final String WEB_LIBS_CONTAINER = "org.eclipse.jst.j2ee.internal.web.container";

    private List<Facet> facets = Lists.newArrayList(); // TODO: turn into Set?

    public WtpFacet(XmlTransformer xmlTransformer) {
        super(xmlTransformer);
    }

    public List<Facet> getFacets() {
        return facets;
    }

    public void setFacets(List<Facet> facets) {
        this.facets = facets;
    }

    @Override
    protected void load(Node xml) {
        NodeList fixed = (NodeList) xml.get("fixed");
        NodeList installed = (NodeList) xml.get("installed");
        for (Object n : fixed) {
            facets.add(new Facet((Node) n));
        }
        for (Object n : installed) {
            facets.add(new Facet((Node) n));
        }
    }

    @Override
    protected void store(Node xml) {
        removeConfigurableDataFromXml();
        for (Facet facet : facets) {
            facet.appendNode(xml);
        }
    }

    @Override
    protected String getDefaultResourceName() {
        return "defaultWtpFacet.xml";
    }

    public void configure(List<Facet> facets) {
        this.facets.addAll(facets);
        removeDuplicates();
    }

    private void removeDuplicates() {
        this.facets = Lists.newArrayList(Sets.newLinkedHashSet(facets));
    }

    private void removeConfigurableDataFromXml() {
        Node xml = getXml();
        NodeList fixed = (NodeList) xml.get("fixed");
        NodeList installed = (NodeList) xml.get("installed");
        for (Object n : fixed) {
            xml.remove((Node)n);
        }
        for (Object n : installed) {
            xml.remove((Node)n);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        WtpFacet wtpFacet = (WtpFacet) o;
        return Objects.equal(facets, wtpFacet.facets);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(facets);
    }

    public String toString() {
        return "WtpFacet{facets=" + facets + "}";
    }
}
