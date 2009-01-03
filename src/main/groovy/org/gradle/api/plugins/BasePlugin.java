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

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.tasks.Clean;
import org.gradle.api.tasks.ConventionValue;
import org.gradle.api.internal.project.PluginRegistry;
import org.gradle.api.internal.ConventionTask;
import org.gradle.util.GUtil;

import java.util.Map;

/**
 * <p>A {@link Plugin} which provides the basic skeleton for a project.</p>
 *
 * <p>This plugin adds the following tasks to the project:</p>
 *
 * <ul>
 *
 * <li>{@code init}</li>
 *
 * <li>{@code clean}</li>
 *
 * </ul>
 *
 * <p>This plugin adds the following convention objects to the project:</p>
 *
 * <ul>
 *
 * <li>{@link BasePluginConvention}</li>
 *
 * </ul>
 */
public class BasePlugin implements Plugin {
    public static final String CLEAN = "clean";
    public static final String INIT = "init";

    public void apply(final Project project, PluginRegistry pluginRegistry, Map<String, ?> customValues) {
        Convention convention = project.getConvention();
        convention.getPlugins().put("base", new BasePluginConvention(project));

        project.createTask(INIT);

        ((ConventionTask) project.createTask(GUtil.map("type", Clean.class), CLEAN)).
                conventionMapping(GUtil.map("dir", new ConventionValue() {
                    public Object getValue(Convention convention, Task task) {
                        return project.getBuildDir();
                    }
                }));
    }
}
