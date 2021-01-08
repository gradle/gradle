/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.internal.plugins;

import org.apache.commons.lang.StringUtils;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.tasks.TaskContainer;

public class BuildConfigurationRule extends AbstractRule {

    public static final String PREFIX = "build";

    private final ConfigurationContainer configurations;
    private final TaskContainer tasks;

    public BuildConfigurationRule(ConfigurationContainer configurations, TaskContainer tasks) {
        this.configurations = configurations;
        this.tasks = tasks;
    }

    @Override
    public String getDescription() {
        return "Pattern: " + PREFIX + "<ConfigurationName>: Assembles the artifacts of a configuration.";
    }

    @Override
    public void apply(String taskName) {
        if (taskName.startsWith(PREFIX)) {
            String configurationName = StringUtils.uncapitalize(taskName.substring(PREFIX.length()));
            Configuration configuration = configurations.findByName(configurationName);

            if (configuration != null) {
                Task task = tasks.create(taskName);
                task.dependsOn(configuration.getAllArtifacts());
                task.setDescription("Builds the artifacts belonging to " + configuration + ".");
            }
        }
    }
}
