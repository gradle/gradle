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

import org.apache.ivy.core.module.descriptor.Configuration;
import org.gradle.api.DependencyManager;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.internal.project.PluginRegistry;
import org.gradle.api.tasks.bundling.Bundle;

import java.util.Map;

/**
 * @author Hans Dockter
 */
public class WarPlugin implements Plugin {
    public static final String PROVIDED_COMPILE = "providedCompile";
    public static final String PROVIDED_RUNTIME = "providedRuntime";

    public void apply(Project project, PluginRegistry pluginRegistry, Map customValues) {
        pluginRegistry.apply(JavaPlugin.class, project, pluginRegistry, customValues);
        project.task(project.getArchivesTaskBaseName() + "_jar").setEnabled(false);
        ((Bundle) project.task("libs")).war();
        configureDependencyManager(project.getDependencies());
    }

    public void configureDependencyManager(DependencyManager dependencyManager) {
        dependencyManager.addConfiguration(
                new Configuration(PROVIDED_COMPILE, Configuration.Visibility.PRIVATE, null, null, true, null));
        dependencyManager.addConfiguration(
                new Configuration(PROVIDED_RUNTIME, Configuration.Visibility.PRIVATE, null, new String[] {PROVIDED_COMPILE}, true, null));
        dependencyManager.addConfiguration(new Configuration(JavaPlugin.COMPILE, Configuration.Visibility.PRIVATE, null, new String[]
                {PROVIDED_COMPILE}, false, null));
        dependencyManager.addConfiguration(new Configuration(JavaPlugin.RUNTIME, Configuration.Visibility.PRIVATE, null, new String[]
                {JavaPlugin.COMPILE, PROVIDED_RUNTIME}, true, null));
    }
}
