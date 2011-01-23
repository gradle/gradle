/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.plugins

import spock.lang.Specification
import org.gradle.api.Project
import org.gradle.util.HelperUtil
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.SourceSet
import org.gradle.util.Matchers

class ApplicationPluginTest extends Specification {
    private final Project project = HelperUtil.createRootProject();
    private final ApplicationPlugin plugin = new ApplicationPlugin();

    public void appliesJavaPluginAndAddsConventionObject() {
        when:
        plugin.apply(project);

        then:
        project.plugins.hasPlugin(JavaPlugin.class)
        project.convention.getPlugin(ApplicationPluginConvention.class) != null
    }

    public void addsTasksToProject() {
        when:
        plugin.apply(project);

        then:
        def task = project.tasks[ApplicationPlugin.TASK_RUN_NAME]
        task instanceof JavaExec
        task.classpath == project.sourceSets[SourceSet.MAIN_SOURCE_SET_NAME].runtimeClasspath
        task Matchers.dependsOn('classes')
    }

    public void setMainClassNameSetsMainInRunTask() {
        when:
        plugin.apply(project);
        project.mainClassName = "Acme";

        then:
        def run = project.tasks[ApplicationPlugin.TASK_RUN_NAME]
        run.main == "Acme"
    }
}
