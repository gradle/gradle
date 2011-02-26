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

import org.gradle.api.artifacts.Configuration
import org.gradle.api.tasks.XmlGeneratorTask
import org.gradle.plugins.eclipse.model.*
import org.gradle.plugins.eclipse.model.internal.WtpComponentFactory

/**
 * Generates the org.eclipse.wst.common.component settings file for Eclipse WTP.
 *
 * @author Hans Dockter
 */
class EclipseWtpComponent extends XmlGeneratorTask<WtpComponent> {
    /**
     * The source directories to be transformed into wb-resource elements.
     */
    Set<File> sourceDirs

    /**
     * The configurations whose files are to be transformed into dependent-module elements.
     */
    Set<Configuration> plusConfigurations

    /**
     * The configurations whose files are to be excluded from dependent-module elements.
     */
    Set<Configuration> minusConfigurations

    /**
     * The deploy name to be used.
     */
    String deployName

    /**
     * The variables to be used for replacing absolute path in dependent-module elements.
     */
    Map<String, File> variables = [:]

    /**
     * Additional wb-resource elements.
     */
    List<WbResource> resources = []

    /**
     * Additional property elements.
     */
    List<WbProperty> properties = []

    /**
     * The context path for the web application
     */
    String contextPath

    private final WtpComponentFactory modelFactory = new WtpComponentFactory()

    EclipseWtpComponent() {
        xmlTransformer.indentation = "\t"
    }

    @Override protected WtpComponent create() {
        new WtpComponent(xmlTransformer)
    }

    @Override protected void configure(WtpComponent component) {
        modelFactory.configure(this, component)
    }

    /**
     * Adds variables to be used for replacing absolute path in dependent-module elements.
     *
     * @param variables A map where the keys are the variable names and the values are the variable values.
     */
    void variables(Map<String, File> variables) {
        assert variables != null
        this.variables.putAll variables
    }

    /**
     * Adds a property.
     *
     * @param args A map that must contain a name and value key with corresponding values.
     */
    void property(Map<String, String> args) {
        properties.add(new WbProperty(args.name, args.value))
    }

    /**
     * Adds a wb-resource.
     *
     * @param args A map that must contain a deployPath and sourcePath key with corresponding values.
     */
    void resource(Map<String, String> args) {
        resources.add(new WbResource(args.deployPath, args.sourcePath))
    }
}
