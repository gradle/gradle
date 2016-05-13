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

import com.google.common.base.Function
import com.google.common.base.Predicate
import com.google.common.collect.Iterables
import com.google.common.collect.Lists
import com.google.common.collect.Sets
import groovy.transform.CompileStatic
import org.gradle.api.Action
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.internal.ConventionMapping
import org.gradle.api.internal.IConventionAware
import org.gradle.api.plugins.GroovyBasePlugin
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.plugins.scala.ScalaBasePlugin
import org.gradle.api.tasks.SourceSetContainer
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
import org.gradle.plugins.ide.eclipse.model.Link
import org.gradle.plugins.ide.internal.IdePlugin

import javax.inject.Inject
import java.util.concurrent.Callable
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
            project.gradle.projectsEvaluated(new Closure(this, this) {
                public void doCall() {
                    makeSureProjectNamesAreUnique()
                }
            })
        }
    }

    public void makeSureProjectNamesAreUnique() {
        new EclipseNameDeduper().configureRoot(project.rootProject);
    }

    private void configureEclipseProject(Project project, EclipseModel model) {
        maybeAddTask(project, this, ECLIPSE_PROJECT_TASK_NAME, GenerateEclipseProject, new Action<GenerateEclipseProject>() {
            @Override
            void execute(GenerateEclipseProject task) {
                EclipseProject projectModel = task.projectModel

                //task properties:
                task.description = "Generates the Eclipse project file."
                task.inputFile = project.file('.project')
                task.outputFile = project.file('.project')

                //model:
                model.project = projectModel
                projectModel.name = project.name

                ConventionMapping convention = ((IConventionAware) projectModel).conventionMapping
                convention.map('comment', new Callable<String>() {
                    @Override
                    String call() {
                        return project.description
                    }
                })

                project.plugins.withType(JavaBasePlugin, new Action<JavaBasePlugin>() {
                    @Override
                    void execute(JavaBasePlugin javaBasePlugin) {
                        if (!project.plugins.hasPlugin(EarPlugin)) {
                            projectModel.buildCommand "org.eclipse.jdt.core.javabuilder"
                        }
                        projectModel.natures "org.eclipse.jdt.core.javanature"
                        convention.map('linkedResources', new Callable<Set<Link>>() {
                            @Override
                            Set<Link> call() {
                                return new LinkedResourcesCreator().links(project);
                            }
                        })
                    }
                })

                project.plugins.withType(GroovyBasePlugin, new Action<GroovyBasePlugin>() {
                    @Override
                    void execute(GroovyBasePlugin groovyBasePlugin) {
                        projectModel.natures.add(projectModel.natures.indexOf("org.eclipse.jdt.core.javanature"), "org.eclipse.jdt.groovy.core.groovyNature")
                    }
                })

                project.plugins.withType(ScalaBasePlugin, new Action<ScalaBasePlugin>() {
                    @Override
                    void execute(ScalaBasePlugin scalaBasePlugin) {
                        projectModel.buildCommands.set(Iterables.indexOf(projectModel.buildCommands, new Predicate<BuildCommand>() {
                            @Override
                            boolean apply(BuildCommand buildCommand) {
                                return buildCommand.name == "org.eclipse.jdt.core.javabuilder"
                            }
                        }), new BuildCommand("org.scala-ide.sdt.core.scalabuilder"))
                        projectModel.natures.add(projectModel.natures.indexOf("org.eclipse.jdt.core.javanature"), "org.scala-ide.sdt.core.scalanature")
                    }
                })
            }
        })
    }

    private void configureEclipseClasspath(Project project, EclipseModel model) {
        model.classpath = instantiator.newInstance(EclipseClasspath, project)
        ((IConventionAware) model.classpath).conventionMapping.map('defaultOutputDir', new Callable<File>() {
            @Override
            File call() {
                return new File(project.projectDir, 'bin');
            }
        })

        EclipsePlugin eclipsePlugin = this;
        project.plugins.withType(JavaBasePlugin, new Action<JavaBasePlugin>() {
            @Override
            void execute(JavaBasePlugin javaBasePlugin) {
                maybeAddTask(project, eclipsePlugin, ECLIPSE_CP_TASK_NAME, GenerateEclipseClasspath, new Action<GenerateEclipseClasspath>() {
                    @Override
                    void execute(GenerateEclipseClasspath task) {
                        //task properties:
                        task.description = "Generates the Eclipse classpath file."
                        task.inputFile = project.file('.classpath')
                        task.outputFile = project.file('.classpath')

                        //model properties:
                        task.classpath = model.classpath
                        task.classpath.file = new XmlFileContentMerger((XmlTransformer) task.getProperty('xmlTransformer'))
                        task.classpath.sourceSets = project.convention.getPlugin(JavaPluginConvention).sourceSets

                        project.afterEvaluate(new Action<Project>() {
                            @Override
                            void execute(Project p) {
                                // keep the ordering we had in earlier gradle versions
                                Set<String> containers = Sets.newLinkedHashSet();
                                containers.add("org.eclipse.jdt.launching.JRE_CONTAINER/org.eclipse.jdt.internal.debug.ui.launcher.StandardVMType/" + model.jdt.getJavaRuntimeName() + "/".toString())
                                containers.addAll(task.classpath.containers)
                                task.classpath.containers = containers
                            }
                        })

                        project.plugins.withType(JavaPlugin, new Action<JavaPlugin>() {
                            @Override
                            void execute(JavaPlugin javaPlugin) {
                                configureJavaClasspath(project, task)
                            }
                        })

                        configureScalaDependencies(project, task)
                    }
                });
            }
        })
    }

    private static void configureJavaClasspath(Project project, GenerateEclipseClasspath task) {
        task.classpath.plusConfigurations = Lists.newArrayList(
            project.configurations.getByName("testRuntime"),
            project.configurations.getByName("compileClasspath"),
            project.configurations.getByName("testCompileClasspath")
        )
        ((IConventionAware) task.classpath).conventionMapping.map('classFolders', new Callable<List<File>>() {
            @Override
            List<File> call() {
                SourceSetContainer sourceSets = project.convention.getPlugin(JavaPluginConvention).sourceSets;
                return Lists.newArrayList(Iterables.concat(
                    sourceSets.getByName("main").output.dirs,
                    sourceSets.getByName("test").output.dirs
                ));
            }
        });
        SourceSetContainer sourceSets = project.convention.getPlugin(JavaPluginConvention).sourceSets;
        task.dependsOn(sourceSets.getByName("main").output.dirs)
        task.dependsOn(sourceSets.getByName("test").output.dirs)
    }

    private static void configureScalaDependencies(Project project, GenerateEclipseClasspath task) {
        project.plugins.withType(ScalaBasePlugin, new Action<ScalaBasePlugin>() {
            @Override
            void execute(ScalaBasePlugin scalaBasePlugin) {
                task.classpath.containers 'org.scala-ide.sdt.launching.SCALA_CONTAINER'

                // exclude the dependencies already provided by SCALA_CONTAINER; prevents problems with Eclipse Scala plugin
                project.gradle.projectsEvaluated(new Closure(this, this) {
                    public void doCall() {
                        List<String> provided = Lists.newArrayList("scala-library", "scala-swing", "scala-dbc")
                        Predicate<Dependency> dependencyInProvided = new Predicate<Dependency>() {
                            @Override
                            boolean apply(Dependency dependency) {
                                return provided.contains(dependency.name)
                            }
                        };
                        List<Dependency> dependencies = Lists.newArrayList(Iterables.filter(Iterables.concat(
                            Iterables.transform(task.classpath.plusConfigurations, new Function<Configuration, Iterable<Dependency>>() {
                                @Override
                                Iterable<Dependency> apply(Configuration config) {
                                    return config.allDependencies
                                }
                            })
                        ), dependencyInProvided))
                        if (!dependencies.empty) {
                            task.classpath.minusConfigurations.add(
                                project.configurations.detachedConfiguration(dependencies.toArray(new Dependency[dependencies.size()]))
                            )
                        }
                    }
                })
            }
        })
    }

    private void configureEclipseJdt(Project project, EclipseModel model) {
        EclipsePlugin eclipsePlugin = this;
        project.plugins.withType(JavaBasePlugin, new Action<JavaBasePlugin>() {
            @Override
            void execute(JavaBasePlugin javaBasePlugin) {
                maybeAddTask(project, eclipsePlugin, ECLIPSE_JDT_TASK_NAME, GenerateEclipseJdt, new Action<GenerateEclipseJdt>() {
                    @Override
                    void execute(GenerateEclipseJdt task) {
                        //task properties:
                        task.description = "Generates the Eclipse JDT settings file."
                        task.outputFile = project.file('.settings/org.eclipse.jdt.core.prefs')
                        task.inputFile = project.file('.settings/org.eclipse.jdt.core.prefs')
                        //model properties:
                        def jdt = task.jdt
                        model.jdt = jdt
                        def conventionMapping = ((IConventionAware) jdt).conventionMapping
                        conventionMapping.map('sourceCompatibility', new Callable<JavaVersion>() {
                            @Override
                            JavaVersion call() {
                                return project.convention.getPlugin(JavaPluginConvention).sourceCompatibility
                            }
                        })
                        conventionMapping.map('targetCompatibility', new Callable<JavaVersion>() {
                            @Override
                            JavaVersion call() {
                                return project.convention.getPlugin(JavaPluginConvention).targetCompatibility
                            }
                        })
                        conventionMapping.map('javaRuntimeName', new Callable<String>() {
                            @Override
                            String call() {
                                return "JavaSE-" + project.convention.getPlugin(JavaPluginConvention).targetCompatibility
                            }
                        })
                    }
                })
            }
        })
    }

    private static <T extends Task> void maybeAddTask(Project project, IdePlugin plugin, String taskName,
                                                      Class<T> taskType, Action<T> action) {
        TaskContainer tasks = project.tasks
        if (tasks.findByName(taskName) != null) {
            return
        }
        def task = tasks.create(taskName, taskType)
        project.configure(Arrays.asList(task), action)
        plugin.addWorker(task)
    }
}
