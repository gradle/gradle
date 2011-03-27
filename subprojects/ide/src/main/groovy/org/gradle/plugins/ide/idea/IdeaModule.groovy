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

import org.gradle.api.internal.artifacts.dependencies.DefaultExternalModuleDependency
import org.gradle.api.specs.Specs
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.XmlGeneratorTask
import org.gradle.plugins.ide.idea.model.Module
import org.gradle.plugins.ide.idea.model.ModuleLibrary
import org.gradle.plugins.ide.idea.model.Path
import org.gradle.plugins.ide.idea.model.PathFactory
import org.gradle.plugins.ide.idea.model.internal.ModuleDependencyBuilder
import org.gradle.api.artifacts.*

/**
 * Generates an IDEA module file.
 * <p>
 * Usually it is not necessary to configure ideaModule directly because applying the 'idea' plugin on projects should be enough.
 * However, if you need to do it here's how:
 * <pre>
 * allprojects {
 *   apply plugin: 'java'
 *   apply plugin: 'idea'
 * }
 *
 * project(':model') {
 *   ideaModule {
 *     //...
 *   }
 * }
 * </pre>
 *
 * @author Hans Dockter
 */
public class IdeaModule extends XmlGeneratorTask<Module> {
    /**
     * The content root directory of the module.
     */
    @Input
    File moduleDir

    /**
     * The directories containing the production sources.
     */
    @Input
    Set<File> sourceDirs

    /**
     * The directories containing the test sources.
     */
    @Input
    Set<File> testSourceDirs

    /**
     * The directories to be excluded.
     */
    @Input
    Set<File> excludeDirs

    /**
     * If true, output directories for this module will be located below the output directory for the project;
     * otherwise, they will be set to the directories specified by {@link #outputDir} and {@link #testOutputDir}.
     */
    @Input @Optional
    Boolean inheritOutputDirs

    /**
     * The output directory for production classes. If {@code null}, no entry will be created.
     */
    @Input @Optional
    File outputDir

    /**
     * The output directory for test classes. If {@code null}, no entry will be created.
     */
    @Input @Optional
    File testOutputDir

    /**
     * The JDK to use for this module. If {@code null}, the value of the existing or default ipr XML (inherited)
     * is used. If it is set to <code>inherited</code>, the project SDK is used. Otherwise the SDK for the corresponding
     * value of java version is used for this module
     */
    @Input @Optional
    String javaVersion = org.gradle.plugins.ide.idea.model.Module.INHERITED

    /**
     * Whether to download and add sources associated with the dependency jars.
     */
    @Input
    boolean downloadSources = true

    /**
     * Whether to download and add javadoc associated with the dependency jars.
     */
    @Input
    boolean downloadJavadoc = false

    /**
     * The variables to be used for replacing absolute paths in the iml entries. For example, you might add a
     * {@code GRADLE_USER_HOME} variable to point to the Gradle user home dir.
     */
    @Input
    Map<String, File> variables = [:]

    /**
     * The keys of this map are the Intellij scopes. Each key points to another map that has two keys, plus and minus.
     * The values of those keys are sets of  {@link org.gradle.api.artifacts.Configuration}  objects. The files of the
     * plus configurations are added minus the files from the minus configurations.
     * <p>
     * Example how to use scopes property to enable 'provided' dependencies in the output *.iml file:
     * <pre>
     * configurations {
     *   provided
     *   provided.extendsFrom(compile)
     * }
     *
     * dependencies { ... }
     *
     * ideaModule {
     *   scopes.PROVIDED.plus += configurations.provided
     * }
     * </pre>
     */
    Map<String, Map<String, Configuration>> scopes = [:]

    @Override protected Module create() {
        Module module = new Module(xmlTransformer)
        configurePathFactory(module)
        return module
    }

    @Override protected void configure(Module module) {
        module.configure(getContentPath(), getSourcePaths(), getTestSourcePaths(), getExcludePaths(),
                inheritOutputDirs, getOutputPath(), getTestOutputPath(), getDependencies(), javaVersion)
    }

    protected Path getContentPath() {
        getPath(getModuleDir())
    }

    protected Path getOutputPath() {
        getOutputDir() ? getPath(getOutputDir()) : null
    }

    protected Path getTestOutputPath() {
        getTestOutputDir() ? getPath(getTestOutputDir()) : null
    }

    protected Set getSourcePaths() {
        getSourceDirs().findAll { it.exists() }.collect { getPath(it) }
    }

    protected Set getTestSourcePaths() {
        getTestSourceDirs().findAll { it.exists() }.collect { getPath(it) }
    }

    protected Set getExcludePaths() {
        getExcludeDirs().collect { getPath(it) }
    }

    protected Set getDependencies() {
        scopes.keySet().inject([] as LinkedHashSet) { result, scope ->
            result.addAll(getModuleLibraries(scope))
            result.addAll(getModules(scope))
            result
        }
    }

    protected Set getModules(String scope) {
        if (scopes[scope]) {
            return getScopeDependencies(scopes[scope], { it instanceof ProjectDependency }).collect { ProjectDependency dependency ->
                def project = dependency.dependencyProject
                return new ModuleDependencyBuilder().create(project, scope)
            }
        }
        return []
    }

    protected Set getModuleLibraries(String scope) {
        if (scopes[scope]) {
            Set firstLevelDependencies = getScopeDependencies(scopes[scope], { it instanceof ExternalDependency })

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

    private Set getScopeDependencies(Map<String, Configuration> configurations, Closure filter) {
        Set firstLevelDependencies = new LinkedHashSet()
        configurations.plus.each { Configuration configuration ->
            firstLevelDependencies.addAll(configuration.getAllDependencies().findAll(filter))
        }
        configurations.minus.each { Configuration configuration ->
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

    private getFiles(Set dependencies, String classifier) {
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

    protected Set getAllDeps(Set deps, Set allDeps = []) {
        deps.each { ResolvedDependency resolvedDependency ->
            def notSeenBefore = allDeps.add(resolvedDependency)
            if (notSeenBefore) { // defend against circular dependencies
                getAllDeps(resolvedDependency.children, allDeps)
            }
        }
        allDeps
    }

    protected addSourceArtifact(DefaultExternalModuleDependency dependency) {
        dependency.artifact { artifact ->
            artifact.name = dependency.name
            artifact.type = 'source'
            artifact.extension = 'jar'
            artifact.classifier = 'sources'
        }
    }

    protected addJavadocArtifact(DefaultExternalModuleDependency dependency) {
        dependency.artifact { artifact ->
            artifact.name = dependency.name
            artifact.type = 'javadoc'
            artifact.extension = 'jar'
            artifact.classifier = 'javadoc'
        }
    }

    protected Path getPath(File file) {
        return pathFactory.path(file)
    }

    def configurePathFactory(Module module) {
        module.pathFactory = pathFactory
    }

    protected PathFactory getPathFactory() {
        PathFactory factory = new PathFactory()
        factory.addPathVariable('MODULE_DIR', getOutputFile().parentFile)
        variables.each { key, value ->
            factory.addPathVariable(key, value)
        }
        return factory
    }

    /**
     * Configures output *.iml file. It's <b>optional</b> because the task should configure it correctly for you
     * (including making sure it is unique in the multi-module build).
     * If you really need to change the output file name it is much easier to do it via the <b>moduleName</b> property.
     * <p>
     * Please refer to documentation on <b>moduleName</b> property. In IntelliJ IDEA the module name is the same as the name of the *.iml file.
     *
     * @return
     */
    File getOutputFile() {
        //this getter lives here only for the sake of generating the DSL documentation.
        return super.outputFile
    }

    /**
     * Configures module name. It's <b>optional</b> because the task should configure it correctly for you.
     * By default it will try to use the <b>project.name</b> or prefix it with a part of a <b>project.path</b>
     * to make sure the moduleName is unique in the scope of a multi-module build.
     * The 'uniqeness' of a module name is required for correct import
     * into IntelliJ IDEA and the task will make sure the name is unique.
     * <p>
     * <b>moduleName</b> is a synthethic property that actually modifies the <b>outputFile</b> property value.
     * This means that you should not configure both moduleName and outputFile at the same time. moduleName is recommended.
     * <p>
     * However, in case you really need to override the default moduleName this is the way to go:
     * <pre>
     * project(':someProject') {
     *    ideaModule {
     *      moduleName = 'some-important-project'
     *    }
     * }
     * </pre>
     * <p>
     * <b>since</b> 1.0-milestone-2
     * <p>
     * @return
     */
    String getModuleName() {
        return outputFile.name.replaceFirst(/\.iml$/,"");
    }

    void setModuleName(String moduleName) {
        outputFile = new File(outputFile.parentFile, moduleName + ".iml")
    }
}
