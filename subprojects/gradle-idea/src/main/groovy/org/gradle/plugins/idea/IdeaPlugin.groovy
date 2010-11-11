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
package org.gradle.plugins.idea;


import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.internal.plugins.IdePlugin
import org.gradle.api.plugins.JavaPlugin

/**
 * @author Hans Dockter
 *
 * When applied to a project, this plugin add one IdeaModule task. If the project is the root project, the plugin
 * adds also an IdeaProject task.
 *
 * If the java plugin is or has been added to a project where this plugin is applied to, the IdeaModule task gets some
 * Java specific configuration.
 */
class IdeaPlugin extends IdePlugin {
    @Override protected String getLifecycleTaskName() {
        return 'idea'
    }

    @Override protected void onApply(Project project) {
        lifecycleTask.description = 'Generates IDEA project files (IML, IPR, IWS)'
        cleanTask.description = 'Cleans IDEA project files (IML, IPR)'
        configureIdeaWorkspace(project)
        configureIdeaProject(project)
        configureIdeaModule(project)
        configureForJavaPlugin(project)
    }

    private def configureIdeaWorkspace(Project project) {
        if (isRoot(project)) {
            def task = project.task('ideaWorkspace', description: 'Generates an IDEA workspace file (IWS)', type: IdeaWorkspace) {
                outputFile = new File(project.projectDir, project.name + ".iws")
            }
            addWorker(task)
        }
    }

    private def configureIdeaModule(Project project) {
        def task = project.task('ideaModule', description: 'Generates IDEA module files (IML)', type: IdeaModule) {
            conventionMapping.outputFile = { new File(project.projectDir, project.name + ".iml") }
            conventionMapping.moduleDir = { project.projectDir }
            conventionMapping.sourceDirs = { [] as Set }
            conventionMapping.excludeDirs = { [project.buildDir, project.file('.gradle')] as Set }
            conventionMapping.testSourceDirs = { [] as Set }
        }
        addWorker(task)
    }

    private def configureIdeaProject(Project project) {
        if (isRoot(project)) {
            def task = project.task('ideaProject', description: 'Generates IDEA project file (IPR)', type: IdeaProject) {
                outputFile = new File(project.projectDir, project.name + ".ipr")
                subprojects = project.rootProject.allprojects
                javaVersion = JavaVersion.VERSION_1_6.toString()
                wildcards = ['!?*.java', '!?*.groovy']
            }
            addWorker(task)
        }
    }

    private def configureForJavaPlugin(Project project) {
        project.plugins.withType(JavaPlugin).allPlugins {
            configureIdeaProjectForJava(project)
            configureIdeaModuleForJava(project)
        }
    }

    private def configureIdeaProjectForJava(Project project) {
        if (isRoot(project)) {
            project.ideaProject {
                javaVersion = project.sourceCompatibility
            }
        }
    }

    private def configureIdeaModuleForJava(Project project) {
        project.ideaModule {
            conventionMapping.sourceDirs = { project.sourceSets.main.allSource.sourceTrees.srcDirs.flatten() as Set }
            conventionMapping.testSourceDirs = { project.sourceSets.test.allSource.sourceTrees.srcDirs.flatten() as Set }
            conventionMapping.outputDir = { project.sourceSets.main.classesDir }
            conventionMapping.testOutputDir = { project.sourceSets.test.classesDir }
            def configurations = project.configurations
            scopes = [
                    COMPILE: [plus: [configurations.compile], minus: []],
                    RUNTIME: [plus: [configurations.runtime], minus: [configurations.compile]],
                    TEST: [plus: [configurations.testRuntime], minus: [configurations.runtime]]
            ]
        }
    }

    private boolean isRoot(Project project) {
        return project.parent == null
    }
}

