/*
 * Copyright 2007 the original author or authors.
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

package org.gradle.api.plugins;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.Action;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.artifacts.specs.DependencySpecs;
import org.gradle.api.artifacts.specs.Type;
import org.gradle.api.internal.IConventionAware;
import org.gradle.api.specs.Specs;
import org.gradle.api.tasks.ConventionValue;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.bundling.War;
import org.gradle.api.tasks.ide.eclipse.*;
import org.gradle.util.GUtil;
import org.gradle.util.WrapUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * <p>A {@link org.gradle.api.Plugin} which generates Eclipse project files for projects that use the {@link
 * org.gradle.api.plugins.JavaPlugin} or the {@link org.gradle.api.plugins.WarPlugin}.</p>
 *
 * @author Hans Dockter
 */
public class EclipsePlugin implements Plugin {
    public static final String ECLIPSE_TASK_NAME = "eclipse";
    public static final String ECLIPSE_CLEAN_TASK_NAME = "eclipseClean";
    public static final String ECLIPSE_PROJECT_TASK_NAME = "eclipseProject";
    public static final String ECLIPSE_WTP_TASK_NAME = "eclipseWtp";
    public static final String ECLIPSE_CP_TASK_NAME = "eclipseCp";
    public static final String ECLIPSE_WTP_MODULE_TASK_NAME = "eclipseWtpModule";

    public void use(final Project project, ProjectPluginsContainer projectPluginsHandler) {
        projectPluginsHandler.withType(JavaPlugin.class).allPlugins(new Action<JavaPlugin>() {
            public void execute(JavaPlugin plugin) {
                configureEclipseProjectAndClasspath(project);
                configureEclipseWtpModuleForJavaProjects(project);
            }
        });
        projectPluginsHandler.withType(WarPlugin.class).allPlugins(new Action<WarPlugin>() {
            public void execute(WarPlugin plugin) {
                configureEclipseWtpModuleForWarProjects(project, (War) project.getTasks().findByName(WarPlugin.WAR_TASK_NAME));
            }
        });
    }

    private void configureEclipseProjectAndClasspath(Project project) {
        project.getTasks().add(ECLIPSE_TASK_NAME).dependsOn(
                configureEclipseProject(project),
                configureEclipseClasspath(project)
        ).setDescription("Generates an Eclipse .project and .classpath file.");

        project.getTasks().add(ECLIPSE_CLEAN_TASK_NAME, EclipseClean.class).setDescription("Deletes the Eclipse .project and .classpath files.");
    }

    private EclipseProject configureEclipseProject(Project project) {
        EclipseProject eclipseProject = project.getTasks().add(ECLIPSE_PROJECT_TASK_NAME, EclipseProject.class);
        eclipseProject.setProjectName(project.getName());
        eclipseProject.setProjectType(ProjectType.JAVA);
        eclipseProject.setDescription("Generates an Eclipse .project file.");
        return eclipseProject;
    }

    private EclipseClasspath configureEclipseClasspath(final Project project) {
        EclipseClasspath eclipseClasspath = project.getTasks().add(ECLIPSE_CP_TASK_NAME, EclipseClasspath.class);
        eclipseClasspath.getConventionMapping().map(GUtil.map(
                "srcDirs", new ConventionValue() {
                    public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                        SourceSet sourceSet = java(convention).getSource().getByName(JavaPlugin.MAIN_SOURCE_SET_NAME);
                        return GUtil.addLists(sourceSet.getJava().getSrcDirs(), sourceSet.getResources().getSrcDirs());
                    }
                },
                "testSrcDirs", new ConventionValue() {
                    public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                        SourceSet sourceSet = java(convention).getSource().getByName(JavaPlugin.TEST_SOURCE_SET_NAME);
                        return GUtil.addLists(sourceSet.getJava().getSrcDirs(), sourceSet.getResources().getSrcDirs());
                    }
                },
                "outputDirectory", new ConventionValue() {
                    public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                        return java(convention).getSource().getByName(JavaPlugin.MAIN_SOURCE_SET_NAME).getClassesDir();
                    }
                },
                "testOutputDirectory", new ConventionValue() {
                    public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                        return java(convention).getSource().getByName(JavaPlugin.TEST_SOURCE_SET_NAME).getClassesDir();
                    }
                },
                "classpathLibs", new ConventionValue() {
                    public Object getValue(Convention convention, final IConventionAware conventionAwareObject) {
                        ConfigurationContainer configurationContainer = ((Task) conventionAwareObject).getProject().getConfigurations();
                        return new ArrayList(configurationContainer.getByName(JavaPlugin.TEST_RUNTIME_CONFIGURATION_NAME).files(DependencySpecs.type(Type.EXTERNAL)));
                    }
                },
                "projectDependencies", new ConventionValue() {
                    public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                        return new ArrayList(project.getConfigurations().getByName(JavaPlugin.TEST_RUNTIME_CONFIGURATION_NAME).getAllDependencies(
                                ProjectDependency.class));
                    }
                }));
        eclipseClasspath.setDescription("Generates an Eclipse .classpath file.");
        return eclipseClasspath;
    }

    private void configureEclipseWtpModuleForJavaProjects(Project project) {
        EclipseWtpModule eclipseWtpModule = project.getTasks().add(ECLIPSE_WTP_MODULE_TASK_NAME, EclipseWtpModule.class);

        eclipseWtpModule.conventionMapping(
                "srcDirs", new ConventionValue() {
                    public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                        SourceSet sourceSet = java(convention).getSource().getByName(JavaPlugin.MAIN_SOURCE_SET_NAME);
                        return GUtil.addLists(sourceSet.getJava().getSrcDirs(), sourceSet.getResources().getSrcDirs());
                    }
                });
        eclipseWtpModule.setDescription("Generates the Eclipse Wtp files.");
    }

    private void configureEclipseWtpModuleForWarProjects(final Project project, final War war) {
        final EclipseWtp eclipseWtp = project.getTasks().add(ECLIPSE_WTP_TASK_NAME, EclipseWtp.class);

        eclipseWtp.getConventionMapping().map(GUtil.map(
                "warResourceMappings", new ConventionValue() {
                    public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                        SourceSet sourceSet = java(convention).getSource().getByName(JavaPlugin.MAIN_SOURCE_SET_NAME);
                        List allSrcDirs = GUtil.addLists(sourceSet.getJava().getSrcDirs(), sourceSet.getResources().getSrcDirs());
                        Map resourceMappings = WrapUtil.toMap("/WEB-INF/classes", allSrcDirs);
                        resourceMappings.put("/", WrapUtil.toList(war(convention).getWebAppDir()));
                        return resourceMappings;
                    }
                },
                "outputDirectory", new ConventionValue() {
                    public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                        return java(convention).getSource().getByName(JavaPlugin.MAIN_SOURCE_SET_NAME).getClassesDir();
                    }
                },
                "deployName", new ConventionValue() {
                    public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                        return project.getName();
                    }
                },
                "warLibs", new ConventionValue() {
                    public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                        List warLibs = war.dependencies(eclipseWtp.isFailForMissingDependencies(), false);
                        if (war.getAdditionalLibFileSets() != null) {
                            warLibs.addAll(war.getAdditionalLibFileSets());
                        }
                        return warLibs;
                    }
                },
                "projectDependencies", new ConventionValue() {
                    public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                        /*
                        * todo We return all project dependencies here, not just the one for runtime. We can't use Ivy here, as we
                        * request the project dependencies not via a resolve. We would have to filter the project dependencies
                        * ourselfes. This is not completely trivial due to configuration inheritance.
                        */
                        return new ArrayList(Specs.filterIterable(
                                ((Task) conventionAwareObject).getProject().getConfigurations().getByName(JavaPlugin.RUNTIME_CONFIGURATION_NAME).getAllDependencies(),
                                DependencySpecs.type(Type.PROJECT))
                        );
                    }
                }));

        // todo: When we refactor the way we resolve project dependencies this step might become obsolete
        createDependencyOnEclipseProjectTaskOfDependentProjects(project, eclipseWtp);

        project.getTasks().getByName(ECLIPSE_TASK_NAME).dependsOn(eclipseWtp);
    }

    private void createDependencyOnEclipseProjectTaskOfDependentProjects(Project project, EclipseWtp eclipseWtp) {
        Set<Dependency> projectDependencies = Specs.filterIterable(
                project.getConfigurations().getByName(JavaPlugin.RUNTIME_CONFIGURATION_NAME).getDependencies(),
                DependencySpecs.type(Type.PROJECT)
        );

        for (Dependency dependentProject : projectDependencies) {
            eclipseWtp.dependsOn(((ProjectDependency) dependentProject).getDependencyProject().getPath() + ":eclipseProject");
        }
    }

    protected JavaPluginConvention java(Convention convention) {
        return convention.getPlugin(JavaPluginConvention.class);
    }

    private WarPluginConvention war(Convention convention) {
        return convention.getPlugin(WarPluginConvention.class);
    }
}