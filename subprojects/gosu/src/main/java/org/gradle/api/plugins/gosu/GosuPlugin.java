/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.plugins.gosu;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.internal.plugins.DslObject;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.GosuRuntime;
import org.gradle.api.tasks.GosuSourceSet;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.gosu.GosuDoc;

/**
 * Gradle plugin for the Gosu language
 */
public class GosuPlugin implements Plugin<Project> {

    public static final String GOSU_DOC_TASK_NAME = "gosudoc";

    public void apply(Project project) {
        project.getPluginManager().apply(GosuBasePlugin.class);
        project.getPluginManager().apply(JavaPlugin.class);

        refreshTestRuntimeClasspath(project);
        configureGosuDoc(project);
    }

    /**
     * Ensures that the runtime dependency on gosu-core is included the testRuntime's classpath
     * @param project
     */
    private void refreshTestRuntimeClasspath(final Project project) {
        final JavaPluginConvention pluginConvention = project.getConvention().getPlugin(JavaPluginConvention.class);
        GosuRuntime gosuRuntime = project.getExtensions().getByType(GosuRuntime.class);

        SourceSet main = pluginConvention.getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME);
        SourceSet test = pluginConvention.getSourceSets().getByName(SourceSet.TEST_SOURCE_SET_NAME);

        test.setRuntimeClasspath(project.files(
            test.getOutput(),
            main.getOutput(),
            project.getConfigurations().getByName(JavaPlugin.TEST_RUNTIME_CONFIGURATION_NAME),
            gosuRuntime.inferGosuClasspath(project.getConfigurations().getByName(JavaPlugin.TEST_COMPILE_CONFIGURATION_NAME))));
    }

    private void configureGosuDoc(final Project project) {
        GosuDoc gosuDoc = project.getTasks().create(GOSU_DOC_TASK_NAME, GosuDoc.class);
        gosuDoc.setDescription("Generates Gosudoc API documentation for the main source code.");
        gosuDoc.setGroup(JavaBasePlugin.DOCUMENTATION_GROUP);

        JavaPluginConvention convention = project.getConvention().getPlugin(JavaPluginConvention.class);
        SourceSet sourceSet = convention.getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME);
        gosuDoc.setClasspath(sourceSet.getOutput().plus(sourceSet.getCompileClasspath()));

        GosuSourceSet gosuSourceSet = new DslObject(sourceSet).getConvention().getPlugin(GosuSourceSet.class);
        gosuDoc.setSource(gosuSourceSet.getGosu());
    }
}
