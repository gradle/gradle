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
import org.gradle.api.internal.ClassGenerator
import org.gradle.plugins.ide.api.XmlGeneratorTask
import org.gradle.plugins.ide.eclipse.model.*
import org.gradle.plugins.ide.internal.XmlFileContentMerger

/**
 * Generates the org.eclipse.wst.common.component settings file for Eclipse WTP.
 *
 * @author Hans Dockter
 */
class GenerateEclipseWtpComponent extends XmlGeneratorTask<WtpComponent> {

    EclipseWtpComponent component

    GenerateEclipseWtpComponent() {
        xmlTransformer.indentation = "\t"
        component = services.get(ClassGenerator).newInstance(EclipseWtpComponent, [project: project, file: new XmlFileContentMerger(xmlTransformer)])
    }

    @Override protected WtpComponent create() {
        new WtpComponent(xmlTransformer)
    }

    @Override protected void configure(WtpComponent xmlComponent) {
        component.mergeXmlComponent(xmlComponent)
    }

    /**
     * The source directories to be transformed into wb-resource elements.
     */
    Set<File> getSourceDirs() {
        component.sourceDirs
    }

    void setSourceDirs(Set<File> sourceDirs) {
        component.sourceDirs = sourceDirs
    }

    /**
     * The configurations whose files are to be transformed into dependent-module elements.
     */
    Set<Configuration> getPlusConfigurations() {
        component.plusConfigurations
    }

    void setPlusConfigurations(Set<Configuration> plusConfigurations) {
        component.plusConfigurations = plusConfigurations
    }

    /**
     * The configurations whose files are to be excluded from dependent-module elements.
     */
    Set<Configuration> getMinusConfigurations() {
        component.minusConfigurations
    }

    void setMinusConfigurations(Set<Configuration> minusConfigurations) {
        component.minusConfigurations = minusConfigurations
    }

    /**
     * The deploy name to be used.
     */
    String getDeployName() {
        component.deployName
    }

    void setDeployName(String deployName) {
        component.deployName = deployName
    }

    /**
     * The variables to be used for replacing absolute path in dependent-module elements.
     */
    Map<String, File> getVariables() {
        component.pathVariables
    }

    void setVariables(Map<String, File> variables) {
        component.pathVariables = variables
    }

    /**
     * Additional wb-resource elements.
     */
    List<WbResource> getResources() {
        component.resources
    }

    void setResources(List<WbResource> resources) {
        component.resources = resources
    }

    /**
     * Additional property elements.
     */
    List<WbProperty> getProperties() {
        component.properties
    }

    void setProperties(List<WbProperty> properties) {
        component.properties = properties
    }

    /**
     * The context path for the web application
     */
    String getContextPath() {
        component.contextPath
    }

    void setContextPath(String contextPath) {
        component.contextPath = contextPath
    }

    /**
     * Adds variables to be used for replacing absolute path in dependent-module elements.
     *
     * @param variables A map where the keys are the variable names and the values are the variable values.
     */
    void variables(Map<String, File> variables) {
        assert variables != null
        component.pathVariables.putAll variables
    }

    /**
     * Adds a property.
     *
     * @param args A map that must contain a name and value key with corresponding values.
     */
    void property(Map<String, String> args) {
        component.property(args)
    }

    /**
     * Adds a wb-resource.
     *
     * @param args A map that must contain a deployPath and sourcePath key with corresponding values.
     */
    void resource(Map<String, String> args) {
        component.resource(args)
    }
}
