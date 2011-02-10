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

import org.gradle.api.internal.ConventionTask
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.listener.ActionBroadcast
import org.gradle.util.ConfigureUtil
import org.gradle.plugins.eclipse.model.*
import org.gradle.plugins.eclipse.model.internal.WtpFacetFactory

/**
 * Generates the org.eclipse.wst.common.project.facet.core settings file for Eclipse WTP.
 *
 * @author Hans Dockter
 */
class EclipseWtpFacet extends ConventionTask {
    /**
     * Any existing file that is to be merged with the generated file.
     */
    @Optional
    File inputFile

    /**
     * The file to be generated.
     */
    @OutputFile
    File outputFile

    /**
     * The facets to be added as installed elements.
     */
    List<Facet> facets = []

    protected WtpFacetFactory modelFactory = new WtpFacetFactory()

    ActionBroadcast<Map<String, Node>> withXmlActions = new ActionBroadcast<Map<String, Node>>()
    ActionBroadcast<WtpFacet> beforeConfiguredActions = new ActionBroadcast<WtpFacet>()
    ActionBroadcast<WtpFacet> whenConfiguredActions = new ActionBroadcast<WtpFacet>()

    EclipseWtpFacet() {
        outputs.upToDateWhen { false }
    }

    @TaskAction
    protected void generateXml() {
        WtpFacet facets = modelFactory.createWtpFacet(this)
        facets.toXml(outputFile)
    }

    /**
     * Adds a facet.
     *
     * @param args A map that must contain a name and version key with corresponding values.
     */
    void facet(Map<String, ?> args) {
        facets << ConfigureUtil.configureByMap(args, new Facet())
    }

    /**
     * Adds a closure to be called when the XML content for each file has been generated, but before the content is
     * written to the file.
     */
    void withXml(Closure closure) {
        withXmlActions.add(closure)
    }

    /**
     * Adds a closure to be called when the model has been loaded from the input file, and before this task has
     * configured the model.
     */
    void beforeConfigured(Closure closure) {
        beforeConfiguredActions.add(closure)
    }

    /**
     * Adds a closure to be called after this task has configured model, and before it generates the XML content for the
     * files.
     */
    void whenConfigured(Closure closure) {
        whenConfiguredActions.add(closure)
    }
}
