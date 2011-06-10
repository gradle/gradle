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
import org.gradle.util.DeprecationLogger

/**
 * Generates an IDEA module file. If you want to fine tune the idea configuration
 * <p>
 * Please refer to interesting examples on idea configuration in {@link IdeaModule}.
 * <p>
 * TAt this moment nearly all configuration is done via {@link IdeaModule}.
 *
 * @author Hans Dockter
 */
public class GenerateIdeaModule extends XmlGeneratorTask<Module> {

    /**
     * Idea module model
     */
    IdeaModule module

    @Override protected Module create() {
        new Module(xmlTransformer, module.pathFactory)
    }

    @Override protected void configure(Module xmlModule) {
        getModule().mergeXmlModule(xmlModule)
    }

    /**
     * Deprecated. Please use #idea.module.contentRoot. See examples in {@link IdeaModule}.
     * <p>
     * The content root directory of the module.
     */
    @Deprecated
    File getModuleDir() {
        DeprecationLogger.nagUser("ideaModule.moduleDir", "idea.module.contentRoot")
        module.contentRoot
    }

    @Deprecated
    void setModuleDir(File contentRoot) {
        DeprecationLogger.nagUser("ideaModule.moduleDir", "idea.module.contentRoot")
        module.contentRoot = contentRoot
    }

    /**
     * Deprecated. Please use #idea.module.sourceDirs. See examples in {@link IdeaModule}.
     * <p>
     * The directories containing the production sources.
     */
    @Deprecated
    Set<File> getSourceDirs() {
       DeprecationLogger.nagUser("ideaModule.sourceDirs", "idea.module.sourceDirs")
       module.sourceDirs
    }

    @Deprecated
    void setSourceDirs(Set<File> sourceDirs) {
       DeprecationLogger.nagUser("ideaModule.sourceDirs", "idea.module.sourceDirs")
       module.sourceDirs = sourceDirs
    }

    /**
     * Deprecated. Please use #idea.module.testSourceDirs. See examples in {@link IdeaModule}.
     * <p>
     * The directories containing the test sources.
     */
    @Deprecated
    Set<File> getTestSourceDirs() {
        DeprecationLogger.nagUser("ideaModule.testSourceDirs", "idea.module.testSourceDirs")
        module.testSourceDirs
    }

    @Deprecated
    void setTestSourceDirs(Set<File> testSourceDirs) {
        DeprecationLogger.nagUser("ideaModule.testSourceDirs", "idea.module.testSourceDirs")
        module.testSourceDirs = testSourceDirs
    }

    /**
     * Deprecated. Please use #idea.module.excludeDirs. See examples in {@link IdeaModule}.
     * <p>
     * The directories to be excluded.
     */
    @Deprecated
    Set<File> getExcludeDirs() {
        DeprecationLogger.nagUser("ideaModule.excludeDirs", "idea.module.excludeDirs")
        module.excludeDirs
    }

    @Deprecated
    void setExcludeDirs(Set<File> excludeDirs) {
        DeprecationLogger.nagUser("ideaModule.excludeDirs", "idea.module.excludeDirs")
        module.excludeDirs = excludeDirs
    }

    /**
     * Deprecated. Please use #idea.module.inheritOutputDirs. See examples in {@link IdeaModule}.
     * <p>
     * If true, output directories for this module will be located below the output directory for the project;
     * otherwise, they will be set to the directories specified by #outputDir and #testOutputDir.
     */
    @Deprecated
    Boolean getInheritOutputDirs() {
        DeprecationLogger.nagUser("ideaModule.inheritOutputDirs", "idea.module.inheritOutputDirs")
        module.inheritOutputDirs
    }

    @Deprecated
    void setInheritOutputDirs(Boolean inheritOutputDirs) {
        DeprecationLogger.nagUser("ideaModule.inheritOutputDirs", "idea.module.inheritOutputDirs")
        module.inheritOutputDirs = inheritOutputDirs
    }

    /**
     * Deprecated. Please use #idea.module.outputDir. See examples in {@link IdeaModule}.
     * <p>
     * The output directory for production classes. If {@code null}, no entry will be created.
     */
    @Deprecated
    File getOutputDir() {
        DeprecationLogger.nagUser("ideaModule.outputDir", "idea.module.outputDir")
        module.outputDir
    }

    @Deprecated
    void setOutputDir(File outputDir) {
        DeprecationLogger.nagUser("ideaModule.outputDir", "idea.module.outputDir")
        module.outputDir = outputDir
    }

    /**
     * Deprecated. Please use #idea.module.testOutputDir. See examples in {@link IdeaModule}.
     * <p>
     * The output directory for test classes. If {@code null}, no entry will be created.
     */
    @Deprecated
    File getTestOutputDir() {
        DeprecationLogger.nagUser("ideaModule.testOutputDir", "idea.module.testOutputDir")
        module.testOutputDir
    }

    @Deprecated
    void setTestOutputDir(File testOutputDir) {
        DeprecationLogger.nagUser("ideaModule.testOutputDir", "idea.module.testOutputDir")
        module.testOutputDir = testOutputDir
    }

    /**
     * Deprecated. Please use #idea.module.javaVersion. See examples in {@link IdeaModule}.
     * <p>
     * The JDK to use for this module. If {@code null}, the value of the existing or default ipr XML (inherited)
     * is used. If it is set to <code>inherited</code>, the project SDK is used. Otherwise the SDK for the corresponding
     * value of java version is used for this module
     */
    @Deprecated
    String getJavaVersion() {
        DeprecationLogger.nagUser("ideaModule.javaVersion", "idea.module.javaVersion")
        module.javaVersion
    }

    @Deprecated
    void setJavaVersion(String javaVersion) {
        DeprecationLogger.nagUser("ideaModule.javaVersion", "idea.module.javaVersion")
        module.javaVersion = javaVersion
    }

    /**
     * Deprecated. Please use #idea.module.downloadSources. See examples in {@link IdeaModule}.
     * <p>
     * Whether to download and add sources associated with the dependency jars.
     */
    @Deprecated
    boolean getDownloadSources() {
        DeprecationLogger.nagUser("ideaModule.downloadSources", "idea.module.downloadSources")
        module.downloadSources
    }

    @Deprecated
    void setDownloadSources(boolean downloadSources) {
        DeprecationLogger.nagUser("ideaModule.downloadSources", "idea.module.downloadSources")
        module.downloadSources = downloadSources
    }

    /**
     * Deprecated. Please use #idea.module.downloadJavadoc. See examples in {@link IdeaModule}.
     * <p>
     * Whether to download and add javadoc associated with the dependency jars.
     */
    @Deprecated
    boolean getDownloadJavadoc() {
        DeprecationLogger.nagUser("ideaModule.downloadJavadoc", "idea.module.downloadJavadoc")
        module.downloadJavadoc
    }

    @Deprecated
    void setDownloadJavadoc(boolean downloadJavadoc) {
        DeprecationLogger.nagUser("ideaModule.downloadJavadoc", "idea.module.downloadJavadoc")
        module.downloadJavadoc = downloadJavadoc
    }

    /**
     * Deprecated. Please use #idea.pathVariables. See examples in {@link IdeaModule}.
     * <p>
     * The variables to be used for replacing absolute paths in the iml entries. For example, you might add a
     * {@code GRADLE_USER_HOME} variable to point to the Gradle user home dir.
     */
    @Deprecated
    Map<String, File> getVariables() {
        DeprecationLogger.nagUser("ideaModule.variables", "idea.pathVariables")
        module.pathVariables
    }

    @Deprecated
    void setVariables(Map<String, File> variables) {
        DeprecationLogger.nagUser("ideaModule.variables", "idea.pathVariables")
        module.pathVariables = variables
    }

    /**
     * Deprecated. Please use #idea.module.scopes. See examples in {@link IdeaModule}.
     * <p>
     * The keys of this map are the IDEA scopes. Each key points to another map that has two keys, plus and minus.
     * The values of those keys are collections of {@link org.gradle.api.artifacts.Configuration} objects. The files of the
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
    @Deprecated
    Map<String, Map<String, Collection<Configuration>>> getScopes() {
        DeprecationLogger.nagUser("ideaModule.scopes", "idea.module.scopes")
        module.scopes
    }

    @Deprecated
    void setScopes(Map<String, Map<String, Collection<Configuration>>> scopes) {
        DeprecationLogger.nagUser("ideaModule.scopes", "idea.module.scopes")
        module.scopes = scopes
    }

    /**
     * Configures output *.iml file. It's <b>optional</b> because the task should configure it correctly for you
     * (including making sure it is unique in the multi-module build).
     * If you really need to change the output file name it is much easier to do it via the <b>idea.module.name</b> property.
     * <p>
     * Please refer to documentation in {@link IdeaModule} <b>name</b> property. In IntelliJ IDEA the module name is the same as the name of the *.iml file.
     */
    File getOutputFile() {
        return module.outputFile
    }

    void setOutputFile(File newOutputFile) {
        module.outputFile = newOutputFile
    }

    /**
     * Deprecated. Please use #idea.module.name. See examples in {@link IdeaModule}.
     * <p>
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
    @Deprecated
    String getModuleName() {
        DeprecationLogger.nagUser("ideaModule.moduleName", "idea.module.name")
        return module.name
    }

    @Deprecated
    void setModuleName(String moduleName) {
        DeprecationLogger.nagUser("ideaModule.moduleName", "idea.module.name")
        module.name = moduleName
    }
}
