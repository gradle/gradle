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
package org.gradle.plugins.ide.eclipse

import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.plugins.GroovyBasePlugin
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.scala.ScalaBasePlugin
import org.gradle.internal.reflect.Instantiator
import org.gradle.plugins.ear.EarPlugin
import org.gradle.plugins.ide.api.XmlFileContentMerger
import org.gradle.plugins.ide.eclipse.internal.EclipseNameDeduper
import org.gradle.plugins.ide.eclipse.internal.LinkedResourcesCreator
import org.gradle.plugins.ide.eclipse.model.BuildCommand
import org.gradle.plugins.ide.eclipse.model.EclipseClasspath
import org.gradle.plugins.ide.eclipse.model.EclipseModel
import org.gradle.plugins.ide.internal.IdePlugin

import javax.inject.Inject

/**
 * <p>A plugin which generates Eclipse files.</p>
 */
class EclipsePlugin extends IdePlugin {
    static final String ECLIPSE_TASK_NAME = "eclipse"
    static final String ECLIPSE_PROJECT_TASK_NAME = "eclipseProject"
    static final String ECLIPSE_CP_TASK_NAME = "eclipseClasspath"
    static final String ECLIPSE_JDT_TASK_NAME = "eclipseJdt"

    private final Instantiator instantiator
    EclipseModel model

    @Inject
    EclipsePlugin(Instantiator instantiator) {
        this.instantiator = instantiator
    }

    @Override protected String getLifecycleTaskName() {
        return 'eclipse'
    }

    @Override protected void onApply(Project project) {
        lifecycleTask.description = 'Generates all Eclipse files.'
        cleanTask.description = 'Cleans all Eclipse files.'

        model = project.extensions.create("eclipse", EclipseModel)

        configureEclipseProject(project)
        configureEclipseClasspath(project)
        configureEclipseJdt(project)

        hookDeduplicationToTheRoot(project)
    }

    void hookDeduplicationToTheRoot(Project project) {
        if (project.parent == null) {
            project.gradle.projectsEvaluated {
                makeSureProjectNamesAreUnique()
            }
        }
    }

    public void makeSureProjectNamesAreUnique() {
        new EclipseNameDeduper().configureRoot(project.rootProject);
    }

    private void configureEclipseProject(Project project) {
        maybeAddTask(project, this, ECLIPSE_PROJECT_TASK_NAME, GenerateEclipseProject) {
            //task properties:
            description = "Generates the Eclipse project file."
            inputFile = project.file('.project')
            outputFile = project.file('.project')

            //model:
            model.project = projectModel

            projectModel.name = project.name
            projectModel.conventionMapping.comment = { project.description }

            project.plugins.withType(JavaBasePlugin) {
                if (!project.plugins.hasPlugin(EarPlugin)) {
                    projectModel.buildCommand "org.eclipse.jdt.core.javabuilder"
                }
                projectModel.natures "org.eclipse.jdt.core.javanature"
                projectModel.conventionMapping.linkedResources = {
                    new LinkedResourcesCreator().links(project)
                }
            }

            project.plugins.withType(GroovyBasePlugin) {
                projectModel.natures.add(projectModel.natures.indexOf("org.eclipse.jdt.core.javanature"), "org.eclipse.jdt.groovy.core.groovyNature")
            }

            project.plugins.withType(ScalaBasePlugin) {
                projectModel.buildCommands.set(projectModel.buildCommands.findIndexOf { it.name == "org.eclipse.jdt.core.javabuilder" },
                        new BuildCommand("org.scala-ide.sdt.core.scalabuilder"))
                projectModel.natures.add(projectModel.natures.indexOf("org.eclipse.jdt.core.javanature"), "org.scala-ide.sdt.core.scalanature")
            }
        }
    }

    private void configureEclipseClasspath(Project project) {
        model.classpath = instantiator.newInstance(EclipseClasspath, project)
        model.classpath.conventionMapping.defaultOutputDir = { new File(project.projectDir, 'bin') }

        project.plugins.withType(JavaBasePlugin) {
            maybeAddTask(project, this, ECLIPSE_CP_TASK_NAME, GenerateEclipseClasspath) { task ->
                //task properties:
                description = "Generates the Eclipse classpath file."
                inputFile = project.file('.classpath')
                outputFile = project.file('.classpath')

                //model properties:
                classpath = model.classpath
                classpath.file = new XmlFileContentMerger(xmlTransformer)

                classpath.sourceSets = project.sourceSets

                classpath.containers 'org.eclipse.jdt.launching.JRE_CONTAINER'

                project.plugins.withType(JavaPlugin) {
                    classpath.plusConfigurations = [project.configurations.testRuntime]
                    classpath.conventionMapping.classFolders = {
                        return (project.sourceSets.main.output.dirs + project.sourceSets.test.output.dirs) as List
                    }
                    task.dependsOn {
                        project.sourceSets.main.output.dirs + project.sourceSets.test.output.dirs
                    }
                }

                project.plugins.withType(ScalaBasePlugin) {
                    classpath.containers 'org.scala-ide.sdt.launching.SCALA_CONTAINER'

                    // exclude the dependencies already provided by SCALA_CONTAINER; prevents problems with Eclipse Scala plugin
                    project.gradle.projectsEvaluated {
                        def provided = ["scala-library", "scala-swing", "scala-dbc"]
                        def dependencies = classpath.plusConfigurations.collectMany { it.allDependencies }.findAll { it.name in provided }
                        if (!dependencies.empty) {
                            classpath.minusConfigurations += project.configurations.detachedConfiguration(dependencies as Dependency[])
                        }
                    }
                }
            }
        }
    }

    private void configureEclipseJdt(Project project) {
        project.plugins.withType(JavaBasePlugin) {
            maybeAddTask(project, this, ECLIPSE_JDT_TASK_NAME, GenerateEclipseJdt) {
                //task properties:
                description = "Generates the Eclipse JDT settings file."
                outputFile = project.file('.settings/org.eclipse.jdt.core.prefs')
                inputFile = project.file('.settings/org.eclipse.jdt.core.prefs')
                //model properties:
                model.jdt = jdt
                jdt.conventionMapping.sourceCompatibility = { project.sourceCompatibility }
                jdt.conventionMapping.targetCompatibility = { project.targetCompatibility }
            }
        }
    }

    private void maybeAddTask(Project project, IdePlugin plugin, String taskName, Class taskType, Closure action) {
        if (project.tasks.findByName(taskName)) { return }
        def task = project.tasks.create(taskName, taskType)
        project.configure(task, action)
        plugin.addWorker(task)
    }
}
