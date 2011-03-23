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
import org.gradle.api.Task
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.GeneratorTaskConfigurer
import org.gradle.plugins.ide.internal.IdePlugin

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

    private configureIdeaConfigurer(Project project) {
        def root = project.rootProject
        if (!root.tasks.findByName('ideaConfigurer')) {
            root.task('ideaConfigurer', description: 'Performs extra configuration on idea generator tasks', type: IdeaConfigurer)
        }
    }

    private configureIdeaWorkspace(Project project) {
        if (isRoot(project)) {
            def task = project.task('ideaWorkspace', description: 'Generates an IDEA workspace file (IWS)', type: IdeaWorkspace) {
                outputFile = new File(project.projectDir, project.name + ".iws")
            }
            addWorker(task)
            configureGeneratorTaskConfigurer(task)
            shouldDependOnConfigurer(task)
        }
    }

    private configureIdeaModule(Project project) {
        def task = project.task('ideaModule', description: 'Generates IDEA module files (IML)', type: IdeaModule) {
            conventionMapping.outputFile = { new File(project.projectDir, project.name + ".iml") }
            conventionMapping.moduleDir = { project.projectDir }
            conventionMapping.sourceDirs = { [] as LinkedHashSet }
            conventionMapping.excludeDirs = { [project.buildDir, project.file('.gradle')] as LinkedHashSet }
            conventionMapping.testSourceDirs = { [] as LinkedHashSet }
        }

        addWorker(task)
        configureGeneratorTaskConfigurer(task)
        shouldDependOnConfigurer(task)
    }

    private configureIdeaProject(Project project) {
        if (isRoot(project)) {
            def task = project.task('ideaProject', description: 'Generates IDEA project file (IPR)', type: IdeaProject) {
                outputFile = new File(project.projectDir, project.name + ".ipr")
                subprojects = project.rootProject.allprojects
                javaVersion = JavaVersion.VERSION_1_6.toString()
                wildcards = ['!?*.java', '!?*.groovy']
            }
            addWorker(task)
            configureGeneratorTaskConfigurer(task)
            shouldDependOnConfigurer(task)
        }
    }

    private configureGeneratorTaskConfigurer(task) {
        def generatorTaskConfigurer = task.project.task(task.name + 'Configurer', description: 'Configures the domain object before generation task can act', type: GeneratorTaskConfigurer) {
            configurationTarget = task
        }
        task.dependsOn(generatorTaskConfigurer)
        generatorTaskConfigurer.dependsOn(task.project.rootProject.ideaConfigurer)
    }

    private shouldDependOnConfigurer(Task task) {
        def ideaConfigurer = task.project.rootProject.ideaConfigurer
        task.dependsOn(ideaConfigurer)
        getCleanTask(task).dependsOn(ideaConfigurer)
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
            conventionMapping.sourceDirs = { project.sourceSets.main.allSource.sourceTrees.srcDirs.flatten() as LinkedHashSet }
            conventionMapping.testSourceDirs = { project.sourceSets.test.allSource.sourceTrees.srcDirs.flatten() as LinkedHashSet }
            def configurations = project.configurations
            scopes = [
                    PROVIDED: [plus: [], minus: []],
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

