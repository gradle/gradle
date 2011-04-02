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

package org.gradle.plugins.ide.idea.model

import org.gradle.api.artifacts.Configuration
import org.gradle.api.internal.XmlTransformer
import org.gradle.plugins.ide.idea.model.internal.IdeaDependenciesProvider

/**
 * Model for idea module.
 * <p>
 * Example of use with a blend of various properties.
 * Bear in mind that usually you don't have configure idea module directly because Gradle configures it for free!
 *
 * <pre autoTested=''>
 * apply plugin: 'java'
 * apply plugin: 'idea'
 *
 * //for the sake of the example lets have a 'provided' dependency configuration
 * configurations {
 *   provided
 *   provided.extendsFrom(compile)
 * }
 *
 * dependencies {
 *   //provided "some.interesting:dependency:1.0"
 * }
 *
 * idea {
 *   module {
 *     //if for some reason you want to add an extra sourceDirs
 *     sourceDirs += file('some-extra-source-folder')
 *
 *     //and some extra test source dirs
 *     testSourceDirs += file('some-extra-test-dir')
 *
 *     //and some extra dirs that should be excluded by IDEA
 *     excludeDirs += file('some-extra-exclude-dir')
 *
 *     //if you don't like the name Gradle have chosen
 *     name = 'some-better-name'
 *
 *     //if you prefer different output folders
 *     inheritOutputDirs = false
 *     outputDir = file('muchBetterOutputDir')
 *     testOutputDir = file('muchBetterTestOutputDir')
 *
 *     //if you prefer different java version than inherited from IDEA project
 *     javaVersion = '1.6'
 *
 *     //if you need to put provided dependencies on the classpath
 *     scopes.COMPILE.plus += configurations.provided
 *
 *     //if you like to keep *.iml in a secret folder
 *     generateTo = file('secret-modules-folder')
 *
 *     //if 'content root' (as IDEA calls it) of the module is different
 *     moduleDir = file('my-module-content-root')
 *
 *     //if you love browsing javadocs
 *     downloadJavadoc = true
 *
 *     //and hate reading sources :)
 *     downloadSources = false
 *
 *     //if you want parts of paths in resulting *.iml to be replaced by variables (files)
 *     variables = [GRADLE_HOME: file('~/cool-software/gradle')]
 *
 *     //if you want to mess with the resulting xml in whatever way you fancy
 *     withXml {
 *       def node = it.asNode()
 *       node.appendNode('iLoveGradle', 'true')
 *     }
 *   }
 * }
 *
 * </pre>
 *
 * Author: Szczepan Faber, created at: 3/31/11
 */
class IdeaModule {

    org.gradle.api.Project project
    XmlTransformer xmlTransformer
    Module xmlModule
    PathFactory pathFactory

   /**
     * Idea module name; controls the name of the *.iml file
     */
    String name

    /**
     * The directories containing the production sources.
     */
    Set<File> sourceDirs

    /**
     * The keys of this map are the Intellij scopes. Each key points to another map that has two keys, plus and minus.
     * The values of those keys are sets of  {@link org.gradle.api.artifacts.Configuration}  objects. The files of the
     * plus configurations are added minus the files from the minus configurations. See example below...
     * <p>
     * Example how to use scopes property to enable 'provided' dependencies in the output *.iml file:
     * <pre autoTested=''>
     * apply plugin: 'java'
     * apply plugin: 'idea'
     *
     * configurations {
     *   provided
     *   provided.extendsFrom(compile)
     * }
     *
     * dependencies {
     *   //provided "some.interesting:dependency:1.0"
     * }
     *
     * idea {
     *   module {
     *     scopes.COMPILE.plus += configurations.provided
     *   }
     * }
     * </pre>
     */
    Map<String, Map<String, Configuration>> scopes = [:]

    /**
     * Whether to download and add sources associated with the dependency jars.
     */
    boolean downloadSources = true

    /**
     * Whether to download and add javadoc associated with the dependency jars.
     */
    boolean downloadJavadoc = false

    /**
     * Folder where the *.iml file will be generated to
     */
    File generateTo

    /**
     * The content root directory of the module.
     */
    File moduleDir

    /**
     * The directories containing the test sources.
     */
    Set<File> testSourceDirs

    /**
     * The directories to be excluded.
     */
    Set<File> excludeDirs

    /**
     * If true, output directories for this module will be located below the output directory for the project;
     * otherwise, they will be set to the directories specified by {@link #outputDir} and {@link #testOutputDir}.
     */
    Boolean inheritOutputDirs

    /**
     * The output directory for production classes. If {@code null}, no entry will be created.
     */
    File outputDir

    /**
     * The output directory for test classes. If {@code null}, no entry will be created.
     */
    File testOutputDir

    /**
     * The variables to be used for replacing absolute paths in the iml entries. For example, you might add a
     * {@code GRADLE_USER_HOME} variable to point to the Gradle user home dir.
     */
    Map<String, File> variables = [:]

    /**
     * The JDK to use for this module. If {@code null}, the value of the existing or default ipr XML (inherited)
     * is used. If it is set to <code>inherited</code>, the project SDK is used. Otherwise the SDK for the corresponding
     * value of java version is used for this module
     */
    String javaVersion = Module.INHERITED

    /**
     * Adds a closure to be called when the XML document has been created. The XML is passed to the closure as a
     * parameter in form of a {@link org.gradle.api.artifacts.maven.XmlProvider}. The closure can modify the XML before
     * it is written to the output file.
     *
     * @param closure The closure to execute when the XML has been created.
     */
    public void withXml(Closure closure) {
        xmlTransformer.addAction(closure);
    }

    //TODO SF: most likely what's above should be a part of an interface and what's below should not be exposed. For now, below methods are protected

    protected File getOutputFile() {
        new File((File) getGenerateTo(), getName() + ".iml")
    }

    protected void setOutputFile(File newOutputFile) {
        name = newOutputFile.name.replaceFirst(/\.iml$/,"");
        generateTo = newOutputFile.parentFile
    }

    protected Set<Path> getSourcePaths(PathFactory pathFactory) {
        getSourceDirs().findAll { it.exists() }.collect { pathFactory.path(it) }
    }

    protected Set getDependencies(PathFactory pathFactory) {
        new IdeaDependenciesProvider().provide(this, pathFactory);
    }

    protected Set<Path> getTestSourcePaths(PathFactory pathFactory) {
        getTestSourceDirs().findAll { it.exists() }.collect { pathFactory.path(it) }
    }

    protected Set<Path> getExcludePaths(PathFactory pathFactory) {
        getExcludeDirs().collect { pathFactory.path(it) }
    }

    protected Path getOutputPath(PathFactory pathFactory) {
        getOutputDir() ? pathFactory.path(getOutputDir()) : null
    }

    protected Path getTestOutputPath(PathFactory pathFactory) {
        getTestOutputDir() ? pathFactory.path(getTestOutputDir()) : null
    }

    protected void applyXmlModule(Module xmlModule) {
        xmlModule.pathFactory = getPathFactory()
        //TODO SF: refactor
        xmlModule.configure(this)
        this.xmlModule = xmlModule
    }

    protected void generate() {
        xmlModule.store(getOutputFile())
    }
}
