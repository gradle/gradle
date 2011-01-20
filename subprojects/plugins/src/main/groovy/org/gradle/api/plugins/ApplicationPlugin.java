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
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.SourceSet;

/**
 * <p>A {@link Plugin} which runs a project as a Java Application.</p>
 *
 * @author Rene Groeschke
 */
public class ApplicationPlugin implements Plugin<Project> {

    public static final String TASK_RUN_NAME = "run";
    public static final String APPLICATION_PLUGIN_NAME = "application";
    public static final String APPLICATION_GROUP = APPLICATION_PLUGIN_NAME;

    public void apply(final Project project) {
        project.getPlugins().apply(JavaPlugin.class);
        project.getConvention().getPlugins().put("application", new ApplicationPluginConvention(project));
        JavaExec run = project.getTasks().add(TASK_RUN_NAME, JavaExec.class);
        run.setDescription("Runs this project as java application");
        run.setGroup(APPLICATION_GROUP);
        run.dependsOn(JavaPlugin.CLASSES_TASK_NAME);
        run.setClasspath(project.getConvention().getPlugin(JavaPluginConvention.class)
                .getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME).getRuntimeClasspath());
    }
}
