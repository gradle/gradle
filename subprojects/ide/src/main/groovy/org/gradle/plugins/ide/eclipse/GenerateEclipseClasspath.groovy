/*
 * Copyright 2010 the original author or authors.
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
import org.gradle.api.tasks.SourceSet
import org.gradle.plugins.ide.api.XmlGeneratorTask
import org.gradle.plugins.ide.eclipse.model.Classpath
import org.gradle.plugins.ide.eclipse.model.EclipseClasspath
import org.gradle.util.DeprecationLogger

/**
 * Generates an Eclipse <code>.classpath</code> file. If you want to fine tune the eclipse configuration
 * <p>
 * At this moment nearly all configuration is done via {@link EclipseClasspath}.
 */
class GenerateEclipseClasspath extends XmlGeneratorTask<Classpath> {

    EclipseClasspath classpath

    GenerateEclipseClasspath() {
        xmlTransformer.indentation = "\t"
    }

    @Override protected Classpath create() {
        return new Classpath(xmlTransformer, classpath.fileReferenceFactory)
    }

    @Override protected void configure(Classpath xmlClasspath) {
        classpath.mergeXmlClasspath(xmlClasspath)
    }

    /**
     * Deprecated. Please use #eclipse.classpath.sourceSets. See examples in {@link EclipseClasspath}.
     * <p>
     * The source sets to be added to the classpath.
     */
    Iterable<SourceSet> getSourceSets() {
        DeprecationLogger.nagUserOfReplacedMethod("eclipseClasspath.sourceSets", "eclipse.classpath.sourceSets")
        classpath.sourceSets
    }

    /**
     * Deprecated. Please use #eclipse.classpath.sourceSets. See examples in {@link EclipseClasspath}.
     * <p>
     * The source sets to be added to the classpath.
     */
    void setSourceSets(Iterable<SourceSet> sourceSets) {
        DeprecationLogger.nagUserOfReplacedMethod("eclipseClasspath.sourceSets", "eclipse.classpath.sourceSets")
        classpath.sourceSets = sourceSets
    }

    /**
     * Deprecated. Please use #eclipse.classpath.plusConfigurations. See examples in {@link EclipseClasspath}.
     * <p>
     * The configurations which files are to be transformed into classpath entries.
     */
    Collection<Configuration> getPlusConfigurations() {
        DeprecationLogger.nagUserOfReplacedMethod("eclipseClasspath.plusConfigurations", "eclipse.classpath.plusConfigurations")
        classpath.plusConfigurations
    }

    void setPlusConfigurations(Collection<Configuration> plusConfigurations) {
        DeprecationLogger.nagUserOfReplacedMethod("eclipseClasspath.plusConfigurations", "eclipse.classpath.plusConfigurations")
        classpath.plusConfigurations = plusConfigurations
    }

    /**
     * Deprecated. Please use #eclipse.classpath.minusConfigurations. See examples in {@link EclipseClasspath}.
     * <p>
     * The configurations which files are to be excluded from the classpath entries.
     */
    Collection<Configuration> getMinusConfigurations() {
        DeprecationLogger.nagUserOfReplacedMethod("eclipseClasspath.minusConfigurations", "eclipse.classpath.minusConfigurations")
        classpath.minusConfigurations
    }

    void setMinusConfigurations(Collection<Configuration> minusConfigurations) {
        DeprecationLogger.nagUserOfReplacedMethod("eclipseClasspath.minusConfigurations", "eclipse.classpath.minusConfigurations")
        classpath.minusConfigurations = minusConfigurations
    }

    /**
     * Deprecated. Please use #eclipse.pathVariables. See examples in {@link EclipseClasspath}.
     * <p>
     * Adds path variables to be used for replacing absolute paths in classpath entries.
     *
     * @param pathVariables A map with String->File pairs.
     */
    Map<String, File> getVariables() {
        DeprecationLogger.nagUserOfReplacedMethod("eclipseClasspath.variables", "eclipse.pathVariables")
        classpath.pathVariables
    }

    void setVariables(Map<String, File> variables) {
        DeprecationLogger.nagUserOfReplacedMethod("eclipseClasspath.variables", "eclipse.pathVariables")
        classpath.pathVariables = variables
    }

    /**
     * Deprecated. Please use #eclipse.classpath.containers. See examples in {@link EclipseClasspath}.
     * <p>
     * Containers to be added to the classpath
     */
    Set<String> getContainers() {
        DeprecationLogger.nagUserOfReplacedMethod("eclipseClasspath.containers", "eclipse.classpath.containers")
        classpath.containers
    }

    void setContainers(Set<String> containers) {
        DeprecationLogger.nagUserOfReplacedMethod("eclipseClasspath.containers", "eclipse.classpath.containers")
        classpath.containers = containers
    }

    /**
     * Deprecated. Please use #eclipse.classpath.defaultOutputDir. See examples in {@link EclipseClasspath}.
     * <p>
     * The default output directory for eclipse generated files, eg classes.
     */
    File getDefaultOutputDir() {
        DeprecationLogger.nagUserOfReplacedMethod("eclipseClasspath.defaultOutputDir", "eclipse.classpath.defaultOutputDir")
        classpath.defaultOutputDir
    }

    void setDefaultOutputDir(File defaultOutputDir) {
        DeprecationLogger.nagUserOfReplacedMethod("eclipseClasspath.defaultOutputDir", "eclipse.classpath.defaultOutputDir")
        classpath.defaultOutputDir = defaultOutputDir
    }

    /**
     * Deprecated. Please use #eclipse.classpath.downloadSources. See examples in {@link EclipseClasspath}.
     * <p>
     * Whether to download and add sources associated with the dependency jars. Defaults to true.
     */
    boolean getDownloadSources() {
        DeprecationLogger.nagUserOfReplacedMethod("eclipseClasspath.downloadSources", "eclipse.classpath.downloadSources")
        classpath.downloadSources
    }

    void setDownloadSources(boolean downloadSources) {
        DeprecationLogger.nagUserOfReplacedMethod("eclipseClasspath.downloadSources", "eclipse.classpath.downloadSources")
        classpath.downloadSources = downloadSources
    }

    /**
     * Deprecated. Please use #eclipse.classpath.downloadJavadoc. See examples in {@link EclipseClasspath}.
     * <p>
     * Whether to download and add javadocs associated with the dependency jars. Defaults to false.
     */
    boolean getDownloadJavadoc() {
        DeprecationLogger.nagUserOfReplacedMethod("eclipseClasspath.downloadJavadoc", "eclipse.classpath.downloadJavadoc")
        classpath.downloadJavadoc
    }

    void setDownloadJavadoc(boolean downloadJavadoc) {
        DeprecationLogger.nagUserOfReplacedMethod("eclipseClasspath.downloadJavadoc", "eclipse.classpath.downloadJavadoc")
        classpath.downloadJavadoc = downloadJavadoc
    }

    /**
     * Deprecated. Please use #eclipse.classpath.containers. See examples in {@link EclipseClasspath}.
     * <p>
     * Adds containers to the .classpath.
     *
     * @param containers the container names to be added to the .classpath.
     */
    void containers(String... containers) {
        DeprecationLogger.nagUserOfReplacedMethod("eclipseClasspath.containers", "eclipse.classpath.containers")
        classpath.containers(containers)
    }

    /**
     * Deprecated. Please use #eclipse.pathVariables. See examples in {@link EclipseClasspath}.
     * <p>
     * Adds variables to be used for replacing absolute paths in classpath entries.
     *
     * @param variables A map where the keys are the variable names and the values are the variable values.
     */
    void variables(Map<String, File> variables) {
        DeprecationLogger.nagUserOfReplacedMethod("eclipseClasspath.variables", "eclipse.pathVariables")
        assert variables != null
        classpath.pathVariables.putAll variables
    }
}
