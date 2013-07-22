/*
 * Copyright 2011 the original author or authors.
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
import org.gradle.internal.reflect.Instantiator
import org.gradle.plugins.ide.api.XmlFileContentMerger
import org.gradle.plugins.ide.api.XmlGeneratorTask
import org.gradle.plugins.ide.eclipse.model.EclipseWtpComponent
import org.gradle.plugins.ide.eclipse.model.WbProperty
import org.gradle.plugins.ide.eclipse.model.WbResource
import org.gradle.plugins.ide.eclipse.model.WtpComponent
import org.gradle.util.DeprecationLogger

/**
 * Generates the org.eclipse.wst.common.component settings file for Eclipse WTP.
 * If you want to fine tune the eclipse configuration
 * <p>
 * At this moment nearly all configuration is done via {@link EclipseWtpComponent}.
 */
class GenerateEclipseWtpComponent extends XmlGeneratorTask<WtpComponent> {

    EclipseWtpComponent component

    GenerateEclipseWtpComponent() {
        xmlTransformer.indentation = "\t"
        component = services.get(Instantiator).newInstance(EclipseWtpComponent, project, new XmlFileContentMerger(xmlTransformer))
    }

    @Override protected WtpComponent create() {
        new WtpComponent(xmlTransformer)
    }

    @Override protected void configure(WtpComponent xmlComponent) {
        component.mergeXmlComponent(xmlComponent)
    }

    /**
     * Deprecated. Please use #eclipse.wtp.component.sourceDirs. See examples in {@link EclipseWtpComponent}.
     * <p>
     * The source directories to be transformed into wb-resource elements.
     */
    Set<File> getSourceDirs() {
        DeprecationLogger.nagUserOfReplacedMethod("eclipseWtpComponent.sourceDirs", "eclipse.wtp.component.sourceDirs")
        component.sourceDirs
    }

    void setSourceDirs(Set<File> sourceDirs) {
        DeprecationLogger.nagUserOfReplacedMethod("eclipseWtpComponent.sourceDirs", "eclipse.wtp.component.sourceDirs")
        component.sourceDirs = sourceDirs
    }

    /**
     * Deprecated. Please use #eclipse.wtp.component.plusConfigurations. See examples in {@link EclipseWtpComponent}.
     */
    Set<Configuration> getPlusConfigurations() {
        DeprecationLogger.nagUserOfReplacedMethod("eclipseWtpComponent.plusConfigurations", "eclipse.wtp.component.plusConfigurations")
        component.plusConfigurations
    }

    void setPlusConfigurations(Set<Configuration> plusConfigurations) {
        DeprecationLogger.nagUserOfReplacedMethod("eclipseWtpComponent.plusConfigurations", "eclipse.wtp.component.plusConfigurations")
        component.plusConfigurations = plusConfigurations
    }

    /**
     * Deprecated. Please use #eclipse.wtp.component.minusConfigurations. See examples in {@link EclipseWtpComponent}.
     * <p>
     * The configurations whose files are to be excluded from dependent-module elements.
     */
    Set<Configuration> getMinusConfigurations() {
        DeprecationLogger.nagUserOfReplacedMethod("eclipseWtpComponent.minusConfigurations", "eclipse.wtp.component.minusConfigurations")
        component.minusConfigurations
    }

    void setMinusConfigurations(Set<Configuration> minusConfigurations) {
        DeprecationLogger.nagUserOfReplacedMethod("eclipseWtpComponent.minusConfigurations", "eclipse.wtp.component.minusConfigurations")
        component.minusConfigurations = minusConfigurations
    }

    /**
     * Deprecated. Please use #eclipse.wtp.component.deployName. See examples in {@link EclipseWtpComponent}.
     * <p>
     * The deploy name to be used.
     */
    String getDeployName() {
        DeprecationLogger.nagUserOfReplacedMethod("eclipseWtpComponent.deployName", "eclipse.wtp.component.deployName")
        component.deployName
    }

    void setDeployName(String deployName) {
        DeprecationLogger.nagUserOfReplacedMethod("eclipseWtpComponent.deployName", "eclipse.wtp.component.deployName")
        component.deployName = deployName
    }

    /**
     * Deprecated. Please use #eclipse.pathVariables. See examples in {@link EclipseWtpComponent}.
     * <p>
     * The variables to be used for replacing absolute path in dependent-module elements.
     */
    Map<String, File> getVariables() {
        DeprecationLogger.nagUserOfReplacedMethod("eclipseWtpComponent.variables", "eclipse.pathVariables")
        component.pathVariables
    }

    void setVariables(Map<String, File> variables) {
        DeprecationLogger.nagUserOfReplacedMethod("eclipseWtpComponent.variables", "eclipse.pathVariables")
        component.pathVariables = variables
    }

    /**
     * Deprecated. Please use #eclipse.wtp.component.resources. See examples in {@link EclipseWtpComponent}.
     * <p>
     * Additional wb-resource elements.
     */
    List<WbResource> getResources() {
        DeprecationLogger.nagUserOfReplacedMethod("eclipseWtpComponent.resources", "eclipse.wtp.component.resources")
        component.resources
    }

    void setResources(List<WbResource> resources) {
        DeprecationLogger.nagUserOfReplacedMethod("eclipseWtpComponent.resources", "eclipse.wtp.component.resources")
        component.resources = resources
    }

    /**
     * Deprecated. Please use #eclipse.wtp.component.properties. See examples in {@link EclipseWtpComponent}.
     * <p>
     * Additional property elements.
     */
    List<WbProperty> getProperties() {
        DeprecationLogger.nagUserOfReplacedMethod("eclipseWtpComponent.properties", "eclipse.wtp.component.properties")
        component.properties
    }

    void setProperties(List<WbProperty> properties) {
        DeprecationLogger.nagUserOfReplacedMethod("eclipseWtpComponent.properties", "eclipse.wtp.component.properties")
        component.properties = properties
    }

    /**
     * Deprecated. Please use #eclipse.wtp.component.contextPath. See examples in {@link EclipseWtpComponent}.
     * <p>
     * The context path for the web application
     */
    String getContextPath() {
        DeprecationLogger.nagUserOfReplacedMethod("eclipseWtpComponent.contextPath", "eclipse.wtp.component.contextPath")
        component.contextPath
    }

    void setContextPath(String contextPath) {
        DeprecationLogger.nagUserOfReplacedMethod("eclipseWtpComponent.contextPath", "eclipse.wtp.component.contextPath")
        component.contextPath = contextPath
    }

    /**
     * Deprecated. Please use #eclipse.pathVariables. See examples in {@link EclipseWtpComponent}.
     * <p>
     * Adds variables to be used for replacing absolute path in dependent-module elements.
     *
     * @param variables A map where the keys are the variable names and the values are the variable values.
     */
    void variables(Map<String, File> variables) {
        DeprecationLogger.nagUserOfReplacedMethod("eclipseWtpComponent.variables", "eclipse.pathVariables")
        assert variables != null
        component.pathVariables.putAll variables
    }

    /**
     * Deprecated. Please use #eclipse.wtp.component.property. See examples in {@link EclipseWtpComponent}.
     * <p>
     * Adds a property.
     *
     * @param args A map that must contain a name and value key with corresponding values.
     */
    void property(Map<String, String> args) {
        DeprecationLogger.nagUserOfReplacedMethod("eclipseWtpComponent.property", "eclipse.wtp.component.property")
        component.property(args)
    }

    /**
     * Deprecated. Please use #eclipse.wtp.component.resource. See examples in {@link EclipseWtpComponent}.
     * <p>
     * Adds a wb-resource.
     *
     * @param args A map that must contain a deployPath and sourcePath key with corresponding values.
     */
    void resource(Map<String, String> args) {
        DeprecationLogger.nagUserOfReplacedMethod("eclipseWtpComponent.resource", "eclipse.wtp.component.resource")
        component.resource(args)
    }
}
