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

import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.artifacts.Configuration
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.plugins.eclipse.model.internal.ModelFactory
import org.gradle.plugins.eclipse.model.Container
import org.gradle.plugins.eclipse.model.Classpath
import org.gradle.api.internal.XmlTransformer
import org.gradle.api.artifacts.maven.XmlProvider
import org.gradle.api.Action

/**
 * Generates an Eclipse <i>.classpath</i> file.
 *
 * @author Hans Dockter
 */
public class EclipseClasspath extends AbstractXmlGeneratorTask {
    /**
     * The file that is merged into the to be produced classpath file. This file must not exist.
     */
    File inputFile

    @OutputFile
    /**
     * The output file where to generate the classpath to.
     */
    File outputFile

    /**
     * The source sets to be added to the classpath.
     */
    NamedDomainObjectContainer sourceSets

    /**
     * The configurations which files are to be transformed into classpath entries.
     */
    Set<Configuration> plusConfigurations = new LinkedHashSet<Configuration>();

    /**
     * The configurations which files are to be excluded from the classpath entries.
     */
    Set<Configuration> minusConfigurations = new LinkedHashSet<Configuration>();

    /**
     * The variables to be used for replacing absolute paths in classpath entries.
     */
    Map variables = [:]

    /**
     * Containers to be added to the classpath
     */
    Set<Container> containers = new LinkedHashSet<Container>();

    /**
     * Whether to download and add sources associated with the dependency jars. Defaults to true.
     */
    boolean downloadSources = true

    /**
     * Whether to download and add javadocs associated with the dependency jars. Defaults to false.
     */
    boolean downloadJavadoc = false

    protected ModelFactory modelFactory = new ModelFactory()

    protected XmlTransformer withXmlActions = new XmlTransformer();

    def EclipseClasspath() {
        outputs.upToDateWhen { false }
    }

    @TaskAction
    void generateXml() {
        Classpath classpath = modelFactory.createClasspath(this)
        classpath.toXml(getOutputFile())
    }

    /**
     * Adds containers to the .classpath.
     *
     * @param containers the container names to be added to the .classpath.
     */
    void containers(String... containers) {
        assert containers != null
        this.containers.addAll(containers as List)
    }

    /**
     * Adds variables to be used for replacing absolute paths in classpath entries.
     *
     * @param variables A map where the keys are the variable names and the values are the variable values.
     */
    void variables(Map variables) {
        assert variables != null
        this.variables.putAll variables
    }

    /**
     * Adds a closure to be called when the .classpath XML has been created. The XML is passed to the closure as a
     * parameter in form of a {@link org.gradle.api.artifacts.maven.XmlProvider}. The closure can modify the XML.
     *
     * @param closure The closure to execute when the .classpath XML has been created.
     */
    void withXml(Closure closure) {
        withXmlActions.addAction(closure);
    }

    /**
     * Adds an action to be called when the .classpath XML has been created. The XML is passed to the action as a
     * parameter in form of a {@link org.gradle.api.artifacts.maven.XmlProvider}. The action can modify the XML.
     *
     * @param action The action to execute when the .classpath XML has been created.
     */
    void withXml(Action<? super XmlProvider> action) {
        withXmlActions.addAction(action);
    }
}
