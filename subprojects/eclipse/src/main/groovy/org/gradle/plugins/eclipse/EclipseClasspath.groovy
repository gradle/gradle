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
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.XmlGeneratorTask
import org.gradle.plugins.eclipse.model.Classpath
import org.gradle.plugins.eclipse.model.Container
import org.gradle.plugins.eclipse.model.internal.ClasspathFactory

/**
 * Generates an Eclipse <code>.classpath</code> file.
 *
 * @author Hans Dockter
 */
class EclipseClasspath extends XmlGeneratorTask<Classpath> {
    /**
     * The source sets to be added to the classpath.
     */
    Iterable<SourceSet> sourceSets

    /**
     * The configurations which files are to be transformed into classpath entries.
     */
    Set<Configuration> plusConfigurations = new LinkedHashSet<Configuration>()

    /**
     * The configurations which files are to be excluded from the classpath entries.
     */
    Set<Configuration> minusConfigurations = new LinkedHashSet<Configuration>()

    /**
     * The variables to be used for replacing absolute paths in classpath entries.
     */
    Map<String, File> variables = [:]

    /**
     * Containers to be added to the classpath
     */
    Set<String> containers = new LinkedHashSet<String>()

    /**
     * The default output directory for eclipse generated files, eg classes.
     */
    File defaultOutputDir

    /**
     * Whether to download and add sources associated with the dependency jars. Defaults to true.
     */
    boolean downloadSources = true

    /**
     * Whether to download and add javadocs associated with the dependency jars. Defaults to false.
     */
    boolean downloadJavadoc = false

    protected ClasspathFactory modelFactory = new ClasspathFactory()

    EclipseClasspath() {
        xmlTransformer.indentation = "\t"
    }

    @Override protected Classpath create() {
        return new Classpath(xmlTransformer)
    }

    @Override protected void configure(Classpath object) {
        modelFactory.configure(this, object)
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
    void variables(Map<String, File> variables) {
        assert variables != null
        this.variables.putAll variables
    }
}
