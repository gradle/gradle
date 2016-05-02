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

import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode
import org.codehaus.groovy.runtime.InvokerHelper
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Dependency
import org.gradle.api.internal.ConventionMapping
import org.gradle.api.plugins.GroovyBasePlugin
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.plugins.scala.ScalaBasePlugin
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskContainer
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.xml.XmlTransformer
import org.gradle.plugins.ear.EarPlugin
import org.gradle.plugins.ide.api.XmlFileContentMerger
import org.gradle.plugins.ide.eclipse.internal.EclipseNameDeduper
import org.gradle.plugins.ide.eclipse.internal.LinkedResourcesCreator
import org.gradle.plugins.ide.eclipse.model.BuildCommand
import org.gradle.plugins.ide.eclipse.model.EclipseClasspath
import org.gradle.plugins.ide.eclipse.model.EclipseModel
import org.gradle.plugins.ide.eclipse.model.EclipseProject
import org.gradle.plugins.ide.internal.IdePlugin

import javax.inject.Inject
/**
 * <p>A plugin which generates Eclipse files.</p>
 */
@CompileStatic
class EclipsePlugin extends IdePlugin {
    static final String ECLIPSE_TASK_NAME = "eclipse"
    static final String ECLIPSE_PROJECT_TASK_NAME = "eclipseProject"
    static final String ECLIPSE_CP_TASK_NAME = "eclipseClasspath"
    static final String ECLIPSE_JDT_TASK_NAME = "eclipseJdt"

    private final Instantiator instantiator

    @Inject
    EclipsePlugin(Instantiator instantiator) {
        this.instantiator = instantiator
    }

    @Override
    protected String getLifecycleTaskName() {
        return ECLIPSE_TASK_NAME
    }

    @Override
    protected void onApply(Project project) {
        lifecycleTask.description = 'Generates all Eclipse files.'
        cleanTask.description = 'Cleans all Eclipse files.'

        EclipseModel model = (EclipseModel) project.extensions.create("eclipse", EclipseModel)

        configureEclipseProject(project, model)
        configureEclipseJdt(project, model)
        configureEclipseClasspath(project, model)

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

    private void configureEclipseProject(Project project, EclipseModel model) {
        maybeAddTask(project, this, ECLIPSE_PROJECT_TASK_NAME, GenerateEclipseProject) { GenerateEclipseProject task ->
            EclipseProject projectModel = task.projectModel

            //task properties:
            task.description = "Generates the Eclipse project file."
            task.inputFile = project.file('.project')
            task.outputFile = project.file('.project')

            //model:
            model.project = projectModel

            projectModel.name = project.name

            ConventionMapping convention = conventionMappingFor(projectModel)
            convention.map('comment') { project.description }

            project.plugins.withType(JavaBasePlugin) {
                if (!project.plugins.hasPlugin(EarPlugin)) {
                    projectModel.buildCommand "org.eclipse.jdt.core.javabuilder"
                }
                projectModel.natures "org.eclipse.jdt.core.javanature"
                convention.map('linkedResources') {
                    new LinkedResourcesCreator().links(project)
                }
            }

            project.plugins.withType(GroovyBasePlugin) {
                projectModel.natures.add(projectModel.natures.indexOf("org.eclipse.jdt.core.javanature"), "org.eclipse.jdt.groovy.core.groovyNature")
            }

            project.plugins.withType(ScalaBasePlugin) {
                projectModel.buildCommands.set(projectModel.buildCommands.findIndexOf { ((BuildCommand) it).name == "org.eclipse.jdt.core.javabuilder" },
                    new BuildCommand("org.scala-ide.sdt.core.scalabuilder"))
                projectModel.natures.add(projectModel.natures.indexOf("org.eclipse.jdt.core.javanature"), "org.scala-ide.sdt.core.scalanature")
            }
        }
    }

    private void configureEclipseClasspath(Project project, EclipseModel model) {
        model.classpath = instantiator.newInstance(EclipseClasspath, project)
        conventionMappingFor(model.classpath).map('defaultOutputDir') { new File(project.projectDir, 'bin') }

        project.plugins.withType(JavaBasePlugin) {
            maybeAddTask(project, this, ECLIPSE_CP_TASK_NAME, GenerateEclipseClasspath) { GenerateEclipseClasspath task ->
                //task properties:
                task.description = "Generates the Eclipse classpath file."
                task.inputFile = project.file('.classpath')
                task.outputFile = project.file('.classpath')

                //model properties:
                task.classpath = model.classpath
                task.classpath.file = new XmlFileContentMerger((XmlTransformer) task.getProperty('xmlTransformer'))

                task.classpath.sourceSets = (Iterable<SourceSet>) InvokerHelper.getProperty(project, 'sourceSets')

                project.afterEvaluate {
                    // keep the ordering we had in earlier gradle versions
                    Set<String> containers = new LinkedHashSet<String>()
                    containers.add("org.eclipse.jdt.launching.JRE_CONTAINER/org.eclipse.jdt.internal.debug.ui.launcher.StandardVMType/${model.jdt.getJavaRuntimeName()}/".toString())
                    containers.addAll(task.classpath.containers)
                    task.classpath.containers = containers
                }

                project.plugins.withType(JavaPlugin) {
                    configureJavaClasspath(task)
                }

                configureScalaDependencies(task)
            }
        }
    }

    @CompileStatic(TypeCheckingMode.SKIP)
    private void configureJavaClasspath(GenerateEclipseClasspath task) {
        task.classpath.plusConfigurations = [project.configurations.testRuntime, project.configurations.compileClasspath, project.configurations.testCompileClasspath]
        task.classpath.conventionMapping.classFolders = {
            return (project.sourceSets.main.output.dirs + project.sourceSets.test.output.dirs) as List
        }
        task.dependsOn {
            project.sourceSets.main.output.dirs + project.sourceSets.test.output.dirs
        }
    }


    @CompileStatic(TypeCheckingMode.SKIP)
    private void configureScalaDependencies(task) {
        project.plugins.withType(ScalaBasePlugin) {
            task.classpath.containers 'org.scala-ide.sdt.launching.SCALA_CONTAINER'

            // exclude the dependencies already provided by SCALA_CONTAINER; prevents problems with Eclipse Scala plugin
            project.gradle.projectsEvaluated {
                def provided = ["scala-library", "scala-swing", "scala-dbc"]
                def dependencies = task.classpath.plusConfigurations.collectMany { it.allDependencies }.findAll { it.name in provided }
                if (!dependencies.empty) {
                    task.classpath.minusConfigurations << project.configurations.detachedConfiguration(dependencies as Dependency[])
                }
            }
        }
    }

    private void configureEclipseJdt(Project project, EclipseModel model) {
        project.plugins.withType(JavaBasePlugin) {
            maybeAddTask(project, this, ECLIPSE_JDT_TASK_NAME, GenerateEclipseJdt) { GenerateEclipseJdt task ->
                //task properties:
                task.description = "Generates the Eclipse JDT settings file."
                task.outputFile = project.file('.settings/org.eclipse.jdt.core.prefs')
                task.inputFile = project.file('.settings/org.eclipse.jdt.core.prefs')
                //model properties:
                def jdt = task.jdt
                model.jdt = jdt
                def conventionMapping = conventionMappingFor(jdt)
                conventionMapping.map('sourceCompatibility') { project.convention.getPlugin(JavaPluginConvention).sourceCompatibility }
                conventionMapping.map('targetCompatibility'){ project.convention.getPlugin(JavaPluginConvention).targetCompatibility }
                conventionMapping.map('javaRuntimeName') { String.format("JavaSE-%s", project.convention.getPlugin(JavaPluginConvention).targetCompatibility) }
            }
        }
    }

    private static <T extends Task> void maybeAddTask(Project project, IdePlugin plugin, String taskName, Class<T> taskType,
                                                     @DelegatesTo(strategy = Closure.DELEGATE_FIRST, type = "T") Closure action) {
        TaskContainer tasks = project.tasks
        if (tasks.findByName(taskName) != null) {
            return
        }
        def task = tasks.create(taskName, taskType)
        project.configure(task, action)
        plugin.addWorker(task)
    }
}
