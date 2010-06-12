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
import org.gradle.api.internal.project.DefaultProject;
import org.gradle.util.HelperUtil;
import static org.gradle.util.WrapUtil.toSet;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import java.util.Set;

/**
 * @author Hans Dockter
 */
public class MavenPluginTest {
    private final DefaultProject project = HelperUtil.createRootProject();
    private final MavenPlugin mavenPlugin = new MavenPlugin();

    @org.junit.Test
    public void applyWithWarPlugin() {
        project.getPlugins().apply(WarPlugin.class);
        mavenPlugin.apply(project);
        assertHasConfigurationAndMapping(project, WarPlugin.PROVIDED_COMPILE_CONFIGURATION_NAME, Conf2ScopeMappingContainer.PROVIDED,
                MavenPlugin.PROVIDED_COMPILE_PRIORITY);
        assertHasConfigurationAndMapping(project, WarPlugin.PROVIDED_RUNTIME_CONFIGURATION_NAME, Conf2ScopeMappingContainer.PROVIDED,
                MavenPlugin.PROVIDED_RUNTIME_PRIORITY);

        Task task = project.getTasks().getByName(MavenPlugin.INSTALL_TASK_NAME);
        Set dependencies = task.getTaskDependencies().getDependencies(task);
        assertThat(dependencies, equalTo((Set) toSet(project.getTasks().getByName(WarPlugin.WAR_TASK_NAME))));
    }

    private void assertHasConfigurationAndMapping(DefaultProject project, String configurationName, String scope, int priority) {
        Conf2ScopeMappingContainer scopeMappingContainer = project.getRepositories().getMavenScopeMappings();
        ConfigurationContainer configurationContainer = project.getConfigurations();
        Conf2ScopeMapping mapping = scopeMappingContainer.getMappings().get(configurationContainer.getByName(configurationName));
        assertThat(mapping.getScope(), equalTo(scope));
        assertThat(mapping.getPriority(), equalTo(priority));
    }

    @org.junit.Test
    public void applyWithJavaPlugin() {
        project.getPlugins().apply(JavaPlugin.class);
        mavenPlugin.apply(project);
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
    public void applyWithoutWarPlugin() {
        mavenPlugin.apply(project);
        assertThat(project.getConfigurations().findByName(WarPlugin.PROVIDED_COMPILE_CONFIGURATION_NAME),
                nullValue());
    }

    @org.junit.Test
    public void applyWithoutJavaPlugin() {
        mavenPlugin.apply(project);
        assertThat(project.getConfigurations().findByName(JavaPlugin.COMPILE_CONFIGURATION_NAME),
                nullValue());
    }
}
