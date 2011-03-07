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
import org.gradle.api.Task
import org.gradle.api.internal.plugins.IdePlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.plugins.idea.configurer.DefaultIdeaAssetsConfigurer

/**
 * @author Hans Dockter
 *
 * Adds an IdeaModule task. When applied to a root project, also adds an IdeaProject task.
 * For projects that have the Java plugin applied, the tasks receive additional Java-specific configuration.
 */
class IdeaPlugin extends IdePlugin {
    @Override protected String getLifecycleTaskName() {
        return 'idea'
    }

    @Override protected void onApply(Project project) {
        lifecycleTask.description = 'Generates IDEA project files (IML, IPR, IWS)'
        cleanTask.description = 'Cleans IDEA project files (IML, IPR)'

        configureIdeaConfigurer(project)
        configureIdeaWorkspace(project)
        configureIdeaProject(project)
        configureIdeaModule(project)
        configureForJavaPlugin(project)
    }

    private def configureIdeaConfigurer(Project project) {
        def root = project.rootProject
        if (!root.tasks.findByName('ideaConfigurer')) {
            root.task('ideaConfigurer', description: 'Performs extra configuration on idea generator tasks', type: IdeaConfigurer) {
                conventionMapping.configurer = { new DefaultIdeaAssetsConfigurer() }
            }
        }
    }

    private def configureIdeaWorkspace(Project project) {
        if (isRoot(project)) {
            def task = project.task('ideaWorkspace', description: 'Generates an IDEA workspace file (IWS)', type: IdeaWorkspace) {
                outputFile = new File(project.projectDir, project.name + ".iws")
            }
            addWorker(task)
            shouldDependOnConfigurer(task)
        }
    }

    private def configureIdeaModule(Project project) {
        def task = project.task('ideaModule', description: 'Generates IDEA module files (IML)', type: IdeaModule) {
            conventionMapping.outputFile = { new File(project.projectDir, project.name + ".iml") }
            conventionMapping.moduleDir = { project.projectDir }
            conventionMapping.sourceDirs = { [] as LinkedHashSet }
            conventionMapping.excludeDirs = { [project.buildDir, project.file('.gradle')] as LinkedHashSet }
            conventionMapping.testSourceDirs = { [] as LinkedHashSet }
        }

        addWorker(task)
        shouldDependOnConfigurer(task)
    }

    private def configureIdeaProject(Project project) {
        if (isRoot(project)) {
            def task = project.task('ideaProject', description: 'Generates IDEA project file (IPR)', type: IdeaProject) {
                outputFile = new File(project.projectDir, project.name + ".ipr")
                subprojects = project.rootProject.allprojects
                javaVersion = JavaVersion.VERSION_1_6.toString()
                wildcards = ['!?*.java', '!?*.groovy']
                projectModel = new org.gradle.plugins.idea.model.Project(xmlTransformer)
            }
            addWorker(task)
            shouldDependOnConfigurer(task)
        }
    }

    private def shouldDependOnConfigurer(Task task) {
        def ideaConfigurer = task.project.rootProject.ideaConfigurer
        task.dependsOn(ideaConfigurer)
        getCleanTask(task).dependsOn(ideaConfigurer)
    }

    private def configureForJavaPlugin(Project project) {
        project.plugins.withType(JavaPlugin) {
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
            conventionMapping.sourceDirs = { project.sourceSets.main.allSource.sourceTrees.srcDirs.flatten() as LinkedHashSet }
            conventionMapping.testSourceDirs = { project.sourceSets.test.allSource.sourceTrees.srcDirs.flatten() as LinkedHashSet }
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

