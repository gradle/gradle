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
package org.gradle.plugins.idea

import org.gradle.api.Action
import org.gradle.api.internal.ConventionTask
import org.gradle.api.internal.artifacts.dependencies.DefaultExternalModuleDependency
import org.gradle.api.specs.Specs
import org.gradle.listener.ListenerBroadcast
import org.gradle.api.artifacts.*
import org.gradle.api.tasks.*
import org.gradle.plugins.idea.model.*
import org.gradle.api.internal.XmlTransformer
import org.gradle.api.artifacts.maven.XmlProvider

/**
 * Generates an IDEA module file.
 *
 * @author Hans Dockter
 */
public class IdeaModule extends ConventionTask {
    /**
     * The content root directory of the module. Must not be null.
     */
    @InputFiles
    File moduleDir

    /**
     * The iml file. Used to look for existing files as well as the target for generation. Must not be null. 
     */
    @OutputFile
    File outputFile

    /**
     * The dirs containing the production sources. Must not be null.
     */
    @InputFiles
    Set sourceDirs

    /**
     * The dirs containing the test sources. Must not be null.
     */
    @InputFiles
    Set testSourceDirs

    /**
     * The dirs to be excluded by idea. Must not be null.
     */
    @InputFiles
    Set excludeDirs

    /**
     * The idea output dir for the production sources. If null no entry for output dirs is created.
     */
    @InputFiles @Optional
    File outputDir

    /**
     * The idea output dir for the test sources. If null no entry for test output dirs is created.
     */
    @InputFiles @Optional
    File testOutputDir

    /**
     * If this is null the value of the existing or default ipr XML (inherited) is used. If it is set
     * to <code>inherited</code>, the project SDK is used. Otherwise the SDK for the corresponding
     * value of java version is used for this module
     */
    @Input @Optional
    String javaVersion = org.gradle.plugins.idea.model.Module.INHERITED

    /**
     * Whether to download and add sources associated with the dependency jars. Defaults to true. 
     */
    @Input
    boolean downloadSources = true

    /**
     * Whether to download and add javadoc associated with the dependency jars. Defaults to false.
     */
    @Input
    boolean downloadJavadoc = false

    /**
     * If this variable is set, dependencies in the existing iml file will be parsed for this variable.
     * If they use it, it will be replaced with a path that has the $MODULE_DIR$ variable as a root and
     * then a relative path to  {@link #gradleCacheHome} . That way Gradle can recognize equal dependencies.
     */
    @Input @Optional
    String gradleCacheVariable

    /**
     * This variable is used in conjunction with the {@link #gradleCacheVariable}.
     */
    @InputFiles @Optional
    File gradleCacheHome

    /**
     * The keys of this map are the Intellij scopes. Each key points to another map that has two keys, plus and minus.
     * The values of those keys are sets of  {@link org.gradle.api.artifacts.Configuration}  objects. The files of the
     * plus configurations are added minus the files from the minus configurations.
     */
    Map scopes = [:]

    private ListenerBroadcast<Action> beforeConfiguredActions = new ListenerBroadcast<Action>(Action.class);
    private ListenerBroadcast<Action> whenConfiguredActions = new ListenerBroadcast<Action>(Action.class);
    private XmlTransformer withXmlActions = new XmlTransformer();

    def IdeaModule() {
        outputs.upToDateWhen { false }
    }

    @TaskAction
    void updateXML() {
        Reader xmlreader = getOutputFile().exists() ? new FileReader(getOutputFile()) : null;
        org.gradle.plugins.idea.model.Module module = new org.gradle.plugins.idea.model.Module(getContentPath(), getSourcePaths(), getTestSourcePaths(), getExcludePaths(), getOutputPath(), getTestOutputPath(),
                getDependencies(), getVariableReplacement(), javaVersion, xmlreader, beforeConfiguredActions.source, whenConfiguredActions.source, withXmlActions)
        getOutputFile().withWriter {Writer writer -> module.toXml(writer)}
    }

    protected Path getContentPath() {
        getPath(project.projectDir)
    }

    protected Path getOutputPath() {
        getOutputDir() ? getPath(getOutputDir()) : null
    }

    protected Path getTestOutputPath() {
        getTestOutputDir() ? getPath(getTestOutputDir()) : null
    }

    protected Set getSourcePaths() {
        getSourceDirs().collect { getPath(it) }
    }

    protected Set getTestSourcePaths() {
        getTestSourceDirs().collect { getPath(it) }
    }

    protected Set getExcludePaths() {

        getExcludeDirs().collect { getPath(it) }
    }

    protected Set getDependencies() {
        scopes.keySet().inject([] as LinkedHashSet) {result, scope ->
            result + (getModuleLibraries(scope) + getModules(scope))
        }
    }

    protected getVariableReplacement() {
        if (getGradleCacheHome() && getGradleCacheVariable()) {
            String replacer = org.gradle.plugins.idea.model.Path.getRelativePath(getOutputFile().parentFile, '$MODULE_DIR$', getGradleCacheHome())
            return new VariableReplacement(replacer: replacer, replacable: '$' + getGradleCacheVariable() + '$')
        }
        return VariableReplacement.NO_REPLACEMENT
    }

    protected Set getModules(String scope) {
        if (scopes[scope]) {
            return getScopeDependencies(scopes[scope], { it instanceof ProjectDependency}).collect { ProjectDependency projectDependency ->
                projectDependency.dependencyProject
            }.collect { project ->
                new org.gradle.plugins.idea.model.ModuleDependency(project.name, scope)
            }
        }
        return []
    }

    protected Set getModuleLibraries(String scope) {
        if (scopes[scope]) {
            Set firstLevelDependencies = getScopeDependencies(scopes[scope], { it instanceof ExternalDependency})

            ResolvedConfiguration resolvedConfiguration = project.configurations.detachedConfiguration((firstLevelDependencies as Dependency[])).resolvedConfiguration
            def allResolvedDependencies = getAllDeps(resolvedConfiguration.firstLevelModuleDependencies)

            Set sourceDependencies = getResolvableDependenciesForAllResolvedDependencies(allResolvedDependencies) { dependency ->
                addSourceArtifact(dependency)
            }
            Map sourceFiles = downloadSources ? getFiles(sourceDependencies, "sources") : [:]

            Set javadocDependencies = getResolvableDependenciesForAllResolvedDependencies(allResolvedDependencies) { dependency ->
                addJavadocArtifact(dependency)
            }
            Map javadocFiles = downloadJavadoc ? getFiles(javadocDependencies, "javadoc") : [:]

            List moduleLibraries = resolvedConfiguration.getFiles(Specs.SATISFIES_ALL).collect { File binaryFile ->
                File sourceFile = sourceFiles[binaryFile.name]
                File javadocFile = javadocFiles[binaryFile.name]
                new ModuleLibrary([getPath(binaryFile)] as Set, javadocFile ? [getPath(javadocFile)] as Set : [] as Set, sourceFile ? [getPath(sourceFile)] as Set : [] as Set, [] as Set, scope)
            }
            moduleLibraries.addAll(getSelfResolvingFiles(getScopeDependencies(scopes[scope],
                    { it instanceof SelfResolvingDependency && !(it instanceof ProjectDependency)}), scope))
            return moduleLibraries as LinkedHashSet
        }
        return []
    }

    private def getSelfResolvingFiles(Collection dependencies, String scope) {
        dependencies.inject([] as LinkedHashSet) { result, SelfResolvingDependency selfResolvingDependency ->
            result.addAll(selfResolvingDependency.resolve().collect { File file ->
                new ModuleLibrary([getPath(file)] as Set, [] as Set, [] as Set, [] as Set, scope)
            })
            result
        }
    }

    private Set getScopeDependencies(Map configurations, Closure filter) {
        Set firstLevelDependencies = new LinkedHashSet()
        configurations.plus.each { configuration ->
            firstLevelDependencies.addAll(configuration.getAllDependencies().findAll(filter))
        }
        configurations.minus.each { configuration ->
            configuration.getAllDependencies().findAll(filter).each { minusDep ->
                // This deals with dependencies that are defined in different scopes with different
                // artifacts. Right now we accept the fact, that in such a situation some artifacts
                // might be duplicated in Idea (they live in different scopes then). 
                if (minusDep instanceof ExternalDependency) {
                    ExternalDependency removeCandidate = firstLevelDependencies.find { it == minusDep }
                    if (removeCandidate && removeCandidate.artifacts == minusDep.artifacts) {
                        firstLevelDependencies.remove(removeCandidate)
                    }
                } else {
                    firstLevelDependencies.remove(minusDep)
                }
            }
        }
        return firstLevelDependencies
    }

    private def getFiles(Set dependencies, String classifier) {
        return project.configurations.detachedConfiguration((dependencies as Dependency[])).files.inject([:]) { result, sourceFile ->
            String key = sourceFile.name.replace("-${classifier}.jar", '.jar')
            result[key] = sourceFile
            result
        }
    }

    private List getResolvableDependenciesForAllResolvedDependencies(Set allResolvedDependencies, Closure configureClosure) {
        return allResolvedDependencies.collect { ResolvedDependency resolvedDependency ->
            def dependency = new DefaultExternalModuleDependency(resolvedDependency.moduleGroup, resolvedDependency.moduleName, resolvedDependency.moduleVersion,
                    resolvedDependency.configuration)
            dependency.transitive = false
            configureClosure.call(dependency)
            dependency
        }
    }

    protected Set getAllDeps(Set deps) {
        Set result = []
        deps.each { ResolvedDependency resolvedDependency ->
            if (resolvedDependency.children) {
                result.addAll(getAllDeps(resolvedDependency.children))
            }
            result.add(resolvedDependency)
        }
        result
    }

    protected def addSourceArtifact(DefaultExternalModuleDependency dependency) {
        dependency.artifact { artifact ->
            artifact.name = dependency.name
            artifact.type = 'source'
            artifact.extension = 'jar'
            artifact.classifier = 'sources'
        }
    }

    protected def addJavadocArtifact(DefaultExternalModuleDependency dependency) {
        dependency.artifact { artifact ->
            artifact.name = dependency.name
            artifact.type = 'javadoc'
            artifact.extension = 'jar'
            artifact.classifier = 'javadoc'
        }
    }

    protected Path getPath(File file) {
        new Path(getOutputFile().parentFile, '$MODULE_DIR$', file)
    }

    /**
     * Adds a closure to be called when the IML XML has been created. The XML is passed to the closure as a
     * parameter in form of a {@link org.gradle.api.artifacts.maven.XmlProvider}. The closure can modify the XML.
     *
     * @param closure The closure to execute when the IML XML has been created.
     * @return this
     */
    void withXml(Closure closure) {
        withXmlActions.addAction(closure)
    }

    /**
     * Adds an action to be called when the IML XML has been created. The XML is passed to the action as a
     * parameter in form of a {@link org.gradle.api.artifacts.maven.XmlProvider}. The action can modify the XML.
     *
     * @param closure The action to execute when the IML XML has been created.
     * @return this
     */
    void withXml(Action<XmlProvider> action) {
        withXmlActions.addAction(action)
    }

    void beforeConfigured(Closure closure) {
        beforeConfiguredActions.add("execute", closure);
    }

    void whenConfigured(Closure closure) {
        whenConfiguredActions.add("execute", closure);
    }
}