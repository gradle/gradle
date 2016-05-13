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
package org.gradle.plugins.ide.idea

import groovy.transform.CompileStatic
import org.codehaus.groovy.runtime.InvokerHelper
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.internal.ConventionMapping
import org.gradle.api.internal.IConventionAware
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.plugins.scala.ScalaBasePlugin
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.xml.XmlTransformer
import org.gradle.language.scala.plugins.ScalaLanguagePlugin
import org.gradle.plugins.ide.api.XmlFileContentMerger
import org.gradle.plugins.ide.idea.internal.IdeaNameDeduper
import org.gradle.plugins.ide.idea.internal.IdeaScalaConfigurer
import org.gradle.plugins.ide.idea.model.IdeaLanguageLevel
import org.gradle.plugins.ide.idea.model.IdeaModel
import org.gradle.plugins.ide.idea.model.IdeaModule
import org.gradle.plugins.ide.idea.model.IdeaModuleIml
import org.gradle.plugins.ide.idea.model.IdeaProject
import org.gradle.plugins.ide.idea.model.IdeaWorkspace
import org.gradle.plugins.ide.idea.model.PathFactory
import org.gradle.plugins.ide.internal.IdePlugin

import javax.inject.Inject

/**
 * Adds a GenerateIdeaModule task. When applied to a root project, also adds a GenerateIdeaProject task.
 * For projects that have the Java plugin applied, the tasks receive additional Java-specific configuration.
 */
@CompileStatic
// This class was not converted to Java because IDEA Gradle integration casts it to GroovyObject.
class IdeaPlugin extends IdePlugin {
    private final Instantiator instantiator
    private IdeaModel ideaModel

    @Inject
    IdeaPlugin(Instantiator instantiator) {
        this.instantiator = instantiator
    }

    public IdeaModel getModel() {
        ideaModel
    }

    @Override
    protected String getLifecycleTaskName() {
        return 'idea'
    }

    @Override
    protected void onApply(Project project) {
        lifecycleTask.description = 'Generates IDEA project files (IML, IPR, IWS)'
        cleanTask.description = 'Cleans IDEA project files (IML, IPR)'

        ideaModel = (IdeaModel) project.extensions.create("idea", IdeaModel)

        configureIdeaWorkspace(project)
        configureIdeaProject(project)
        configureIdeaModule(project)
        configureForJavaPlugin(project)
        configureForScalaPlugin()
        hookDeduplicationToTheRoot(project)
    }

    void hookDeduplicationToTheRoot(Project project) {
        if (isRoot(project)) {
            project.gradle.projectsEvaluated {
                makeSureModuleNamesAreUnique()
            }
        }
    }

    public void makeSureModuleNamesAreUnique() {
        new IdeaNameDeduper().configureRoot(project.rootProject)
    }

    private configureIdeaWorkspace(Project project) {
        if (isRoot(project)) {
            def task = project.task('ideaWorkspace', description: 'Generates an IDEA workspace file (IWS)', type: GenerateIdeaWorkspace) { GenerateIdeaWorkspace task ->
                task.workspace = new IdeaWorkspace(iws: new XmlFileContentMerger((XmlTransformer) task.getProperty('xmlTransformer')))
                ideaModel.workspace = task.workspace
                task.outputFile = new File(project.projectDir, "${project.name}.iws")
            }
            addWorker(task, false)
        }
    }

    private configureIdeaProject(Project project) {
        if (isRoot(project)) {
            def task = project.task('ideaProject', description: 'Generates IDEA project file (IPR)', type: GenerateIdeaProject) { GenerateIdeaProject task ->
                def ipr = new XmlFileContentMerger((XmlTransformer) task.getProperty('xmlTransformer'))
                def ideaProject = instantiator.newInstance(IdeaProject, project, ipr)
                task.ideaProject = ideaProject
                ideaModel.project = ideaProject

                ideaProject.outputFile = new File(project.projectDir, project.name + ".ipr")
                def conventionMapping = ((IConventionAware)ideaProject).conventionMapping
                conventionMapping.map('jdkName') { JavaVersion.current().toString() }
                conventionMapping.map('languageLevel') {
                    JavaVersion maxSourceCompatibility = getMaxJavaModuleCompatibilityVersionFor { Project p ->
                        p.convention.getPlugin(JavaPluginConvention).sourceCompatibility
                    }
                    new IdeaLanguageLevel(maxSourceCompatibility)
                }
                conventionMapping.map('targetBytecodeVersion') {
                    getMaxJavaModuleCompatibilityVersionFor { Project p ->
                        p.convention.getPlugin(JavaPluginConvention).targetCompatibility
                    }
                }

                ideaProject.wildcards = ['!?*.class', '!?*.scala', '!?*.groovy', '!?*.java'] as Set
                conventionMapping.map('modules') {
                    project.rootProject.allprojects.findAll { it.plugins.hasPlugin(IdeaPlugin) }.collect {
                        ideaModelFor(it).module
                    }
                }

                conventionMapping.map('pathFactory') {
                    new PathFactory().addPathVariable('PROJECT_DIR', task.outputFile.parentFile)
                }
            }
            addWorker(task)
        }
    }

    private static IdeaModel ideaModelFor(Project project) {
        (IdeaModel) InvokerHelper.getProperty(project, 'idea')
    }

    private JavaVersion getMaxJavaModuleCompatibilityVersionFor(Closure collectClosure) {
        List<JavaVersion> allProjectJavaVersions = project.rootProject.allprojects.findAll { it.plugins.hasPlugin(IdeaPlugin) && it.plugins.hasPlugin(JavaBasePlugin) }.collect(collectClosure)
        JavaVersion maxJavaVersion = allProjectJavaVersions.max() ?: JavaVersion.VERSION_1_6
        maxJavaVersion
    }

    private configureIdeaModule(Project project) {
        def task = project.task('ideaModule', description: 'Generates IDEA module files (IML)', type: GenerateIdeaModule) { GenerateIdeaModule task ->
            def iml = new IdeaModuleIml((XmlTransformer) task.getProperty('xmlTransformer'), project.projectDir)
            def module = instantiator.newInstance(IdeaModule, project, iml)
            task.module = module

            ideaModel.module = module
            def conventionMapping = ((IConventionAware)module).conventionMapping
            conventionMapping.map('sourceDirs') { [] as LinkedHashSet }
            conventionMapping.map('name') { project.name }
            conventionMapping.map('contentRoot') { project.projectDir }
            conventionMapping.map('testSourceDirs') { [] as LinkedHashSet }
            conventionMapping.map('excludeDirs') { [project.buildDir, project.file('.gradle')] as LinkedHashSet }

            conventionMapping.map('pathFactory') {
                PathFactory factory = new PathFactory()
                factory.addPathVariable('MODULE_DIR', task.outputFile.parentFile)
                module.pathVariables.each { key, value ->
                    factory.addPathVariable(key, value)
                }
                factory
            }
        }

        addWorker(task)
    }

    private configureForJavaPlugin(Project project) {
        project.plugins.withType(JavaPlugin) {
            configureIdeaModuleForJava(project)
        }
    }

    private configureIdeaModuleForJava(Project project) {
        project.tasks.withType(GenerateIdeaModule) { GenerateIdeaModule ideaModule ->
            // Defaults
            ideaModule.module.setScopes([
                PROVIDED: [plus: [], minus: []],
                COMPILE: [plus: [], minus: []],
                RUNTIME: [plus: [], minus: []],
                TEST: [plus: [], minus: []]
            ] as Map<String, Map<String, Collection<Configuration>>>)
            // Convention
            ConventionMapping convention = ((IConventionAware)ideaModule.module).conventionMapping
            convention.map('sourceDirs') {
                SourceSetContainer sourceSets = project.convention.getPlugin(JavaPluginConvention.class).sourceSets;
                sourceSets.getByName('main').allSource.srcDirs as LinkedHashSet
            }
            convention.map('testSourceDirs') {
                SourceSetContainer sourceSets = project.convention.getPlugin(JavaPluginConvention.class).sourceSets;
                sourceSets.getByName('test').allSource.srcDirs as LinkedHashSet
            }
            convention.map('singleEntryLibraries') {
                SourceSetContainer sourceSets = project.convention.getPlugin(JavaPluginConvention.class).sourceSets;
                [
                    RUNTIME: sourceSets.getByName('main').output.dirs,
                    TEST: sourceSets.getByName('test').output.dirs
                ]
            }
            convention.map('targetBytecodeVersion') {
                JavaVersion moduleTargetBytecodeLevel = project.convention.getPlugin(JavaPluginConvention).targetCompatibility
                includeModuleBytecodeLevelOverride(project.rootProject, moduleTargetBytecodeLevel) ? moduleTargetBytecodeLevel : null
            }
            convention.map('languageLevel') {
                def moduleLanguageLevel = new IdeaLanguageLevel(project.convention.getPlugin(JavaPluginConvention).sourceCompatibility)
                includeModuleLanguageLevelOverride(project.rootProject, moduleLanguageLevel) ? moduleLanguageLevel : null
            }
            // Dependencies
            ideaModule.dependsOn {
                SourceSetContainer sourceSets = project.convention.getPlugin(JavaPluginConvention.class).sourceSets;
                sourceSets.getByName('main').output.dirs + sourceSets.getByName('test').output.dirs
            }
        }
    }

    private static boolean includeModuleBytecodeLevelOverride(Project rootProject, JavaVersion moduleTargetBytecodeLevel) {
        if (!rootProject.plugins.hasPlugin(IdeaPlugin)) {
            return true
        }
        IdeaProject ideaProject = ideaModelFor(rootProject).project
        return moduleTargetBytecodeLevel != ideaProject.getTargetBytecodeVersion()
    }

    private static boolean includeModuleLanguageLevelOverride(Project rootProject, IdeaLanguageLevel moduleLanguageLevel) {
        if (!rootProject.plugins.hasPlugin(IdeaPlugin)) {
            return true
        }
        IdeaProject ideaProject = ideaModelFor(rootProject).project
        return moduleLanguageLevel != ideaProject.languageLevel
    }

    private void configureForScalaPlugin() {
        project.plugins.withType(ScalaBasePlugin) {
            ideaModuleDependsOnRoot()
        }
        project.plugins.withType(ScalaLanguagePlugin) {
            ideaModuleDependsOnRoot()
        }
        if (isRoot(project)) {
            new IdeaScalaConfigurer(project).configure()
        }
    }

    private void ideaModuleDependsOnRoot() {
        // see IdeaScalaConfigurer which requires the ipr to be generated first
        project.getTasks().findByName("ideaModule").dependsOn(project.getRootProject().getTasks().findByName("ideaProject"));
    }

    private static boolean isRoot(Project project) {
        return project.parent == null
    }
}
