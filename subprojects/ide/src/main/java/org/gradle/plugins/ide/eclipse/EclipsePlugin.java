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
import org.gradle.api.Action;
import org.gradle.api.JavaVersion;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.internal.ConventionMapping;
import org.gradle.api.internal.IConventionAware;
import org.gradle.api.internal.PropertiesTransformer;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.plugins.GroovyBasePlugin;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.plugins.JavaTestFixturesPlugin;
import org.gradle.api.plugins.WarPlugin;
import org.gradle.api.plugins.jvm.internal.JvmEcosystemUtilities;
import org.gradle.api.plugins.scala.ScalaBasePlugin;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.internal.xml.XmlTransformer;
import org.gradle.plugins.ear.EarPlugin;
import org.gradle.plugins.ide.api.PropertiesFileContentMerger;
import org.gradle.plugins.ide.api.XmlFileContentMerger;
import org.gradle.plugins.ide.eclipse.internal.AfterEvaluateHelper;
import org.gradle.plugins.ide.eclipse.internal.EclipsePluginConstants;
import org.gradle.plugins.ide.eclipse.internal.EclipseProjectMetadata;
import org.gradle.plugins.ide.eclipse.internal.LinkedResourcesCreator;
import org.gradle.plugins.ide.eclipse.model.BuildCommand;
import org.gradle.plugins.ide.eclipse.model.EclipseClasspath;
import org.gradle.plugins.ide.eclipse.model.EclipseJdt;
import org.gradle.plugins.ide.eclipse.model.EclipseModel;
import org.gradle.plugins.ide.eclipse.model.EclipseProject;
import org.gradle.plugins.ide.eclipse.model.Link;
import org.gradle.plugins.ide.eclipse.model.internal.EclipseJavaVersionMapper;
import org.gradle.plugins.ide.internal.IdeArtifactRegistry;
import org.gradle.plugins.ide.internal.IdePlugin;
import org.gradle.plugins.ide.internal.configurer.UniqueProjectNameProvider;

import javax.inject.Inject;
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * <p>A plugin which generates Eclipse files.</p>
 *
 * @see <a href="https://docs.gradle.org/current/userguide/eclipse_plugin.html">Eclipse plugin reference</a>
 */
public class EclipsePlugin extends IdePlugin {

    public static final String ECLIPSE_TASK_NAME = "eclipse";
    public static final String ECLIPSE_PROJECT_TASK_NAME = "eclipseProject";
    public static final String ECLIPSE_CP_TASK_NAME = "eclipseClasspath";
    public static final String ECLIPSE_JDT_TASK_NAME = "eclipseJdt";

    private final UniqueProjectNameProvider uniqueProjectNameProvider;
    private final IdeArtifactRegistry artifactRegistry;
    private final JvmEcosystemUtilities jvmEcosystemUtilities;

    @Inject
    public EclipsePlugin(UniqueProjectNameProvider uniqueProjectNameProvider,
                         IdeArtifactRegistry artifactRegistry,
                         JvmEcosystemUtilities jvmEcosystemUtilities) {
        this.uniqueProjectNameProvider = uniqueProjectNameProvider;
        this.artifactRegistry = artifactRegistry;
        this.jvmEcosystemUtilities = jvmEcosystemUtilities;
    }

    @Override
    protected String getLifecycleTaskName() {
        return ECLIPSE_TASK_NAME;
    }

    @Override
    protected void onApply(Project project) {
        getLifecycleTask().configure(withDescription("Generates all Eclipse files."));
        getCleanTask().configure(withDescription("Cleans all Eclipse files."));

        EclipseModel model = project.getExtensions().create("eclipse", EclipseModel.class, project);

        configureEclipseProject((ProjectInternal) project, model);
        configureEclipseJdt(project, model);
        configureEclipseClasspath(project, model);

        applyEclipseWtpPluginOnWebProjects(project);

        configureRootProjectTask(project);
    }

    private void configureRootProjectTask(Project project) {
        // The `eclipse` task in the root project should generate Eclipse projects for all Gradle projects
        if (project.getGradle().getParent() == null && project.getParent() == null) {
            getLifecycleTask().configure(new Action<Task>() {
                @Override
                public void execute(Task task) {
                    task.dependsOn(artifactRegistry.getIdeProjectFiles(EclipseProjectMetadata.class));
                }
            });
        }
    }

    private void configureEclipseProject(final ProjectInternal project, final EclipseModel model) {
        final EclipseProject projectModel = model.getProject();

        projectModel.setName(uniqueProjectNameProvider.getUniqueName(project));

        final ConventionMapping convention = ((IConventionAware) projectModel).getConventionMapping();
        convention.map("comment", new Callable<String>() {
            @Override
            public String call() {
                return project.getDescription();
            }

        });

        final TaskProvider<GenerateEclipseProject> task = project.getTasks().register(ECLIPSE_PROJECT_TASK_NAME, GenerateEclipseProject.class, model.getProject());
        task.configure(new Action<GenerateEclipseProject>() {
            @Override
            public void execute(GenerateEclipseProject task) {
                task.setDescription("Generates the Eclipse project file.");
                task.setInputFile(project.file(".project"));
                task.setOutputFile(project.file(".project"));
            }
        });
        addWorker(task, ECLIPSE_PROJECT_TASK_NAME);

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

        artifactRegistry.registerIdeProject(new EclipseProjectMetadata(model, project.getProjectDir(), task));
    }

    private void configureEclipseClasspath(final Project project, final EclipseModel model) {
        model.setClasspath(project.getObjects().newInstance(EclipseClasspath.class, project));
        ((IConventionAware) model.getClasspath()).getConventionMapping().map("defaultOutputDir", new Callable<File>() {
            @Override
            public File call() {
                return new File(project.getProjectDir(), EclipsePluginConstants.DEFAULT_PROJECT_OUTPUT_PATH);
            }

        });

        project.getPlugins().withType(JavaBasePlugin.class, new Action<JavaBasePlugin>() {
            @Override
            public void execute(JavaBasePlugin javaBasePlugin) {
                final TaskProvider<GenerateEclipseClasspath> task = project.getTasks().register(ECLIPSE_CP_TASK_NAME, GenerateEclipseClasspath.class, model.getClasspath());
                task.configure(new Action<GenerateEclipseClasspath>() {
                    @Override
                    public void execute(final GenerateEclipseClasspath task) {
                        task.setDescription("Generates the Eclipse classpath file.");
                        task.setInputFile(project.file(".classpath"));
                        task.setOutputFile(project.file(".classpath"));
                    }
                });
                addWorker(task, ECLIPSE_CP_TASK_NAME);

                XmlTransformer xmlTransformer = new XmlTransformer();
                xmlTransformer.setIndentation("\t");
                model.getClasspath().setFile(new XmlFileContentMerger(xmlTransformer));
                model.getClasspath().setSourceSets(project.getExtensions().getByType(JavaPluginExtension.class).getSourceSets());

                AfterEvaluateHelper.afterEvaluateOrExecute(project, new Action<Project>() {
                    @Override
                    public void execute(Project p) {
                        // keep the ordering we had in earlier gradle versions
                        Set<String> containers = Sets.newLinkedHashSet();
                        containers.add("org.eclipse.jdt.launching.JRE_CONTAINER/org.eclipse.jdt.internal.debug.ui.launcher.StandardVMType/" + model.getJdt().getJavaRuntimeName() + "/");
                        containers.addAll(model.getClasspath().getContainers());
                        model.getClasspath().setContainers(containers);
                    }
                });

                configureScalaDependencies(project, model);
                configureJavaClasspath(project, task, model);
            }

        });
    }

    private static void configureJavaClasspath(final Project project, final TaskProvider<GenerateEclipseClasspath> task, final EclipseModel model) {
        project.getPlugins().withType(JavaPlugin.class, new Action<JavaPlugin>() {
            @Override
            public void execute(JavaPlugin javaPlugin) {
                ((IConventionAware) model.getClasspath()).getConventionMapping().map("plusConfigurations", new Callable<Collection<Configuration>>() {
                    @Override
                    public Collection<Configuration> call() {
                        SourceSetContainer sourceSets = project.getExtensions().getByType(JavaPluginExtension.class).getSourceSets();
                        List<Configuration> sourceSetsConfigurations = Lists.newArrayListWithCapacity(sourceSets.size() * 2);
                        ConfigurationContainer configurations = project.getConfigurations();
                        for (SourceSet sourceSet : sourceSets) {
                            sourceSetsConfigurations.add(configurations.getByName(sourceSet.getCompileClasspathConfigurationName()));
                            sourceSetsConfigurations.add(configurations.getByName(sourceSet.getRuntimeClasspathConfigurationName()));
                        }
                        return sourceSetsConfigurations;
                    }
                }).cache();

                ((IConventionAware) model.getClasspath()).getConventionMapping().map("classFolders", new Callable<List<File>>() {
                    @Override
                    public List<File> call() {
                        List<File> result = Lists.newArrayList();
                        for (SourceSet sourceSet : project.getExtensions().getByType(JavaPluginExtension.class).getSourceSets()) {
                            result.addAll(sourceSet.getOutput().getDirs().getFiles());
                        }
                        return result;
                    }
                });

                task.configure(new Action<GenerateEclipseClasspath>() {
                    @Override
                    public void execute(GenerateEclipseClasspath task) {
                        for (SourceSet sourceSet : project.getExtensions().getByType(JavaPluginExtension.class).getSourceSets()) {
                            task.dependsOn(sourceSet.getOutput().getDirs());
                        }
                    }
                });
            }
        });

        project.getPlugins().withType(JavaTestFixturesPlugin.class, new Action<JavaTestFixturesPlugin>() {
            @Override
            public void execute(JavaTestFixturesPlugin javaTestFixturesPlugin) {
                model.getClasspath().getContainsTestFixtures().convention(true);
            }
        });
    }

    private void configureScalaDependencies(final Project project, final EclipseModel model) {
        project.getPlugins().withType(ScalaBasePlugin.class, new Action<ScalaBasePlugin>() {
            @Override
            public void execute(ScalaBasePlugin scalaBasePlugin) {
                model.getClasspath().containers("org.scala-ide.sdt.launching.SCALA_CONTAINER");

                // exclude the dependencies already provided by SCALA_CONTAINER; prevents problems with Eclipse Scala plugin
                project.getGradle().projectsEvaluated(gradle -> {
                    final List<String> provided = Lists.newArrayList("scala-library", "scala-swing", "scala-dbc");
                    Predicate<Dependency> dependencyInProvided = dependency -> provided.contains(dependency.getName());
                    List<Dependency> dependencies = Lists.newArrayList(Iterables.filter(Iterables.concat(Iterables.transform(model.getClasspath().getPlusConfigurations(), new Function<Configuration, Iterable<Dependency>>() {
                        @Override
                        public Iterable<Dependency> apply(Configuration config) {
                            return config.getAllDependencies();
                        }

                    })), dependencyInProvided));
                    if (!dependencies.isEmpty()) {
                        Configuration detachedScalaConfiguration = project.getConfigurations().detachedConfiguration(dependencies.toArray(new Dependency[0]));
                        jvmEcosystemUtilities.configureAsRuntimeClasspath(detachedScalaConfiguration);
                        model.getClasspath().getMinusConfigurations().add(detachedScalaConfiguration);
                    }
                });
            }
        });
    }

    private void configureEclipseJdt(final Project project, final EclipseModel model) {
        project.getPlugins().withType(JavaBasePlugin.class, new Action<JavaBasePlugin>() {
            @Override
            public void execute(JavaBasePlugin javaBasePlugin) {
                model.setJdt(project.getObjects().newInstance(EclipseJdt.class, new PropertiesFileContentMerger(new PropertiesTransformer())));
                final TaskProvider<GenerateEclipseJdt> task = project.getTasks().register(ECLIPSE_JDT_TASK_NAME, GenerateEclipseJdt.class, model.getJdt());
                task.configure(new Action<GenerateEclipseJdt>() {
                    @Override
                    public void execute(GenerateEclipseJdt task) {
                        //task properties:
                        task.setDescription("Generates the Eclipse JDT settings file.");
                        task.setOutputFile(project.file(".settings/org.eclipse.jdt.core.prefs"));
                        task.setInputFile(project.file(".settings/org.eclipse.jdt.core.prefs"));
                    }
                });
                addWorker(task, ECLIPSE_JDT_TASK_NAME);

                //model properties:
                ConventionMapping conventionMapping = ((IConventionAware) model.getJdt()).getConventionMapping();
                conventionMapping.map("sourceCompatibility", new Callable<JavaVersion>() {
                    @Override
                    public JavaVersion call() {
                        return project.getExtensions().getByType(JavaPluginExtension.class).getSourceCompatibility();
                    }

                });
                conventionMapping.map("targetCompatibility", new Callable<JavaVersion>() {
                    @Override
                    public JavaVersion call() {
                        return project.getExtensions().getByType(JavaPluginExtension.class).getTargetCompatibility();
                    }

                });
                conventionMapping.map("javaRuntimeName", new Callable<String>() {
                    @Override
                    public String call() {
                        return eclipseJavaRuntimeNameFor(project.getExtensions().getByType(JavaPluginExtension.class).getTargetCompatibility());
                    }

                });
            }
        });
    }

    private static String eclipseJavaRuntimeNameFor(JavaVersion version) {
        // Default Eclipse JRE paths:
        // https://github.com/eclipse/eclipse.jdt.debug/blob/master/org.eclipse.jdt.launching/plugin.xml#L241-L303
        String eclipseJavaVersion = EclipseJavaVersionMapper.toEclipseJavaVersion(version);
        switch (version) {
            case VERSION_1_1:
                return "JRE-1.1";
            case VERSION_1_2:
            case VERSION_1_3:
            case VERSION_1_4:
            case VERSION_1_5:
                return "J2SE-" + eclipseJavaVersion;
            default:
                return "JavaSE-" + eclipseJavaVersion;
        }
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
}
