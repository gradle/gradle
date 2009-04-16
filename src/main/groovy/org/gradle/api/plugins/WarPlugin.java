/*
 * Copyright 2007-2008 the original author or authors.
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
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.artifacts.specs.DependencySpecs;
import org.gradle.api.artifacts.specs.Type;
import org.gradle.api.internal.IConventionAware;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.internal.artifacts.publish.ArchivePublishArtifact;
import org.gradle.api.internal.project.PluginRegistry;
import org.gradle.api.specs.Specs;
import org.gradle.api.tasks.ConventionValue;
import org.gradle.api.tasks.bundling.Bundle;
import org.gradle.api.tasks.bundling.War;
import org.gradle.api.tasks.ide.eclipse.EclipseWtp;
import org.gradle.util.GUtil;
import org.gradle.util.WrapUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * <p>A {@link Plugin} which extends the {@link JavaPlugin} to add tasks which assemble a web application into a WAR
 * file.</p>
 *
 * @author Hans Dockter
 */
public class WarPlugin implements Plugin {
    public static final String PROVIDED_COMPILE_CONFIGURATION_NAME = "providedCompile";
    public static final String PROVIDED_RUNTIME_CONFIGURATION_NAME = "providedRuntime";
    public static final String ECLIPSE_WTP_TASK_NAME = "eclipseWtp";

    public void apply(Project project, PluginRegistry pluginRegistry, Map<String, ?> customValues) {
        pluginRegistry.apply(JavaPlugin.class, project, customValues);
        project.task(project.getArchivesTaskBaseName() + "_jar").setEnabled(false);
        War war = ((Bundle) project.task("libs")).war();
        war.setDescription("Generates a war archive with all the compiled classes, the web-app content and the libraries.");
        project.getConfigurations().get(Dependency.MASTER_CONFIGURATION).addArtifact(new ArchivePublishArtifact(war));
        configureConfigurations(project.getConfigurations());
        configureEclipse(project, war);
    }

    public void configureConfigurations(ConfigurationContainer configurationContainer) {
        Configuration provideCompileConfiguration = configurationContainer.add(PROVIDED_COMPILE_CONFIGURATION_NAME).setVisible(false).
                setDescription("Additional compile classpath for libraries that should not be part of the war archive.");
        Configuration provideRuntimeConfiguration = configurationContainer.add(PROVIDED_RUNTIME_CONFIGURATION_NAME).setVisible(false).
                extendsFrom(provideCompileConfiguration).
                setDescription("Additional runtime classpath for libraries that should not be part of the war archive.");
        configurationContainer.get(JavaPlugin.COMPILE_CONFIGURATION_NAME).extendsFrom(provideCompileConfiguration);
        configurationContainer.get(JavaPlugin.RUNTIME_CONFIGURATION_NAME).extendsFrom(provideRuntimeConfiguration);
    }

    private void configureEclipse(Project project, War war) {
        EclipseWtp eclipseWtp = configureEclipseWtp(project, war);
        project.task(JavaPlugin.ECLIPSE_TASK_NAME).dependsOn(eclipseWtp);
    }

    private EclipseWtp configureEclipseWtp(final Project project, final War war) {
        final EclipseWtp eclipseWtp = (EclipseWtp) project.createTask(GUtil.map("type", EclipseWtp.class), ECLIPSE_WTP_TASK_NAME);

        eclipseWtp.conventionMapping(GUtil.map(
                "warResourceMappings", new ConventionValue() {
                    public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                        Map resourceMappings = WrapUtil.toMap("/WEB-INF/classes", GUtil.addLists(java(convention).getSrcDirs(), java(convention).getResourceDirs()));
                        resourceMappings.put("/", WrapUtil.toList(java(convention).getWebAppDir()));
                        return resourceMappings;
                    }
                },
                "outputDirectory", new ConventionValue() {
                    public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                        return java(convention).getClassesDir();
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
                                ((Task) conventionAwareObject).getProject().getConfigurations().get(JavaPlugin.RUNTIME_CONFIGURATION_NAME).getAllDependencies(),
                                DependencySpecs.type(Type.PROJECT))
                        );
                    }
                }));

        // todo: When we refactor the way we resolve project dependencies this step might become obsolete
        createDependencyOnEclipseProjectTaskOfDependentProjects(project, eclipseWtp);

        return eclipseWtp;
    }

    private JavaPluginConvention java(Convention convention) {
        return convention.getPlugin(JavaPluginConvention.class);
    }

    private void createDependencyOnEclipseProjectTaskOfDependentProjects(Project project, EclipseWtp eclipseWtp) {
        Set<Dependency> projectDependencies = Specs.filterIterable(
                project.getConfigurations().get(JavaPlugin.RUNTIME_CONFIGURATION_NAME).getDependencies(),
                DependencySpecs.type(Type.PROJECT)
        );

        for (Dependency dependentProject : projectDependencies) {
            eclipseWtp.dependsOn(((ProjectDependency) dependentProject).getDependencyProject().getPath() + ":eclipseProject");
        }
    }

}
