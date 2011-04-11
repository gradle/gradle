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
package org.gradle.plugins.ide.idea

import org.gradle.api.artifacts.Configuration
import org.gradle.plugins.ide.api.XmlGeneratorTask
import org.gradle.plugins.ide.idea.model.IdeaModule
import org.gradle.plugins.ide.idea.model.Module

/**
 * Generates an IDEA module file.
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
 * ideaModule {
 *   scopes.COMPILE.plus += configurations.provided
 * }
 * </pre>
 *
 * @author Hans Dockter
 */
public class GenerateIdeaModule extends XmlGeneratorTask<Module> {

    /**
     * Idea module model
     */
    IdeaModule module

    //TODO SF: IMPORTANT
    //Once we decide to break backwards compatibility below hacky delegating getters/setters will be gone
    //and the implementation of this task will dwindle into few lines of code or disappear completely

    @Override protected Module create() {
        Module xmlModule = new Module(xmlTransformer)
        return xmlModule
    }

    @Override protected void configure(Module xmlModule) {
        getModule().mergeXmlModule(xmlModule)
    }

    /**
     * The content root directory of the module.
     */
    File getModuleDir() {
        module.contentRoot
    }

    void setModuleDir(File contentRoot) {
        module.contentRoot = contentRoot
    }

    /**
     * The directories containing the production sources.
     */
    Set<File> getSourceDirs() {
       module.sourceDirs
    }

    void setSourceDirs(Set<File> sourceDirs) {
       module.sourceDirs = sourceDirs
    }

    /**
     * The directories containing the test sources.
     */
    Set<File> getTestSourceDirs() {
        module.testSourceDirs
    }

    void setTestSourceDirs(Set<File> testSourceDirs) {
        module.testSourceDirs = testSourceDirs
    }

    /**
     * The directories to be excluded.
     */
    Set<File> getExcludeDirs() {
        module.excludeDirs
    }

    void setExcludeDirs(Set<File> excludeDirs) {
        module.excludeDirs = excludeDirs
    }

    /**
     * If true, output directories for this module will be located below the output directory for the project;
     * otherwise, they will be set to the directories specified by #outputDir and #testOutputDir.
     */
    Boolean getInheritOutputDirs() {
        module.inheritOutputDirs
    }

    void setInheritOutputDirs(Boolean inheritOutputDirs) {
        module.inheritOutputDirs
    }

    /**
     * The output directory for production classes. If {@code null}, no entry will be created.
     */
    File getOutputDir() {
        module.outputDir
    }

    void setOutputDir(File outputDir) {
        module.outputDir
    }

    /**
     * The output directory for test classes. If {@code null}, no entry will be created.
     */
    File getTestOutputDir() {
        module.testOutputDir
    }

    void setTestOutputDir(File testOutputDir) {
        module.testOutputDir
    }

    /**
     * The JDK to use for this module. If {@code null}, the value of the existing or default ipr XML (inherited)
     * is used. If it is set to <code>inherited</code>, the project SDK is used. Otherwise the SDK for the corresponding
     * value of java version is used for this module
     */
    String getJavaVersion() {
        module.javaVersion
    }

    void setJavaVersion(String javaVersion) {
        module.javaVersion = javaVersion
    }

    /**
     * Whether to download and add sources associated with the dependency jars.
     */
    boolean getDownloadSources() {
        module.downloadSources
    }

    void setDownloadSources(boolean downloadSources) {
        module.downloadSources = downloadSources
    }

    /**
     * Whether to download and add javadoc associated with the dependency jars.
     */
    boolean getDownloadJavadoc() {
        module.downloadJavadoc
    }

    void setDownloadJavadoc(boolean downloadJavadoc) {
        module.downloadJavadoc = downloadJavadoc
    }

    /**
     * The variables to be used for replacing absolute paths in the iml entries. For example, you might add a
     * {@code GRADLE_USER_HOME} variable to point to the Gradle user home dir.
     */
    Map<String, File> getVariables() {
        module.variables
    }

    void setVariables(Map<String, File> variables) {
        module.variables = variables
    }

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
     * ideaModule {
     *   scopes.PROVIDED.plus += configurations.provided
     * }
     * </pre>
     */
    Map<String, Map<String, Configuration>> getScopes() {
        module.scopes
    }

    Map<String, Map<String, Configuration>> setScopes(Map<String, Map<String, Configuration>> scopes) {
        module.scopes = scopes
    }

    /**
     * Configures output *.iml file. It's <b>optional</b> because the task should configure it correctly for you
     * (including making sure it is unique in the multi-module build).
     * If you really need to change the output file name it is much easier to do it via the <b>moduleName</b> property.
     * <p>
     * Please refer to documentation on <b>moduleName</b> property. In IntelliJ IDEA the module name is the same as the name of the *.iml file.
     */
    File getOutputFile() {
        return module.outputFile
    }

    void setOutputFile(File newOutputFile) {
        module.outputFile = newOutputFile
    }

    /**
     * Configures module name. It's <b>optional</b> because the task should configure it correctly for you.
     * By default it will try to use the <b>project.name</b> or prefix it with a part of a <b>project.path</b>
     * to make sure the moduleName is unique in the scope of a multi-module build.
     * The 'uniqeness' of a module name is required for correct import
     * into IntelliJ IDEA and the task will make sure the name is unique. See example below...
     * <p>
     * <b>moduleName</b> is a synthethic property that actually modifies the <b>outputFile</b> property value.
     * This means that you should not configure both moduleName and outputFile at the same time. moduleName is recommended.
     * <p>
     * However, in case you really need to override the default moduleName this is the way to go:
     * <pre autoTested=''>
     * apply plugin: 'idea'
     *
     * ideaModule {
     *   moduleName = 'some-important-project'
     * }
     * </pre>
     * <p>
     * <b>since</b> 1.0-milestone-2
     */
    String getModuleName() {
        return module.name
    }

    void setModuleName(String moduleName) {
        module.name = moduleName
    }
}
