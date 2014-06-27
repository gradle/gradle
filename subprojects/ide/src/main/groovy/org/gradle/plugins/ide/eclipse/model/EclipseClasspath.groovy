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
package org.gradle.plugins.ide.eclipse.model

import org.gradle.api.artifacts.Configuration
import org.gradle.api.tasks.SourceSet
import org.gradle.plugins.ide.api.XmlFileContentMerger
import org.gradle.plugins.ide.eclipse.model.internal.ClasspathFactory
import org.gradle.plugins.ide.eclipse.model.internal.ExportedEntriesUpdater
import org.gradle.plugins.ide.eclipse.model.internal.FileReferenceFactory
import org.gradle.util.ConfigureUtil

/**
 * The build path settings for the generated Eclipse project. Used by the
 * {@link org.gradle.plugins.ide.eclipse.GenerateEclipseClasspath} task to generate an Eclipse .classpath file.
 * <p>
 * The following example demonstrates the various configuration options.
 * Keep in mind that all properties have sensible defaults; only configure them explicitly
 * if the defaults don't match your needs.
 *
 * <pre autoTested=''>
 * apply plugin: 'java'
 * apply plugin: 'eclipse'
 *
 * configurations {
 *   provided
 *   someBoringConfig
 * }
 *
 * eclipse {
 *   //if you want parts of paths in resulting file to be replaced by variables (files):
 *   pathVariables 'GRADLE_HOME': file('/best/software/gradle'), 'TOMCAT_HOME': file('../tomcat')
 *
 *   classpath {
 *     //you can tweak the classpath of the Eclipse project by adding extra configurations:
 *     plusConfigurations += [ configurations.provided ]
 *
 *     //you can also remove configurations from the classpath:
 *     minusConfigurations += [ configurations.someBoringConfig ]
 *
 *     //if you don't want some classpath entries 'exported' in Eclipse
 *     noExportConfigurations += [ configurations.provided ]
 *
 *     //if you want to append extra containers:
 *     containers 'someFriendlyContainer', 'andYetAnotherContainer'
 *
 *     //customizing the classes output directory:
 *     defaultOutputDir = file('build-eclipse')
 *
 *     //default settings for downloading sources and Javadoc:
 *     downloadSources = true
 *     downloadJavadoc = false
 *   }
 * }
 * </pre>
 *
 * For tackling edge cases, users can perform advanced configuration on the resulting XML file.
 * It is also possible to affect the way that the Eclipse plugin merges the existing configuration
 * via beforeMerged and whenMerged closures.
 * <p>
 * The beforeMerged and whenMerged closures receive a {@link Classpath} object.
 * <p>
 * Examples of advanced configuration:
 *
 * <pre autoTested=''>
 * apply plugin: 'java'
 * apply plugin: 'eclipse'
 *
 * eclipse {
 *   classpath {
 *     file {
 *       //if you want to mess with the resulting XML in whatever way you fancy
 *       withXml {
 *         def node = it.asNode()
 *         node.appendNode('xml', 'is what I love')
 *       }
 *
 *       //closure executed after .classpath content is loaded from existing file
 *       //but before gradle build information is merged
 *       beforeMerged { classpath ->
 *         //you can tinker with the {@link Classpath} here
 *       }
 *
 *       //closure executed after .classpath content is loaded from existing file
 *       //and after gradle build information is merged
 *       whenMerged { classpath ->
 *         //you can tinker with the {@link Classpath} here
 *       }
 *     }
 *   }
 * }
 * </pre>
 */
class EclipseClasspath {

    /**
     * The source sets to be added.
     * <p>
     * See {@link EclipseClasspath} for an example.
     */
    Iterable<SourceSet> sourceSets

    /**
     * The configurations whose files are to be added as classpath entries.
     * <p>
     * See {@link EclipseClasspath} for an example.
     */
    Collection<Configuration> plusConfigurations = []

    /**
     * The configurations whose files are to be excluded from the classpath entries.
     * <p>
     * See {@link EclipseClasspath} for an example.
     */
    Collection<Configuration> minusConfigurations = []

    /**
     * A subset of {@link #plusConfigurations} whose files are not to be exported to downstream Eclipse projects.
     * <p>
     * See {@link EclipseClasspath} for an example.
     */
    Collection<Configuration> noExportConfigurations = []

    /**
     * The classpath containers to be added.
     * <p>
     * See {@link EclipseClasspath} for an example.
     */
    Set<String> containers = new LinkedHashSet<String>()

    /**
     * Further classpath containers to be added.
     * <p>
     * See {@link EclipseClasspath} for an example.
     *
     * @param containers the classpath containers to be added
     */
    void containers(String... containers) {
        assert containers != null
        this.containers.addAll(containers as List)
    }

    /**
     * The default output directory where Eclipse puts compiled classes.
     * <p>
     * See {@link EclipseClasspath} for an example.
     */
    File defaultOutputDir

    /**
     * Whether to download and associate source Jars with the dependency Jars. Defaults to true.
     * <p>
     * See {@link EclipseClasspath} for an example.
     */
    boolean downloadSources = true

    /**
     * Whether to download and associate Javadoc Jars with the dependency Jars. Defaults to false.
     * <p>
     * See {@link EclipseClasspath} for an example.
     */
    boolean downloadJavadoc = false

    /**
     * Enables advanced configuration like tinkering with the output XML or affecting the way
     * that the contents of an existing .classpath file is merged with Gradle build information.
     * The object passed to the whenMerged{} and beforeMerged{} closures is of type {@link Classpath}.
     * <p>
     * See {@link EclipseProject} for an example.
     */
    void file(Closure closure) {
        ConfigureUtil.configure(closure, file)
    }

    /**
     * See {@link #file(Closure)}.
     */
    XmlFileContentMerger file

    /** ****/

    final org.gradle.api.Project project
    Map<String, File> pathVariables = [:]
    boolean projectDependenciesOnly = false

    List<File> classFolders

    EclipseClasspath(org.gradle.api.Project project) {
        this.project = project
    }

    /**
     * Calculates, resolves and returns dependency entries of this classpath.
     */
    public List<ClasspathEntry> resolveDependencies() {
        def entries = new ClasspathFactory().createEntries(this)
        new ExportedEntriesUpdater().updateExported(entries, this.noExportConfigurations*.name)
        return entries
    }

    void mergeXmlClasspath(Classpath xmlClasspath) {
        file.beforeMerged.execute(xmlClasspath)
        def entries = resolveDependencies()
        xmlClasspath.configure(entries)
        file.whenMerged.execute(xmlClasspath)
    }

    FileReferenceFactory getFileReferenceFactory() {
        def referenceFactory = new FileReferenceFactory()
        pathVariables.each { name, dir -> referenceFactory.addPathVariable(name, dir) }
        return referenceFactory
    }
}
