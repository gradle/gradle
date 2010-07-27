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

package org.gradle.api.tasks.ide.eclipse;


import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.artifacts.Configuration
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.plugins.eclipse.AbstractXmlGeneratorTask
import org.gradle.plugins.eclipse.model.Classpath
import org.gradle.plugins.eclipse.model.Container
import org.gradle.plugins.eclipse.model.internal.ModelFactory

/**
 * Generates an eclipse <i>.classpath</i> file.
 *
 * @author Hans Dockter
 */
public class EclipseClasspath extends AbstractXmlGeneratorTask {
    File inputFile

    @OutputFile
    File outputFile

    /**
     * The referenced projects of this Eclipse project.
     */
    NamedDomainObjectContainer sourceSets

    /**
     * The natures to be added to this Eclipse project.
     */
    Set<Configuration> plusConfigurations = new LinkedHashSet<Configuration>();

    Set<Configuration> minusConfigurations = new LinkedHashSet<Configuration>();

    /**
     * The natures to be added to this Eclipse project.
     */
    Map variables = [:]

    /**
     * The natures to be added to this Eclipse project.
     */
    Set<Container> containers = new LinkedHashSet<Container>();

    boolean downloadSources = true

    boolean downloadJavadoc = false

    protected ModelFactory modelFactory = new ModelFactory()

    @TaskAction
    void generateXml() {
        Classpath classpath = modelFactory.createClasspath(this)
        classpath.toXml(getOutputFile())
    }

    void containers(String... containers) {
        assert containers != null
        this.containers.addAll(containers as List)
    }

    /**
     * Adds variables to be used for replacing absolute path in dependent-module elements of
     * the org.eclipse.wst.common.component file.
     *
     * @param variables A map where the keys are the variable names and the values are the variable values.
     */
    void variables(Map variables) {
        assert variables != null
        this.variables.putAll variables
    }
}
