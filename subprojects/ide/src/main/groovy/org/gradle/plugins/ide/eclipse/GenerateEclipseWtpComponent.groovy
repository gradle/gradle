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

import org.gradle.api.artifacts.Configuration
import org.gradle.plugins.ide.api.XmlGeneratorTask
import org.gradle.plugins.ide.eclipse.model.EclipseWtp
import org.gradle.plugins.ide.eclipse.model.WbProperty
import org.gradle.plugins.ide.eclipse.model.WbResource
import org.gradle.plugins.ide.eclipse.model.WtpComponent
import org.gradle.plugins.ide.eclipse.model.internal.WtpComponentFactory

/**
 * Generates the org.eclipse.wst.common.component settings file for Eclipse WTP.
 *
 * @author Hans Dockter
 */
class GenerateEclipseWtpComponent extends XmlGeneratorTask<WtpComponent> {

    EclipseWtp wtp

    /**
     * The source directories to be transformed into wb-resource elements.
     */
    Set<File> getSourceDirs() {
        wtp.sourceDirs
    }

    void setSourceDirs(Set<File> sourceDirs) {
        wtp.sourceDirs = sourceDirs
    }

    /**
     * The configurations whose files are to be transformed into dependent-module elements.
     */
    Set<Configuration> getPlusConfigurations() {
        //TODO SF: check if we need to care about it for tooling api minimal model
        wtp.plusConfigurations
    }

    void setPlusConfigurations(Set<Configuration> plusConfigurations) {
        wtp.plusConfigurations = plusConfigurations
    }

    /**
     * The configurations whose files are to be excluded from dependent-module elements.
     */
    Set<Configuration> getMinusConfigurations() {
        wtp.minusConfigurations
    }

    void setMinusConfigurations(Set<Configuration> minusConfigurations) {
        wtp.minusConfigurations = minusConfigurations
    }

    /**
     * The deploy name to be used.
     */
    String getDeployName() {
        wtp.deployName
    }

    void setDeployName(String deployName) {
        wtp.deployName = deployName
    }

    /**
     * The variables to be used for replacing absolute path in dependent-module elements.
     */
    Map<String, File> getVariables() {
        wtp.pathVariables
    }

    void setVariables(Map<String, File> variables) {
        wtp.pathVariables = variables
    }

    /**
     * Additional wb-resource elements.
     */
    List<WbResource> getResources() {
        wtp.resources
    }

    void setResources(List<WbResource> resources) {
        wtp.resources = resources
    }

    /**
     * Additional property elements.
     */
    List<WbProperty> getProperties() {
        wtp.properties
    }

    void setProperties(List<WbProperty> properties) {
        wtp.properties = properties
    }

    /**
     * The context path for the web application
     */
    String contextPath

    private final WtpComponentFactory modelFactory = new WtpComponentFactory()

    GenerateEclipseWtpComponent() {
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
        wtp.pathVariables.putAll variables
    }

    /**
     * Adds a property.
     *
     * @param args A map that must contain a name and value key with corresponding values.
     */
    void property(Map<String, String> args) {
        wtp.property(args)
    }

    /**
     * Adds a wb-resource.
     *
     * @param args A map that must contain a deployPath and sourcePath key with corresponding values.
     */
    void resource(Map<String, String> args) {
        wtp.resource(args)
    }
}
