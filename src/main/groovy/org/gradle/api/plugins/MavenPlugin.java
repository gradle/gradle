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
import org.gradle.api.artifacts.*;
import org.gradle.api.artifacts.maven.Conf2ScopeMappingContainer;
import org.gradle.api.internal.IConventionAware;
import org.gradle.api.internal.project.PluginRegistry;
import org.gradle.api.tasks.ConventionValue;
import org.gradle.api.tasks.Upload;
import org.gradle.util.GUtil;
import org.gradle.util.WrapUtil;

import java.util.Map;

/**
 * @author Hans Dockter
 */
public class MavenPlugin implements Plugin {
    public static final int COMPILE_PRIORITY = 300;
    public static final int RUNTIME_PRIORITY = 200;
    public static final int TEST_COMPILE_PRIORITY = 150;
    public static final int TEST_RUNTIME_PRIORITY = 100;

    public static final int PROVIDED_COMPILE_PRIORITY = COMPILE_PRIORITY + 100;
    public static final int PROVIDED_RUNTIME_PRIORITY = COMPILE_PRIORITY + 150;

    public static final String INSTALL_TASK_NAME = "install";

    public void apply(Project project, PluginRegistry pluginRegistry, Map<String, ?> customValues) {
        setConventionMapping(project);
        addConventionObject(project, customValues);
        if (isJavaPluginApplied(project)) {
            configureJavaScopeMappings(project.getRepositories(), project.getConfigurations());
            configureInstall(project);
        }
        if (isWarPluginApplied(project)) {
            configureWarScopeMappings(project.getRepositories(), project.getConfigurations());
        }
    }

    private void setConventionMapping(final Project project) {
        Map mapping = GUtil.map(
                "mavenPomDir", new ConventionValue() {
                    public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                        return ((MavenPluginConvention) convention.getPlugins().get("maven")).getPomDir();
                    }
                },
                "configurationContainer", new ConventionValue() {
                    public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                        return project.getConfigurations();
                    }
                },
                "mavenScopeMappings", new ConventionValue() {
                    public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                        return ((MavenPluginConvention) convention.getPlugins().get("maven")).getConf2ScopeMappings();
                    }
                });
        project.getRepositoryHandlerFactory().getConventionMapping().putAll(mapping);
    }

    private void addConventionObject(Project project, Map<String, ?> customValues) {
        MavenPluginConvention mavenConvention = new MavenPluginConvention(project, customValues);
        Convention convention = project.getConvention();
        convention.getPlugins().put("maven", mavenConvention);
    }

    private boolean isJavaPluginApplied(Project project) {
        return project.getAppliedPlugins().contains(JavaPlugin.class);
    }

    private boolean isWarPluginApplied(Project project) {
        return project.getAppliedPlugins().contains(WarPlugin.class);
    }

    private void configureJavaScopeMappings(ResolverContainer resolverFactory, ConfigurationContainer configurations) {
        resolverFactory.getMavenScopeMappings().addMapping(COMPILE_PRIORITY, configurations.getByName(JavaPlugin.COMPILE_CONFIGURATION_NAME),
                Conf2ScopeMappingContainer.COMPILE);
        resolverFactory.getMavenScopeMappings().addMapping(RUNTIME_PRIORITY, configurations.getByName(JavaPlugin.RUNTIME_CONFIGURATION_NAME),
                Conf2ScopeMappingContainer.RUNTIME);
        resolverFactory.getMavenScopeMappings().addMapping(TEST_COMPILE_PRIORITY, configurations.getByName(JavaPlugin.TEST_COMPILE_CONFIGURATION_NAME),
                Conf2ScopeMappingContainer.TEST);
        resolverFactory.getMavenScopeMappings().addMapping(TEST_RUNTIME_PRIORITY, configurations.getByName(JavaPlugin.TEST_RUNTIME_CONFIGURATION_NAME),
                Conf2ScopeMappingContainer.TEST);
    }

    private void configureWarScopeMappings(ResolverContainer resolverContainer, ConfigurationContainer configurations) {
        resolverContainer.getMavenScopeMappings().addMapping(PROVIDED_COMPILE_PRIORITY, configurations.getByName(WarPlugin.PROVIDED_COMPILE_CONFIGURATION_NAME),
                Conf2ScopeMappingContainer.PROVIDED);
        resolverContainer.getMavenScopeMappings().addMapping(PROVIDED_RUNTIME_PRIORITY, configurations.getByName(WarPlugin.PROVIDED_RUNTIME_CONFIGURATION_NAME),
                Conf2ScopeMappingContainer.PROVIDED);
    }

    private void configureInstall(Project project) {
        Upload installUpload = project.getTasks().add(INSTALL_TASK_NAME, Upload.class);
        Configuration configuration = project.getConfigurations().getByName(Dependency.ARCHIVES_CONFIGURATION);
        installUpload.dependsOn(configuration.getBuildArtifacts());
        installUpload.setConfiguration(configuration);
        installUpload.getRepositories().mavenInstaller(WrapUtil.toMap("name", "maven-installer"));
        installUpload.setDescription("Does a maven install of the archives artifacts into the local .m2 cache.");
    }
}
