/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.plugins

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.specs.DependencySpecs
import org.gradle.api.artifacts.specs.Type
import org.gradle.api.plugins.scala.ScalaPlugin
import org.gradle.api.specs.Specs
import org.gradle.api.tasks.GroovySourceSet
import org.gradle.api.tasks.ScalaSourceSet
import org.gradle.api.tasks.SourceSet
import org.gradle.util.GUtil
import org.gradle.util.WrapUtil
import org.gradle.api.tasks.ide.eclipse.*

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
        projectPluginsHandler.withType(JavaPlugin.class).allPlugins {
            configureEclipseProjectAndClasspath(project);
            configureEclipseWtpModuleForJavaProjects(project);
        }
        projectPluginsHandler.withType(WarPlugin.class).allPlugins {
            configureEclipseWtpModuleForWarProjects(project);
        }
    }

    private void configureEclipseProjectAndClasspath(Project project) {
        project.tasks.add(ECLIPSE_TASK_NAME).dependsOn(
                configureEclipseProject(project),
                configureEclipseClasspath(project)
        ).setDescription("Generates an Eclipse .project and .classpath file.");

        project.tasks.add(ECLIPSE_CLEAN_TASK_NAME, EclipseClean.class).setDescription("Deletes the Eclipse .project and .classpath files.");
    }

    private EclipseProject configureEclipseProject(Project project) {
        EclipseProject eclipseProject = project.tasks.add(ECLIPSE_PROJECT_TASK_NAME, EclipseProject.class);
        eclipseProject.setProjectName(project.getName());
        eclipseProject.conventionMapping.natureNames = {
            selectEclipseProjectType(project).natureNames() as Set
        }
        eclipseProject.conventionMapping.buildCommandNames = {
            selectEclipseProjectType(project).buildCommandNames() as Set
        }
        eclipseProject.setDescription("Generates an Eclipse .project file.");
        return eclipseProject;
    }

    private ProjectType selectEclipseProjectType(Project project) {
        if (project.getPlugins().hasPlugin(GroovyPlugin.class)) {
            return ProjectType.GROOVY;
        }
        if (project.getPlugins().hasPlugin(ScalaPlugin.class)) {
            return ProjectType.SCALA;
        }

        return ProjectType.JAVA;
    }

    private EclipseClasspath configureEclipseClasspath(final Project project) {
        EclipseClasspath eclipseClasspath = project.getTasks().replace(ECLIPSE_CP_TASK_NAME, EclipseClasspath.class);
        eclipseClasspath.conventionMapping.srcDirs = {
            return allLanguageSrcDirs(project, SourceSet.MAIN_SOURCE_SET_NAME);
        }
        eclipseClasspath.conventionMapping.testSrcDirs = {
            return allLanguageSrcDirs(project, SourceSet.TEST_SOURCE_SET_NAME);
        }
        eclipseClasspath.conventionMapping.outputDirectory = {
            return project.sourceSets[SourceSet.MAIN_SOURCE_SET_NAME].getClassesDir();
        }
        eclipseClasspath.conventionMapping.testOutputDirectory = {
            return project.sourceSets[SourceSet.TEST_SOURCE_SET_NAME].getClassesDir();
        }
        eclipseClasspath.conventionMapping.classpathLibs = {
            ConfigurationContainer configurationContainer = project.getConfigurations();
            configurationContainer[JavaPlugin.TEST_RUNTIME_CONFIGURATION_NAME].files {
                return !(it instanceof ProjectDependency);
            } as List
        }
        eclipseClasspath.conventionMapping.projectDependencies = {
            return new ArrayList(project.configurations[JavaPlugin.TEST_RUNTIME_CONFIGURATION_NAME].getAllDependencies(
                    ProjectDependency.class));
        }
        eclipseClasspath.setDescription("Generates an Eclipse .classpath file.");
        return eclipseClasspath;
    }

    private void configureEclipseWtpModuleForJavaProjects(Project project) {
        EclipseWtpModule eclipseWtpModule = project.tasks.add(ECLIPSE_WTP_MODULE_TASK_NAME, EclipseWtpModule.class);

        eclipseWtpModule.conventionMapping.srcDirs = {
            SourceSet sourceSet = project.sourceSets[SourceSet.MAIN_SOURCE_SET_NAME]
            return GUtil.addLists(sourceSet.java.srcDirs, sourceSet.resources.srcDirs);
        }
        eclipseWtpModule.setDescription("Generates the Eclipse Wtp files.");
    }

    private void configureEclipseWtpModuleForWarProjects(final Project project) {
        final EclipseWtp eclipseWtp = project.getTasks().add(ECLIPSE_WTP_TASK_NAME, EclipseWtp.class);

        eclipseWtp.conventionMapping.warResourceMappings = {
            SourceSet sourceSet = project.sourceSets[SourceSet.MAIN_SOURCE_SET_NAME]
            List allSrcDirs = sourceSet.java.srcDirs + sourceSet.resources.srcDirs as List
            Map resourceMappings = WrapUtil.toMap("/WEB-INF/classes", allSrcDirs);
            resourceMappings.put("/", WrapUtil.toList(war(project).webAppDir));
            return resourceMappings;
        }
        eclipseWtp.conventionMapping.outputDirectory = {
            return project.sourceSets[SourceSet.MAIN_SOURCE_SET_NAME].getClassesDir();
        }
        eclipseWtp.conventionMapping.deployName = {
            return project.getName();
        }
        eclipseWtp.conventionMapping.warLibs = {
            // This isn't quite true
            Closure spec = {
                return !(it instanceof ProjectDependency);
            };
            Set<File> provided = project.configurations[WarPlugin.PROVIDED_RUNTIME_CONFIGURATION_NAME].getFiles();
            Set<File> runtime = project.configurations[JavaPlugin.RUNTIME_CONFIGURATION_NAME].copyRecursive(
                    spec).getFiles();
            runtime.removeAll(provided);
            return runtime as List;
        }
        eclipseWtp.conventionMapping.projectDependencies = {
           /*
            * todo We return all project dependencies here, not just the one for runtime. We can't use Ivy here, as we
            * request the project dependencies not via a resolve. We would have to filter the project dependencies
            * ourselfes. This is not completely trivial due to configuration inheritance.
            */
            return new ArrayList(Specs.filterIterable(
                    project.getConfigurations().getByName(JavaPlugin.RUNTIME_CONFIGURATION_NAME).getAllDependencies(),
                    DependencySpecs.type(Type.PROJECT))
            );
        }

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

    private WarPluginConvention war(Project project) {
        return project.convention.getPlugin(WarPluginConvention.class);
    }

    private Object allLanguageSrcDirs(Project project, String name) {
        SourceSet sourceSet = project.sourceSets[name];

        Set<Object> extraDirs = new TreeSet<Object>();
        GroovySourceSet groovySourceSet = sourceSet.convention.findPlugin(GroovySourceSet.class);
        if (groovySourceSet != null) {
            extraDirs.addAll(groovySourceSet.getGroovy().getSrcDirs());
        }
        ScalaSourceSet scalaSourceSet = sourceSet.convention.findPlugin(ScalaSourceSet.class);
        if (scalaSourceSet != null) {
            extraDirs.addAll(scalaSourceSet.getScala().getSrcDirs());
        }

        return GUtil.addLists(
            sourceSet.getJava().getSrcDirs(),
            sourceSet.getResources().getSrcDirs(),
            extraDirs);
    }
}
