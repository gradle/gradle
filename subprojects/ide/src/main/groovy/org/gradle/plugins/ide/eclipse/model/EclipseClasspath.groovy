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
import org.gradle.util.ConfigureUtil

/**
 * Enables fine-tuning classpath details (.classpath file) of the Eclipse plugin
 * <p>
 * Example of use with a blend of all possible properties.
 * Bear in mind that usually you don't have configure eclipse classpath directly because Gradle configures it for free!
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
 *
 *   //if you want parts of paths in resulting file to be replaced by variables (files):
 *   pathVariables 'GRADLE_HOME': file('/best/software/gradle'), 'TOMCAT_HOME': file('../tomcat')
 *
 *   classpath {
 *     //you can tweak the classpath of the eclipse project by adding extra configurations:
 *     plusConfigurations += configurations.provided
 *
 *     //you can also remove configurations from the classpath:
 *     minusConfigurations += configurations.someBoringConfig
 *
 *     //if you want to append extra containers:
 *     containers 'someFriendlyContainer', 'andYetAnotherContainer'
 *
 *     //customizing the classes output directory:
 *     defaultOutputDir = file('build-eclipse')
 *
 *     //default settings for dependencies sources/javadoc download:
 *     downloadSources = true
 *     downloadJavadoc = false
 *   }
 * }
 * </pre>
 *
 * For tackling edge cases users can perform advanced configuration on resulting xml file.
 * It is also possible to affect the way eclipse plugin merges the existing configuration
 * via beforeMerged and whenMerged closures.
 * <p>
 * beforeMerged and whenMerged closures receive {@link Classpath} object
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
 *       //if you want to mess with the resulting xml in whatever way you fancy
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
 *
 * @author Szczepan Faber, created at: 4/16/11
 */
class EclipseClasspath {

    /**
     * The source sets to be added to the classpath.
     * <p>
     * For example see docs for {@link EclipseClasspath}
     */
    Iterable<SourceSet> sourceSets

    /**
     * The configurations which files are to be transformed into classpath entries.
     * <p>
     * For example see docs for {@link EclipseClasspath}
     */
    Collection<Configuration> plusConfigurations = []

    /**
     * The configurations which files are to be excluded from the classpath entries.
     * <p>
     * For example see docs for {@link EclipseClasspath}
     */
    Collection<Configuration> minusConfigurations = []

   /**
     * Containers to be added to the classpath
     * <p>
     * For example see docs for {@link EclipseClasspath}
     */
    Set<String> containers = new LinkedHashSet<String>()

   /**
     * Adds containers to the .classpath.
     * <p>
     * For example see docs for {@link EclipseClasspath}
     *
     * @param containers the container names to be added to the .classpath.
     */
    void containers(String... containers) {
        assert containers != null
        this.containers.addAll(containers as List)
    }

    /**
     * The default output directory where eclipse puts compiled classes
     * <p>
     * For example see docs for {@link EclipseClasspath}
     */
    File defaultOutputDir

    /**
     * Whether to download and add sources associated with the dependency jars. Defaults to true.
     * <p>
     * For example see docs for {@link EclipseClasspath}
     */
    boolean downloadSources = true

    /**
     * Whether to download and add javadocs associated with the dependency jars. Defaults to false.
     * <p>
     * For example see docs for {@link EclipseClasspath}
     */
    boolean downloadJavadoc = false

    /**
     * Enables advanced configuration like tinkering with the output xml
     * or affecting the way existing .classpath content is merged with gradle build information
     * <p>
     * The object passed to whenMerged{} and beforeMerged{} closures is of type {@link Classpath}
     * <p>
     *
     * For example see docs for {@link EclipseProject}
     */

    void file(Closure closure) {
        ConfigureUtil.configure(closure, file)
    }
    /**
     * See {@link #file(Closure) }
     */

    XmlFileContentMerger file

    /******/

    org.gradle.api.Project project
    Map<String, File> pathVariables = [:]
    boolean projectDependenciesOnly = false

    //only folder paths internal to the project (e.g. beneath the project folder) are supported
    List<String> classFolders

    /**
     * Calculates, resolves & returns dependency entries of this classpath
     */
    public List<ClasspathEntry> resolveDependencies() {
        return new ClasspathFactory().createEntries(this)
    }

    void mergeXmlClasspath(Classpath xmlClasspath) {
        file.beforeMerged.execute(xmlClasspath)
        def entries = resolveDependencies()
        xmlClasspath.configure(entries)
        file.whenMerged.execute(xmlClasspath)
    }
}
