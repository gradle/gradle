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
import org.gradle.api.artifacts.ConfigurationPublishInstruction;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.internal.artifacts.DependencyManagerInternal;
import org.gradle.api.internal.project.PluginRegistry;
import org.gradle.api.tasks.Upload;
import org.gradle.util.GUtil;

import java.util.Map;

/**
 * @author Hans Dockter
 */
public class MavenPlugin implements Plugin {
    public static final String INSTALL = "install";

    public void apply(Project project, PluginRegistry pluginRegistry, Map<String, ?> customValues) {
        pluginRegistry.apply(JavaPlugin.class, project, customValues);
        configureInstall(project, project.getConvention().getPlugin(JavaPluginConvention.class));
    }

    private void configureInstall(Project project, JavaPluginConvention javaConvention) {
        Upload installUpload = (Upload) project.createTask(GUtil.map("type", Upload.class), INSTALL);
        ConfigurationPublishInstruction publishInstruction = new ConfigurationPublishInstruction(Dependency.MASTER_CONFIGURATION);
        publishInstruction.getModuleDescriptor().setPublish(true);
        installUpload.setPublishInstruction(publishInstruction);
        installUpload.getUploadResolvers().setDependencyManager((DependencyManagerInternal) project.getDependencies());
        installUpload.getUploadResolvers().setMavenPomDir(javaConvention.getUploadLibsPomDir());
        installUpload.getUploadResolvers().addMavenInstaller("maven-installer");
    }

}
