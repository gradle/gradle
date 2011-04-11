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
package org.gradle.plugins.ide.idea;


import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.internal.ClassGenerator
import org.gradle.api.plugins.JavaPlugin
import org.gradle.plugins.ide.idea.model.IdeaModel
import org.gradle.plugins.ide.idea.model.IdeaModule
import org.gradle.plugins.ide.idea.model.IdeaModuleIml
import org.gradle.plugins.ide.idea.model.IdeaProject
import org.gradle.plugins.ide.idea.model.IdeaProjectIpr
import org.gradle.plugins.ide.idea.model.PathFactory
import org.gradle.plugins.ide.internal.IdePlugin

/**
 * @author Hans Dockter
 *
 * Adds an IdeaModule task. When applied to a root project, also adds an IdeaProject task.
 * For projects that have the Java plugin applied, the tasks receive additional Java-specific configuration.
 */
class IdeaPlugin extends IdePlugin {

    IdeaModel model

    @Override protected String getLifecycleTaskName() {
        return 'idea'
    }

    @Override protected void onApply(Project project) {
        lifecycleTask.description = 'Generates IDEA project files (IML, IPR, IWS)'
        cleanTask.description = 'Cleans IDEA project files (IML, IPR)'

        model = project.services.get(ClassGenerator).newInstance(IdeaModel)
        project.convention.plugins.idea = model

        configureIdeaWorkspace(project)
        configureIdeaProject(project)
        configureIdeaModule(project)
        configureForJavaPlugin(project)

        project.afterEvaluate {
            new IdeaConfigurer().configure(project)
        }
    }

    private configureIdeaWorkspace(Project project) {
        if (isRoot(project)) {
            def task = project.task('ideaWorkspace', description: 'Generates an IDEA workspace file (IWS)', type: GenerateIdeaWorkspace) {
                outputFile = new File(project.projectDir, project.name + ".iws")
            }
            addWorker(task)
        }
    }

    private configureIdeaModule(Project project) {
        def task = project.task('ideaModule', description: 'Generates IDEA module files (IML)', type: GenerateIdeaModule) {
            def iml = new IdeaModuleIml(xmlTransformer: xmlTransformer, generateTo: project.projectDir)
            module = services.get(ClassGenerator).newInstance(IdeaModule, [project: project, iml: iml])

            model.module = module

            module.conventionMapping.sourceDirs = { [] as LinkedHashSet }
            module.conventionMapping.name = { project.name }
            module.conventionMapping.contentRoot = { project.projectDir }
            module.conventionMapping.testSourceDirs = { [] as LinkedHashSet }
            module.conventionMapping.excludeDirs = { [project.buildDir, project.file('.gradle')] as LinkedHashSet }

            module.conventionMapping.pathFactory = {
                PathFactory factory = new PathFactory()
                factory.addPathVariable('MODULE_DIR', outputFile.parentFile)
                variables.each { key, value ->
                    factory.addPathVariable(key, value)
                }
                factory
            }
        }

        addWorker(task)
    }

    private configureIdeaProject(Project project) {
        if (isRoot(project)) {
            def task = project.task('ideaProject', description: 'Generates IDEA project file (IPR)', type: GenerateIdeaProject) {
                def ipr = new IdeaProjectIpr(xmlTransformer: xmlTransformer)
                ideaProject = services.get(ClassGenerator).newInstance(IdeaProject, [ipr: ipr])

                model.project = ideaProject

                ideaProject.conventionMapping.outputFile = { new File(project.projectDir, project.name + ".ipr") }
                ideaProject.conventionMapping.javaVersion = { JavaVersion.VERSION_1_6.toString() }
                ideaProject.conventionMapping.wildcards = { ['!?*.java', '!?*.groovy'] as Set }
                ideaProject.conventionMapping.subprojects = { project.rootProject.allprojects }
                ideaProject.conventionMapping.pathFactory = {
                    new PathFactory().addPathVariable('PROJECT_DIR', outputFile.parentFile)
                }
            }
            addWorker(task)
        }
    }

    private configureForJavaPlugin(Project project) {
        project.plugins.withType(JavaPlugin) {
            configureIdeaProjectForJava(project)
            configureIdeaModuleForJava(project)
        }
    }

    private configureIdeaProjectForJava(Project project) {
        if (isRoot(project)) {
            project.ideaProject {
                javaVersion = project.sourceCompatibility
            }
        }
    }

    private configureIdeaModuleForJava(Project project) {
        project.ideaModule {
            module.conventionMapping.sourceDirs = { project.sourceSets.main.allSource.sourceTrees.srcDirs.flatten() as LinkedHashSet }
            module.conventionMapping.testSourceDirs = { project.sourceSets.test.allSource.sourceTrees.srcDirs.flatten() as LinkedHashSet }
            def configurations = project.configurations
            module.conventionMapping.scopes = {[
                    PROVIDED: [plus: [], minus: []],
                    COMPILE: [plus: [configurations.compile], minus: []],
                    RUNTIME: [plus: [configurations.runtime], minus: [configurations.compile]],
                    TEST: [plus: [configurations.testRuntime], minus: [configurations.runtime]]
            ]}
        }
    }

    private boolean isRoot(Project project) {
        return project.parent == null
    }
}

