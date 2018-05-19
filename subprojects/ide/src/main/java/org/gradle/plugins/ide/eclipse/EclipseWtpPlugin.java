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

import com.google.common.collect.Lists;
import org.gradle.api.Action;
import org.gradle.api.JavaVersion;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.internal.ConventionMapping;
import org.gradle.api.internal.IConventionAware;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.plugins.WarPlugin;
import org.gradle.api.plugins.WarPluginConvention;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.War;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.plugins.ear.Ear;
import org.gradle.plugins.ear.EarPlugin;
import org.gradle.plugins.ear.EarPluginConvention;
import org.gradle.plugins.ide.eclipse.internal.AfterEvaluateHelper;
import org.gradle.plugins.ide.eclipse.model.Classpath;
import org.gradle.plugins.ide.eclipse.model.EclipseModel;
import org.gradle.plugins.ide.eclipse.model.EclipseWtp;
import org.gradle.plugins.ide.eclipse.model.EclipseWtpComponent;
import org.gradle.plugins.ide.eclipse.model.EclipseWtpFacet;
import org.gradle.plugins.ide.eclipse.model.Facet;
import org.gradle.plugins.ide.eclipse.model.WbResource;
import org.gradle.plugins.ide.eclipse.model.internal.WtpClasspathAttributeSupport;
import org.gradle.plugins.ide.internal.IdePlugin;

import javax.inject.Inject;
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * A plugin which configures the Eclipse Web Tools Platform.
 */
public class EclipseWtpPlugin extends IdePlugin {

    public static final String ECLIPSE_WTP_COMPONENT_TASK_NAME = "eclipseWtpComponent";
    public static final String ECLIPSE_WTP_FACET_TASK_NAME = "eclipseWtpFacet";
    public static final String WEB_LIBS_CONTAINER = "org.eclipse.jst.j2ee.internal.web.container";

    public final Instantiator instantiator;

    @Inject
    public EclipseWtpPlugin(Instantiator instantiator) {
        this.instantiator = instantiator;
    }

    @Override
    protected String getLifecycleTaskName() {
        return "eclipseWtp";
    }

    @Override
    protected void onApply(Project project) {
        project.getPluginManager().apply(EclipsePlugin.class);

        EclipseModel model = project.getExtensions().getByType(EclipseModel.class);
        model.setWtp(project.getObjects().newInstance(EclipseWtp.class));

        getLifecycleTask().configure(withDescription("Generates Eclipse wtp configuration files."));
        getCleanTask().configure(withDescription("Cleans Eclipse wtp configuration files."));

        project.getTasks().get(Task.class, EclipsePlugin.ECLIPSE_TASK_NAME).configure(dependsOn(getLifecycleTask()));
        project.getTasks().get(Task.class, cleanName(EclipsePlugin.ECLIPSE_TASK_NAME)).configure(dependsOn(getCleanTask()));

        configureEclipseProject(project);
        configureEclipseWtpComponent(project, model);
        configureEclipseWtpFacet(project, model);

        // do this after wtp is configured because wtp config is required to update classpath properly
        configureEclipseClasspath(project, model);
    }

    private void configureEclipseClasspath(final Project project, final EclipseModel model) {
        project.getPlugins().withType(JavaPlugin.class, new Action<JavaPlugin>() {
            @Override
            public void execute(JavaPlugin javaPlugin) {
                AfterEvaluateHelper.afterEvaluateOrExecute(project, new Action<Project>() {
                    @Override
                    public void execute(Project project) {
                        Collection<Configuration> plusConfigurations = model.getClasspath().getPlusConfigurations();
                        EclipseWtpComponent component = model.getWtp().getComponent();
                        plusConfigurations.addAll(component.getRootConfigurations());
                        plusConfigurations.addAll(component.getLibConfigurations());
                    }
                });

                model.getClasspath().getFile().whenMerged(new Action<Classpath>() {
                    @Override
                    public void execute(Classpath classpath) {
                        new WtpClasspathAttributeSupport(project, model).enhance(classpath);
                    }
                });
            }
        });

        project.getPlugins().withType(WarPlugin.class, new Action<WarPlugin>() {
            @Override
            public void execute(WarPlugin warPlugin) {
                model.getClasspath().containers(WEB_LIBS_CONTAINER);
            }
        });
    }

    private void configureEclipseWtpComponent(final Project project, final EclipseModel model) {
        final TaskProvider<GenerateEclipseWtpComponent> task = maybeAddTask(project, this, ECLIPSE_WTP_COMPONENT_TASK_NAME, GenerateEclipseWtpComponent.class, new Action<GenerateEclipseWtpComponent>() {
            @Override
            public void execute(final GenerateEclipseWtpComponent task) {
                //task properties:
                task.setDescription("Generates the Eclipse WTP component settings file.");
                task.setInputFile(project.file(".settings/org.eclipse.wst.common.component"));
                task.setOutputFile(project.file(".settings/org.eclipse.wst.common.component"));

                ((IConventionAware) task.getComponent()).getConventionMapping().map("deployName", new Callable<String>() {
                    @Override
                    public String call() throws Exception {
                        return model.getProject().getName();
                    }
                });
            }

        });

        project.getPlugins().withType(JavaPlugin.class, new Action<JavaPlugin>() {
            @Override
            public void execute(JavaPlugin javaPlugin) {
                if (hasWarOrEarPlugin(project)) {
                    return;
                }

                task.configure(new Action<GenerateEclipseWtpComponent>() {
                    @Override
                    public void execute(GenerateEclipseWtpComponent task) {
                        Set<Configuration> libConfigurations = task.getComponent().getLibConfigurations();

                        libConfigurations.add(project.getConfigurations().getByName("runtime"));
                        task.getComponent().setClassesDeployPath("/");
                        ((IConventionAware) task.getComponent()).getConventionMapping().map("libDeployPath", new Callable<String>() {
                            @Override
                            public String call() throws Exception {
                                return "../";
                            }
                        });
                        ((IConventionAware)task.getComponent()).getConventionMapping().map("sourceDirs", new Callable<Set<File>>() {
                            @Override
                            public Set<File> call() throws Exception {
                                return getMainSourceDirs(project);
                            }
                        });
                    }
                });
            }

        });
        project.getPlugins().withType(WarPlugin.class, new Action<WarPlugin>() {
            @Override
            public void execute(WarPlugin warPlugin) {
                task.configure(new Action<GenerateEclipseWtpComponent>() {
                    @Override
                    public void execute(GenerateEclipseWtpComponent task) {
                        Set<Configuration> libConfigurations = task.getComponent().getLibConfigurations();
                        Set<Configuration> minusConfigurations = task.getComponent().getMinusConfigurations();

                        libConfigurations.add(project.getConfigurations().getByName("runtime"));
                        minusConfigurations.add(project.getConfigurations().getByName("providedRuntime"));
                        task.getComponent().setClassesDeployPath("/WEB-INF/classes");
                        ConventionMapping convention = ((IConventionAware) task.getComponent()).getConventionMapping();
                        convention.map("libDeployPath", new Callable<String>() {
                            @Override
                            public String call() throws Exception {
                                return "/WEB-INF/lib";
                            }
                        });
                        convention.map("contextPath", new Callable<String>() {
                            @Override
                            public String call() throws Exception {
                                return ((War) project.getTasks().getByName("war")).getBaseName();
                            }
                        });
                        convention.map("resources", new Callable<List<WbResource>>() {
                            @Override
                            public List<WbResource> call() throws Exception {
                                return Lists.newArrayList(new WbResource("/", project.getConvention().getPlugin(WarPluginConvention.class).getWebAppDirName()));
                            }
                        });
                        convention.map("sourceDirs", new Callable<Set<File>>() {
                            @Override
                            public Set<File> call() throws Exception {
                                return getMainSourceDirs(project);
                            }
                        });
                    }
                });
            }

        });
        project.getPlugins().withType(EarPlugin.class, new Action<EarPlugin>() {
            @Override
            public void execute(EarPlugin earPlugin) {
                task.configure(new Action<GenerateEclipseWtpComponent>() {
                    @Override
                    public void execute(GenerateEclipseWtpComponent task) {
                        Set<Configuration> libConfigurations = task.getComponent().getLibConfigurations();
                        Set<Configuration> rootConfigurations = task.getComponent().getRootConfigurations();

                        rootConfigurations.clear();
                        rootConfigurations.add(project.getConfigurations().getByName("deploy"));
                        libConfigurations.clear();
                        libConfigurations.add(project.getConfigurations().getByName("earlib"));
                        task.getComponent().setClassesDeployPath("/");
                        final ConventionMapping convention = ((IConventionAware) task.getComponent()).getConventionMapping();
                        convention.map("libDeployPath", new Callable<String>() {
                            @Override
                            public String call() throws Exception {
                                String deployPath = ((Ear) project.getTasks().findByName(EarPlugin.EAR_TASK_NAME)).getLibDirName();
                                if (!deployPath.startsWith("/")) {
                                    deployPath = "/" + deployPath;
                                }

                                return deployPath;
                            }
                        });
                        convention.map("sourceDirs", new Callable<Set<File>>() {
                            @Override
                            public Set<File> call() throws Exception {
                                return project.getLayout().files(project.getConvention().getPlugin(EarPluginConvention.class).getAppDirName()).getFiles();
                            }
                        });
                        project.getPlugins().withType(JavaPlugin.class, new Action<JavaPlugin>() {
                            @Override
                            public void execute(JavaPlugin javaPlugin) {
                                convention.map("sourceDirs", new Callable<Set<File>>() {
                                    @Override
                                    public Set<File> call() throws Exception {
                                        return getMainSourceDirs(project);
                                    }
                                });
                            }

                        });
                    }
                });
            }

        });

        //model properties:
        model.getWtp().setComponent(task.map(new Transformer<EclipseWtpComponent, GenerateEclipseWtpComponent>() {
            @Override
            public EclipseWtpComponent transform(GenerateEclipseWtpComponent task) {
                return task.getComponent();
            }
        }));
    }

    private void configureEclipseWtpFacet(final Project project, final EclipseModel eclipseModel) {
        final TaskProvider<GenerateEclipseWtpFacet> task = maybeAddTask(project, this, ECLIPSE_WTP_FACET_TASK_NAME, GenerateEclipseWtpFacet.class, new Action<GenerateEclipseWtpFacet>() {
            @Override
            public void execute(final GenerateEclipseWtpFacet task) {
                //task properties:
                task.setDescription("Generates the Eclipse WTP facet settings file.");
                task.setInputFile(project.file(".settings/org.eclipse.wst.common.project.facet.core.xml"));
                task.setOutputFile(project.file(".settings/org.eclipse.wst.common.project.facet.core.xml"));
            }

        });

        project.getPlugins().withType(JavaPlugin.class, new Action<JavaPlugin>() {
            @Override
            public void execute(JavaPlugin javaPlugin) {
                if (hasWarOrEarPlugin(project)) {
                    return;
                }

                task.configure(new Action<GenerateEclipseWtpFacet>() {
                    @Override
                    public void execute(GenerateEclipseWtpFacet task) {
                        ((IConventionAware) task.getFacet()).getConventionMapping().map("facets", new Callable<List<Facet>>() {
                            @Override
                            public List<Facet> call() throws Exception {
                                return Lists.newArrayList(
                                    new Facet(Facet.FacetType.fixed, "jst.java", null),
                                    new Facet(Facet.FacetType.installed, "jst.utility", "1.0"),
                                    new Facet(Facet.FacetType.installed, "jst.java", toJavaFacetVersion(project.getConvention().getPlugin(JavaPluginConvention.class).getSourceCompatibility()))
                                );
                            }
                        });
                    }
                });
            }

        });
        project.getPlugins().withType(WarPlugin.class, new Action<WarPlugin>() {
            @Override
            public void execute(WarPlugin warPlugin) {
                task.configure(new Action<GenerateEclipseWtpFacet>() {
                    @Override
                    public void execute(GenerateEclipseWtpFacet task) {
                        ((IConventionAware) task.getFacet()).getConventionMapping().map("facets", new Callable<List<Facet>>() {
                            @Override
                            public List<Facet> call() throws Exception {
                                return Lists.newArrayList(
                                    new Facet(Facet.FacetType.fixed, "jst.java", null),
                                    new Facet(Facet.FacetType.fixed, "jst.web", null),
                                    new Facet(Facet.FacetType.installed, "jst.web", "2.4"),
                                    new Facet(Facet.FacetType.installed, "jst.java", toJavaFacetVersion(project.getConvention().getPlugin(JavaPluginConvention.class).getSourceCompatibility()))
                                );
                            }
                        });
                    }
                });
            }

        });
        project.getPlugins().withType(EarPlugin.class, new Action<EarPlugin>() {
            @Override
            public void execute(EarPlugin earPlugin) {
                task.configure(new Action<GenerateEclipseWtpFacet>() {
                    @Override
                    public void execute(GenerateEclipseWtpFacet task) {
                        ((IConventionAware) task.getFacet()).getConventionMapping().map("facets", new Callable<List<Facet>>() {
                            @Override
                            public List<Facet> call() throws Exception {
                                return Lists.newArrayList(
                                    new Facet(Facet.FacetType.fixed, "jst.ear", null),
                                    new Facet(Facet.FacetType.installed, "jst.ear", "5.0")
                                );
                            }
                        });
                    }
                });
            }

        });

        //model properties:
        eclipseModel.getWtp().setFacet(task.map(new Transformer<EclipseWtpFacet, GenerateEclipseWtpFacet>() {
            @Override
            public EclipseWtpFacet transform(GenerateEclipseWtpFacet task) {
                return task.getFacet();
            }
        }));
    }

    private <T extends Task> TaskProvider<T> maybeAddTask(Project project, IdePlugin plugin, String taskName, Class<T> taskType, Action<T> action) {
        TaskContainer tasks = project.getTasks();
        T taskFound = tasks.withType(taskType).findByName(taskName);
        if (taskFound != null) {
            return tasks.get(taskType, taskName);
        }

        TaskProvider<T> task = project.getTasks().createLater(taskName, taskType);
        task.configure(action);
        plugin.addWorker(task);
        return task;
    }

    private void configureEclipseProject(final Project project) {
        Action<Object> action = new Action<Object>() {
            @Override
            public void execute(Object ignored) {
                project.getTasks().withType(GenerateEclipseProject.class).configureEach(new Action<GenerateEclipseProject>() {
                    @Override
                    public void execute(GenerateEclipseProject task) {
                        task.getProjectModel().buildCommand("org.eclipse.wst.common.project.facet.core.builder");
                        task.getProjectModel().buildCommand("org.eclipse.wst.validation.validationbuilder");
                        task.getProjectModel().natures("org.eclipse.wst.common.project.facet.core.nature");
                        task.getProjectModel().natures("org.eclipse.wst.common.modulecore.ModuleCoreNature");
                        task.getProjectModel().natures("org.eclipse.jem.workbench.JavaEMFNature");
                    }

                });
            }

        };
        project.getPlugins().withType(JavaPlugin.class, action);
        project.getPlugins().withType(EarPlugin.class, action);
    }

    private boolean hasWarOrEarPlugin(Project project) {
        return project.getPlugins().hasPlugin(WarPlugin.class) || project.getPlugins().hasPlugin(EarPlugin.class);
    }

    private Set<File> getMainSourceDirs(Project project) {
        return project.getConvention().getPlugin(JavaPluginConvention.class).getSourceSets().getByName("main").getAllSource().getSrcDirs();
    }

    private String toJavaFacetVersion(JavaVersion version) {
        if (version.equals(JavaVersion.VERSION_1_5)) {
            return "5.0";
        }

        if (version.equals(JavaVersion.VERSION_1_6)) {
            return "6.0";
        }

        return version.toString();
    }
}
