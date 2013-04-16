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
package org.gradle.api.plugins;

import org.gradle.api.Task;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.maven.Conf2ScopeMapping;
import org.gradle.api.artifacts.maven.Conf2ScopeMappingContainer;
import org.gradle.api.artifacts.maven.MavenResolver;
import org.gradle.api.internal.plugins.DslObject;
import org.gradle.api.internal.project.DefaultProject;
import org.gradle.api.tasks.Upload;
import org.gradle.util.HelperUtil;

import java.io.File;
import java.util.Set;

import static org.gradle.util.WrapUtil.toSet;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

/**
 * @author Hans Dockter
 */
public class MavenPluginTest {
    private final DefaultProject project = HelperUtil.createRootProject();

    @org.junit.Test
    public void addsConventionToProject() {
        project.getPlugins().apply(MavenPlugin.class);

        assertThat(project.getConvention().getPlugin(MavenPluginConvention.class), notNullValue());
    }
    
    @org.junit.Test
    public void defaultConventionValues() {
        project.getPlugins().apply(MavenPlugin.class);

        MavenPluginConvention convention = project.getConvention().getPlugin(MavenPluginConvention.class);
        assertThat(convention.getMavenPomDir(), equalTo(new File(project.getBuildDir(), "poms")));
        assertThat(convention.getConf2ScopeMappings(), notNullValue());
    }

    @org.junit.Test
    public void applyWithWarPlugin() {
        project.getPlugins().apply(WarPlugin.class);
        project.getPlugins().apply(MavenPlugin.class);

        assertHasConfigurationAndMapping(project, WarPlugin.PROVIDED_COMPILE_CONFIGURATION_NAME, Conf2ScopeMappingContainer.PROVIDED,
                MavenPlugin.PROVIDED_COMPILE_PRIORITY);
        assertHasConfigurationAndMapping(project, WarPlugin.PROVIDED_RUNTIME_CONFIGURATION_NAME, Conf2ScopeMappingContainer.PROVIDED,
                MavenPlugin.PROVIDED_RUNTIME_PRIORITY);

        Task task = project.getTasks().getByName(MavenPlugin.INSTALL_TASK_NAME);
        Set dependencies = task.getTaskDependencies().getDependencies(task);
        assertThat(dependencies, equalTo((Set) toSet(project.getTasks().getByName(WarPlugin.WAR_TASK_NAME))));
    }

    private void assertHasConfigurationAndMapping(DefaultProject project, String configurationName, String scope, int priority) {
        Conf2ScopeMappingContainer scopeMappingContainer = project.getConvention().getPlugin(MavenPluginConvention.class).getConf2ScopeMappings();
        ConfigurationContainer configurationContainer = project.getConfigurations();
        Conf2ScopeMapping mapping = scopeMappingContainer.getMappings().get(configurationContainer.getByName(configurationName));
        assertThat(mapping.getScope(), equalTo(scope));
        assertThat(mapping.getPriority(), equalTo(priority));
    }

    @org.junit.Test
    public void applyWithJavaPlugin() {
        project.getPlugins().apply(JavaPlugin.class);
        project.getPlugins().apply(MavenPlugin.class);

        assertHasConfigurationAndMapping(project, JavaPlugin.COMPILE_CONFIGURATION_NAME, Conf2ScopeMappingContainer.COMPILE,
                MavenPlugin.COMPILE_PRIORITY);
        assertHasConfigurationAndMapping(project, JavaPlugin.RUNTIME_CONFIGURATION_NAME, Conf2ScopeMappingContainer.RUNTIME,
                MavenPlugin.RUNTIME_PRIORITY);
        assertHasConfigurationAndMapping(project, JavaPlugin.TEST_COMPILE_CONFIGURATION_NAME, Conf2ScopeMappingContainer.TEST,
                MavenPlugin.TEST_COMPILE_PRIORITY);
        assertHasConfigurationAndMapping(project, JavaPlugin.TEST_RUNTIME_CONFIGURATION_NAME, Conf2ScopeMappingContainer.TEST,
                MavenPlugin.TEST_RUNTIME_PRIORITY);

        Task task = project.getTasks().getByName(MavenPlugin.INSTALL_TASK_NAME);
        Set dependencies = task.getTaskDependencies().getDependencies(task);
        assertEquals(dependencies, toSet(project.getTasks().getByName(JavaPlugin.JAR_TASK_NAME)));
    }

    @org.junit.Test
    public void addsAndConfiguresAnInstallTask() {
        project.getPlugins().apply(JavaPlugin.class);
        project.getPlugins().apply(MavenPlugin.class);

        Upload task = project.getTasks().withType(Upload.class).getByName(MavenPlugin.INSTALL_TASK_NAME);
        assertThat(task.getRepositories().get(0), instanceOf(MavenResolver.class));
    }

    @org.junit.Test
    public void addsConventionMappingToTheRepositoryContainerOfEachUploadTask() {
        project.getPlugins().apply(JavaPlugin.class);
        project.getPlugins().apply(MavenPlugin.class);

        Upload task = project.getTasks().withType(Upload.class).getByName(MavenPlugin.INSTALL_TASK_NAME);
        MavenRepositoryHandlerConvention convention = new DslObject(task.getRepositories()).getConvention().getPlugin(MavenRepositoryHandlerConvention.class);
        assertThat(convention, notNullValue());

        task = project.getTasks().create("customUpload", Upload.class);
        convention = new DslObject(task.getRepositories()).getConvention().getPlugin(MavenRepositoryHandlerConvention.class);
        assertThat(convention, notNullValue());
    }

    @org.junit.Test
    public void applyWithoutWarPlugin() {
        project.getPlugins().apply(MavenPlugin.class);

        assertThat(project.getConfigurations().findByName(WarPlugin.PROVIDED_COMPILE_CONFIGURATION_NAME),
                nullValue());
    }

    @org.junit.Test
    public void applyWithoutJavaPlugin() {
        project.getPlugins().apply(MavenPlugin.class);

        assertThat(project.getConfigurations().findByName(JavaPlugin.COMPILE_CONFIGURATION_NAME),
                nullValue());
    }
}
