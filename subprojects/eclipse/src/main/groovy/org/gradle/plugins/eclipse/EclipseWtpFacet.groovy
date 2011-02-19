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
package org.gradle.plugins.eclipse

import org.gradle.api.tasks.XmlGeneratorTask
import org.gradle.plugins.eclipse.model.*
import org.gradle.plugins.eclipse.model.internal.WtpFacetFactory
import org.gradle.util.ConfigureUtil

/**
 * Generates the org.eclipse.wst.common.project.facet.core settings file for Eclipse WTP.
 *
 * @author Hans Dockter
 */
class EclipseWtpFacet extends XmlGeneratorTask<WtpFacet> {
    /**
     * The facets to be added as elements.
     */
    // TODO: What's the difference between fixed and installed facets? Why do we only model the latter?
    List<Facet> facets = []

    protected WtpFacetFactory modelFactory = new WtpFacetFactory()

    EclipseWtpFacet() {
        xmlTransformer.indentation = "\t"
    }

    @Override protected WtpFacet create() {
        new WtpFacet(xmlTransformer)
    }

    @Override protected void configure(WtpFacet facet) {
        modelFactory.configure(this, facet)
    }

    /**
     * Adds a facet.
     *
     * @param args A map that must contain a name and version key with corresponding values.
     */
    void facet(Map<String, ?> args) {
        facets << ConfigureUtil.configureByMap(args, new Facet())
    }
}
