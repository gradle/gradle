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
package org.gradle.plugins.ide.eclipse;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.gradle.BuildAdapter;
import org.gradle.api.Action;
import org.gradle.api.JavaVersion;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.internal.ConventionMapping;
import org.gradle.api.internal.IConventionAware;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectLocalComponentProvider;
import org.gradle.api.internal.artifacts.publish.DefaultPublishArtifact;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.plugins.GroovyBasePlugin;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.plugins.WarPlugin;
import org.gradle.api.plugins.scala.ScalaBasePlugin;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.internal.component.local.model.LocalComponentArtifactMetadata;
import org.gradle.internal.component.local.model.PublishArtifactLocalArtifactMetadata;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.plugins.ear.EarPlugin;
import org.gradle.plugins.ide.api.XmlFileContentMerger;
import org.gradle.plugins.ide.eclipse.internal.AfterEvaluateHelper;
import org.gradle.plugins.ide.eclipse.internal.EclipseNameDeduper;
import org.gradle.plugins.ide.eclipse.internal.LinkedResourcesCreator;
import org.gradle.plugins.ide.eclipse.model.BuildCommand;
import org.gradle.plugins.ide.eclipse.model.EclipseClasspath;
import org.gradle.plugins.ide.eclipse.model.EclipseJdt;
import org.gradle.plugins.ide.eclipse.model.EclipseModel;
import org.gradle.plugins.ide.eclipse.model.EclipseProject;
import org.gradle.plugins.ide.eclipse.model.Link;
import org.gradle.plugins.ide.internal.IdePlugin;

import javax.inject.Inject;
import java.io.File;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

import static org.gradle.internal.component.local.model.DefaultProjectComponentIdentifier.newProjectId;

/**
 * <p>A plugin which generates Eclipse files.</p>
 */
public class EclipsePlugin extends IdePlugin {

    public static final String ECLIPSE_TASK_NAME = "eclipse";
    public static final String ECLIPSE_PROJECT_TASK_NAME = "eclipseProject";
    public static final String ECLIPSE_CP_TASK_NAME = "eclipseClasspath";
    public static final String ECLIPSE_JDT_TASK_NAME = "eclipseJdt";

    private final Instantiator instantiator;

    @Inject
    public EclipsePlugin(Instantiator instantiator) {
        this.instantiator = instantiator;
    }

    @Override
    protected String getLifecycleTaskName() {
        return ECLIPSE_TASK_NAME;
    }

    @Override
    protected void onApply(Project project) {
        getLifecycleTask().setDescription("Generates all Eclipse files.");
        getCleanTask().setDescription("Cleans all Eclipse files.");

        EclipseModel model = project.getExtensions().create("eclipse", EclipseModel.class);

        configureEclipseProject(project, model);
        configureEclipseJdt(project, model);
        configureEclipseClasspath(project, model);

        postProcess("eclipse", new Action<Gradle>() {
            @Override
            public void execute(Gradle gradle) {
                performPostEvaluationActions();
            }
        });

        applyEclipseWtpPluginOnWebProjects(project);
    }

    public void performPostEvaluationActions() {
        makeSureProjectNamesAreUnique();
        // This needs to happen after de-duplication
        registerEclipseArtifacts();
    }

    private void makeSureProjectNamesAreUnique() {
        new EclipseNameDeduper().configureRoot(project.getRootProject());
    }

    private void registerEclipseArtifacts() {
        Set<Project> projectsWithEclipse = Sets.filter(project.getRootProject().getAllprojects(), HAS_ECLIPSE_PLUGIN);
        for (Project project : projectsWithEclipse) {
            registerEclipseArtifacts(project);
        }
    }

    private static void registerEclipseArtifacts(Project project) {
        ProjectLocalComponentProvider projectComponentProvider = ((ProjectInternal) project).getServices().get(ProjectLocalComponentProvider.class);
        ProjectComponentIdentifier projectId = newProjectId(project);
        String projectName = project.getExtensions().getByType(EclipseModel.class).getProject().getName();
        projectComponentProvider.registerAdditionalArtifact(projectId, createArtifact("project", projectId, projectName, project));
        projectComponentProvider.registerAdditionalArtifact(projectId, createArtifact("classpath", projectId, projectName, project));
    }

    private static LocalComponentArtifactMetadata createArtifact(String extension, ProjectComponentIdentifier projectId, String projectName, Project project) {
        File projectFile = new File(project.getProjectDir(), "." + extension);
        Task byName = project.getTasks().getByName("eclipseProject");
        String type = "eclipse." + extension;
        PublishArtifact publishArtifact = new DefaultPublishArtifact(projectName, extension, type, null, null, projectFile, byName);
        return new PublishArtifactLocalArtifactMetadata(projectId, publishArtifact);
    }

    private void configureEclipseProject(final Project project, final EclipseModel model) {
        maybeAddTask(project, this, ECLIPSE_PROJECT_TASK_NAME, GenerateEclipseProject.class, new Action<GenerateEclipseProject>() {
            @Override
            public void execute(GenerateEclipseProject task) {
                final EclipseProject projectModel = task.getProjectModel();

                //task properties:
                task.setDescription("Generates the Eclipse project file.");
                task.setInputFile(project.file(".project"));
                task.setOutputFile(project.file(".project"));

                //model:
                model.setProject(projectModel);
                projectModel.setName(project.getName());

                final ConventionMapping convention = ((IConventionAware) projectModel).getConventionMapping();
                convention.map("comment", new Callable<String>() {
                    @Override
                    public String call() {
                        return project.getDescription();
                    }

                });

                project.getPlugins().withType(JavaBasePlugin.class, new Action<JavaBasePlugin>() {
                    @Override
                    public void execute(JavaBasePlugin javaBasePlugin) {
                        if (!project.getPlugins().hasPlugin(EarPlugin.class)) {
                            projectModel.buildCommand("org.eclipse.jdt.core.javabuilder");
                        }

                        projectModel.natures("org.eclipse.jdt.core.javanature");
                        convention.map("linkedResources", new Callable<Set<Link>>() {
                            @Override
                            public Set<Link> call() {
                                return new LinkedResourcesCreator().links(project);
                            }

                        });
                    }

                });

                project.getPlugins().withType(GroovyBasePlugin.class, new Action<GroovyBasePlugin>() {
                    @Override
                    public void execute(GroovyBasePlugin groovyBasePlugin) {
                        projectModel.getNatures().add(projectModel.getNatures().indexOf("org.eclipse.jdt.core.javanature"), "org.eclipse.jdt.groovy.core.groovyNature");
                    }

                });

                project.getPlugins().withType(ScalaBasePlugin.class, new Action<ScalaBasePlugin>() {
                    @Override
                    public void execute(ScalaBasePlugin scalaBasePlugin) {
                        projectModel.getBuildCommands().set(Iterables.indexOf(projectModel.getBuildCommands(), new Predicate<BuildCommand>() {
                            @Override
                            public boolean apply(BuildCommand buildCommand) {
                                return buildCommand.getName().equals("org.eclipse.jdt.core.javabuilder");
                            }

                        }), new BuildCommand("org.scala-ide.sdt.core.scalabuilder"));
                        projectModel.getNatures().add(projectModel.getNatures().indexOf("org.eclipse.jdt.core.javanature"), "org.scala-ide.sdt.core.scalanature");
                    }

                });
            }

        });
    }

    private void configureEclipseClasspath(final Project project, final EclipseModel model) {
        model.setClasspath(instantiator.newInstance(EclipseClasspath.class, project));
        ((IConventionAware) model.getClasspath()).getConventionMapping().map("defaultOutputDir", new Callable<File>() {
            @Override
            public File call() {
                return new File(project.getProjectDir(), "bin");
            }

        });

        final EclipsePlugin eclipsePlugin = this;
        project.getPlugins().withType(JavaBasePlugin.class, new Action<JavaBasePlugin>() {
            @Override
            public void execute(JavaBasePlugin javaBasePlugin) {
                maybeAddTask(project, eclipsePlugin, ECLIPSE_CP_TASK_NAME, GenerateEclipseClasspath.class, new Action<GenerateEclipseClasspath>() {
                    @Override
                    public void execute(final GenerateEclipseClasspath task) {
                        //task properties:
                        task.setDescription("Generates the Eclipse classpath file.");
                        task.setInputFile(project.file(".classpath"));
                        task.setOutputFile(project.file(".classpath"));

                        //model properties:
                        task.setClasspath(model.getClasspath());
                        task.getClasspath().setFile(new XmlFileContentMerger(task.getXmlTransformer()));
                        task.getClasspath().setSourceSets(project.getConvention().getPlugin(JavaPluginConvention.class).getSourceSets());

                        AfterEvaluateHelper.afterEvaluateOrExecute(project, new Action<Project>() {
                            @Override
                            public void execute(Project p) {
                                // keep the ordering we had in earlier gradle versions
                                Set<String> containers = Sets.newLinkedHashSet();
                                containers.add("org.eclipse.jdt.launching.JRE_CONTAINER/org.eclipse.jdt.internal.debug.ui.launcher.StandardVMType/" + model.getJdt().getJavaRuntimeName() + "/");
                                containers.addAll(task.getClasspath().getContainers());
                                task.getClasspath().setContainers(containers);
                            }

                        });

                        project.getPlugins().withType(JavaPlugin.class, new Action<JavaPlugin>() {
                            @Override
                            public void execute(JavaPlugin javaPlugin) {
                                configureJavaClasspath(project, task);
                            }

                        });

                        configureScalaDependencies(project, task);
                    }

                });
            }

        });
    }

    private static void configureJavaClasspath(final Project project, GenerateEclipseClasspath task) {
        task.getClasspath().setPlusConfigurations(Lists.newArrayList(project.getConfigurations().getByName("compileClasspath"), project.getConfigurations().getByName("runtimeClasspath"), project.getConfigurations().getByName("testCompileClasspath"), project.getConfigurations().getByName("testRuntimeClasspath")));
        ((IConventionAware) task.getClasspath()).getConventionMapping().map("classFolders", new Callable<List<File>>() {
            @Override
            public List<File> call() {
                SourceSetContainer sourceSets = project.getConvention().getPlugin(JavaPluginConvention.class).getSourceSets();
                return Lists.newArrayList(Iterables.concat(sourceSets.getByName("main").getOutput().getDirs(), sourceSets.getByName("test").getOutput().getDirs()));
            }

        });
        SourceSetContainer sourceSets = project.getConvention().getPlugin(JavaPluginConvention.class).getSourceSets();
        task.dependsOn(sourceSets.getByName("main").getOutput().getDirs());
        task.dependsOn(sourceSets.getByName("test").getOutput().getDirs());
    }

    private static void configureScalaDependencies(final Project project, final GenerateEclipseClasspath task) {
        project.getPlugins().withType(ScalaBasePlugin.class, new Action<ScalaBasePlugin>() {
            @Override
            public void execute(ScalaBasePlugin scalaBasePlugin) {
                task.getClasspath().containers("org.scala-ide.sdt.launching.SCALA_CONTAINER");

                // exclude the dependencies already provided by SCALA_CONTAINER; prevents problems with Eclipse Scala plugin
                project.getGradle().addBuildListener(new BuildAdapter() {
                    @Override
                    public void projectsEvaluated(Gradle gradle) {
                        final List<String> provided = Lists.newArrayList("scala-library", "scala-swing", "scala-dbc");
                        Predicate<Dependency> dependencyInProvided = new Predicate<Dependency>() {
                            @Override
                            public boolean apply(Dependency dependency) {
                                return provided.contains(dependency.getName());
                            }

                        };
                        List<Dependency> dependencies = Lists.newArrayList(Iterables.filter(Iterables.concat(Iterables.transform(task.getClasspath().getPlusConfigurations(), new Function<Configuration, Iterable<Dependency>>() {
                            @Override
                            public Iterable<Dependency> apply(Configuration config) {
                                return config.getAllDependencies();
                            }

                        })), dependencyInProvided));
                        if (!dependencies.isEmpty()) {
                            task.getClasspath().getMinusConfigurations().add(project.getConfigurations().detachedConfiguration(dependencies.toArray(new Dependency[0])));
                        }
                    }
                });
            }

        });
    }

    private void configureEclipseJdt(final Project project, final EclipseModel model) {
        final EclipsePlugin eclipsePlugin = this;
        project.getPlugins().withType(JavaBasePlugin.class, new Action<JavaBasePlugin>() {
            @Override
            public void execute(JavaBasePlugin javaBasePlugin) {
                maybeAddTask(project, eclipsePlugin, ECLIPSE_JDT_TASK_NAME, GenerateEclipseJdt.class, new Action<GenerateEclipseJdt>() {
                    @Override
                    public void execute(GenerateEclipseJdt task) {
                        //task properties:
                        task.setDescription("Generates the Eclipse JDT settings file.");
                        task.setOutputFile(project.file(".settings/org.eclipse.jdt.core.prefs"));
                        task.setInputFile(project.file(".settings/org.eclipse.jdt.core.prefs"));
                        //model properties:
                        EclipseJdt jdt = task.getJdt();
                        model.setJdt(jdt);
                        ConventionMapping conventionMapping = ((IConventionAware) jdt).getConventionMapping();
                        conventionMapping.map("sourceCompatibility", new Callable<JavaVersion>() {
                            @Override
                            public JavaVersion call() {
                                return project.getConvention().getPlugin(JavaPluginConvention.class).getSourceCompatibility();
                            }

                        });
                        conventionMapping.map("targetCompatibility", new Callable<JavaVersion>() {
                            @Override
                            public JavaVersion call() {
                                return project.getConvention().getPlugin(JavaPluginConvention.class).getTargetCompatibility();
                            }

                        });
                        conventionMapping.map("javaRuntimeName", new Callable<String>() {
                            @Override
                            public String call() {
                                return "JavaSE-" + project.getConvention().getPlugin(JavaPluginConvention.class).getTargetCompatibility();
                            }

                        });
                    }

                });
            }

        });
    }

    private void applyEclipseWtpPluginOnWebProjects(Project project) {
        Action<Plugin<Project>> action = createActionApplyingEclipseWtpPlugin();
        project.getPlugins().withType(WarPlugin.class, action);
        project.getPlugins().withType(EarPlugin.class, action);
    }

    private Action<Plugin<Project>> createActionApplyingEclipseWtpPlugin() {
        return new Action<Plugin<Project>>() {
            @Override
            public void execute(Plugin<Project> plugin) {
                project.getPluginManager().apply(EclipseWtpPlugin.class);
            }

        };
    }

    private static <T extends Task> void maybeAddTask(Project project, IdePlugin plugin, String taskName, Class<T> taskType, Action<T> action) {
        TaskContainer tasks = project.getTasks();
        if (tasks.findByName(taskName) != null) {
            return;
        }

        T task = tasks.create(taskName, taskType);
        action.execute(task);
        plugin.addWorker(task);
    }

    private static final Predicate<Project> HAS_ECLIPSE_PLUGIN = new Predicate<Project>() {
        @Override
        public boolean apply(Project project) {
            return project.getPlugins().hasPlugin(EclipsePlugin.class);
        }
    };
}
