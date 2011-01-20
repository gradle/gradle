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

import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.SourceSet;
import org.gradle.util.HelperUtil;
import org.junit.Test;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * <p>Tests for {@link ApplicationPlugin}.</p>
 *
 * @author Rene Groeschke
 */
public class ApplicationPluginTest {
    private final Project project = HelperUtil.createRootProject();
    private final ApplicationPlugin plugin = new ApplicationPlugin();

    @Test
    public void appliesJavaPluginAndAddsConventionObject() {
        plugin.apply(project);
        assertTrue(project.getPlugins().hasPlugin(JavaPlugin.class));
        assertThat(project.getConvention().getPlugin(ApplicationPluginConvention.class), notNullValue());
    }

    @Test
    public void addsTasksToProject() {
        plugin.apply(project);

        Task task = project.getTasks().getByName(ApplicationPlugin.TASK_RUN_NAME);
        assertThat(task, instanceOf(JavaExec.class));
        assertThat(task.property("classpath"), equalTo((Object) project.getConvention().getPlugin(JavaPluginConvention.class)
            .getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME).getRuntimeClasspath()));
    }

    @Test
    public void setMainClassNameSetsMainInRunTask() {
        plugin.apply(project);

        JavaExec run = project.getTasks().withType(JavaExec.class).findByName("run");
        project.getConvention().getPlugin(ApplicationPluginConvention.class).setMainClassName("Acme");
        assertThat(run.getMain(), equalTo("Acme"));
    }
}
