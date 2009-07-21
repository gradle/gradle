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

import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.internal.artifacts.publish.ArchivePublishArtifact;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.bundling.War;

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
    public static final String WAR_TASK_NAME = "war";

    public void use(Project project, ProjectPluginsContainer projectPluginsHandler) {
        projectPluginsHandler.usePlugin(JavaPlugin.class, project);
        project.getConvention().getPlugins().put("war", new WarPluginConvention(project));

        project.getTasks().withType(War.class).allTasks(new Action<War>() {
            public void execute(War task) {
                task.conventionMapping(DefaultConventionsToPropertiesMapping.WAR);
            }
        });
        
        War war = project.getTasks().add(WAR_TASK_NAME, War.class);
        war.setDescription("Generates a war archive with all the compiled classes, the web-app content and the libraries.");
        Configuration archivesConfiguration = project.getConfigurations().getByName(Dependency.ARCHIVES_CONFIGURATION);
        disableJarTaskAndRemoveFromArchivesConfiguration(project, archivesConfiguration);
        archivesConfiguration.addArtifact(new ArchivePublishArtifact(war));
        configureConfigurations(project.getConfigurations());
    }

    private void disableJarTaskAndRemoveFromArchivesConfiguration(Project project, Configuration archivesConfiguration) {
        Jar jarTask = (Jar) project.getTasks().getByName(JavaPlugin.JAR_TASK_NAME);
        jarTask.setEnabled(false);
        removeJarTaskFromArchivesConfiguration(archivesConfiguration, jarTask);
    }

    private void removeJarTaskFromArchivesConfiguration(Configuration archivesConfiguration, Jar jar) {
        // todo: There should be a richer connection between an ArchiveTask and a PublishArtifact
        for (PublishArtifact publishArtifact : archivesConfiguration.getAllArtifacts()) {
            if (publishArtifact.getFile().equals(jar.getArchivePath())) {
                archivesConfiguration.removeArtifact(publishArtifact);
            }
        }
    }

    public void configureConfigurations(ConfigurationContainer configurationContainer) {
        Configuration provideCompileConfiguration = configurationContainer.add(PROVIDED_COMPILE_CONFIGURATION_NAME).setVisible(false).
                setDescription("Additional compile classpath for libraries that should not be part of the WAR archive.");
        Configuration provideRuntimeConfiguration = configurationContainer.add(PROVIDED_RUNTIME_CONFIGURATION_NAME).setVisible(false).
                extendsFrom(provideCompileConfiguration).
                setDescription("Additional runtime classpath for libraries that should not be part of the WAR archive.");
        configurationContainer.getByName(JavaPlugin.COMPILE_CONFIGURATION_NAME).extendsFrom(provideCompileConfiguration);
        configurationContainer.getByName(JavaPlugin.RUNTIME_CONFIGURATION_NAME).extendsFrom(provideRuntimeConfiguration);
    }
}
