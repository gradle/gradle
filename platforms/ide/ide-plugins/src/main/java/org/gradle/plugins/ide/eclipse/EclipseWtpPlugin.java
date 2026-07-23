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

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.internal.ConventionMapping;
import org.gradle.api.internal.IConventionAware;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.plugins.WarPlugin;
import org.gradle.api.plugins.internal.JavaPluginHelper;
import org.gradle.api.tasks.bundling.War;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.plugins.ear.Ear;
import org.gradle.plugins.ear.EarPlugin;
import org.gradle.plugins.ide.eclipse.internal.AfterEvaluateHelper;
import org.gradle.plugins.ide.eclipse.model.Classpath;
import org.gradle.plugins.ide.eclipse.model.EclipseModel;
import org.gradle.plugins.ide.eclipse.model.EclipseWtpComponent;
import org.gradle.plugins.ide.eclipse.model.WbResource;
import org.gradle.plugins.ide.eclipse.model.internal.WtpClasspathAttributeSupport;
import org.gradle.plugins.ide.internal.IdePlugin;
import org.gradle.util.internal.RelativePathUtil;
import org.gradle.util.internal.WrapUtil;

import javax.inject.Inject;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * A plugin which configures the Eclipse Web Tools Platform.
 *
 * @see <a href="https://docs.gradle.org/current/userguide/eclipse_plugin.html">Eclipse plugin reference</a>
 */
public abstract class EclipseWtpPlugin extends IdePlugin {

    public static final String WEB_LIBS_CONTAINER = "org.eclipse.jst.j2ee.internal.web.container";

    public final Instantiator instantiator;

    @Inject
    public EclipseWtpPlugin(Instantiator instantiator) {
        this.instantiator = instantiator;
    }

    @Override
    protected void onApply(Project project) {
        project.getPluginManager().apply(EclipsePlugin.class);

        EclipseModel model = project.getExtensions().getByType(EclipseModel.class);

        configureEclipseProject(project, model);
        configureEclipseWtpComponent(project, model);

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
        final EclipseWtpComponent component = project.getObjects().newInstance(EclipseWtpComponent.class, project);
        model.getWtp().setComponent(component);

        ((IConventionAware) component).getConventionMapping().map("deployName", new Callable<String>() {
            @Override
            public String call() throws Exception {
                return model.getProject().getName();
            }
        });

        project.getPlugins().withType(JavaPlugin.class, new Action<JavaPlugin>() {
            @Override
            public void execute(JavaPlugin javaPlugin) {
                if (hasWarOrEarPlugin(project)) {
                    return;
                }

                Set<Configuration> libConfigurations = component.getLibConfigurations();

                libConfigurations.add(JavaPluginHelper.getJavaComponent(project).getMainFeature().getRuntimeClasspathConfiguration());
                component.setClassesDeployPath("/");
                ((IConventionAware) component).getConventionMapping().map("libDeployPath", new Callable<String>() {
                    @Override
                    public String call() throws Exception {
                        return "../";
                    }
                });
                ((IConventionAware) component).getConventionMapping().map("sourceDirs", new Callable<Set<File>>() {
                    @Override
                    public Set<File> call() throws Exception {
                        return getMainSourceDirs(project);
                    }
                });
            }

        });
        project.getPlugins().withType(WarPlugin.class, new Action<WarPlugin>() {
            @Override
            public void execute(WarPlugin warPlugin) {
                Set<Configuration> libConfigurations = component.getLibConfigurations();
                Set<Configuration> minusConfigurations = component.getMinusConfigurations();

                libConfigurations.add(JavaPluginHelper.getJavaComponent(project).getMainFeature().getRuntimeClasspathConfiguration());
                minusConfigurations.add(project.getConfigurations().getByName("providedRuntime"));
                component.setClassesDeployPath("/WEB-INF/classes");
                ConventionMapping convention = ((IConventionAware) component).getConventionMapping();
                convention.map("libDeployPath", new Callable<String>() {
                    @Override
                    public String call() throws Exception {
                        return "/WEB-INF/lib";
                    }
                });
                convention.map("contextPath", new Callable<String>() {
                    @Override
                    public String call() throws Exception {
                        return ((War) project.getTasks().getByName("war")).getArchiveBaseName().getOrNull();
                    }
                });
                convention.map("resources", new Callable<List<WbResource>>() {
                    @Override
                    public List<WbResource> call() throws Exception {
                        File projectDir = project.getProjectDir();
                        File webAppDir = ((War) project.getTasks().getByName("war")).getWebAppDirectory().get().getAsFile();
                        String webAppDirName = RelativePathUtil.relativePath(projectDir, webAppDir);
                        List<WbResource> result = new ArrayList<>(1);
                        result.add(new WbResource("/", webAppDirName));
                        return result;
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
        project.getPlugins().withType(EarPlugin.class, new Action<EarPlugin>() {
            @Override
            public void execute(EarPlugin earPlugin) {
                Set<Configuration> libConfigurations = component.getLibConfigurations();
                Set<Configuration> rootConfigurations = component.getRootConfigurations();

                rootConfigurations.clear();
                rootConfigurations.add(project.getConfigurations().getByName("deploy"));
                libConfigurations.clear();
                libConfigurations.add(project.getConfigurations().getByName("earlib"));
                component.setClassesDeployPath("/");
                final ConventionMapping convention = ((IConventionAware) component).getConventionMapping();
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
                        return WrapUtil.toSet(((Ear) project.getTasks().findByName(EarPlugin.EAR_TASK_NAME)).getAppDirectory().get().getAsFile());
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

    private void configureEclipseProject(final Project project, final EclipseModel model) {
        Action<Object> action = new Action<Object>() {
            @Override
            public void execute(Object ignored) {
                model.getProject().buildCommand("org.eclipse.wst.common.project.facet.core.builder");
                model.getProject().buildCommand("org.eclipse.wst.validation.validationbuilder");
                model.getProject().natures("org.eclipse.wst.common.project.facet.core.nature");
                model.getProject().natures("org.eclipse.wst.common.modulecore.ModuleCoreNature");
                model.getProject().natures("org.eclipse.jem.workbench.JavaEMFNature");
            }

        };
        project.getPlugins().withType(JavaPlugin.class, action);
        project.getPlugins().withType(EarPlugin.class, action);
    }

    private boolean hasWarOrEarPlugin(Project project) {
        return project.getPlugins().hasPlugin(WarPlugin.class) || project.getPlugins().hasPlugin(EarPlugin.class);
    }

    private Set<File> getMainSourceDirs(Project project) {
        return project.getExtensions().getByType(JavaPluginExtension.class).getSourceSets().getByName("main").getAllSource().getSrcDirs();
    }

}
