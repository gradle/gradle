/*
 * Copyright 2007-2008 the original author or authors.
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
package org.gradle.plugins.ide.eclipse

import org.gradle.api.internal.ClassGenerator
import org.gradle.plugins.ide.api.XmlGeneratorTask
import org.gradle.plugins.ide.eclipse.model.EclipseWtpFacet
import org.gradle.plugins.ide.eclipse.model.Facet
import org.gradle.plugins.ide.eclipse.model.WtpFacet
import org.gradle.plugins.ide.internal.XmlFileContentMerger

/**
 * Generates the org.eclipse.wst.common.project.facet.core settings file for Eclipse WTP.
 * If you want to fine tune the eclipse configuration
 * please refer to more interesting examples in {@link EclipseWtpFacet}.
 * <p>
 * Example:
 * <pre autoTested=''>
 * apply plugin: 'java'
 * apply plugin: 'eclipse'
 * apply plugin: 'war'
 *
 * eclipseWtpFacet {
 *   doLast {
 *     //...
 *   }
 * }
 * </pre>
 *
 * @author Hans Dockter
 */
class GenerateEclipseWtpFacet extends XmlGeneratorTask<WtpFacet> {

    EclipseWtpFacet facet

    GenerateEclipseWtpFacet() {
        xmlTransformer.indentation = "\t"
        facet = services.get(ClassGenerator).newInstance(EclipseWtpFacet, [file: new XmlFileContentMerger(xmlTransformer)])
    }

    @Override protected WtpFacet create() {
        new WtpFacet(xmlTransformer)
    }

    @Override protected void configure(WtpFacet xmlFacet) {
        facet.mergeXmlFacet(xmlFacet)
    }

    /**
     * The facets to be added as elements.
     */
    List<Facet> getFacets() {
        facet.facets
    }

    void setFacets(List<Facet> facets) {
        facet.facets = facets
    }

    /**
     * Adds a facet.
     *
     * @param args A map that must contain a 'name' and 'version' key with corresponding values.
     */
    void facet(Map<String, ?> args) {
        facet.facet(args)
    }
}
