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

import org.gradle.api.Project
import org.gradle.api.tasks.application.CreateStartScripts
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.bundling.Zip
import org.gradle.util.HelperUtil
import org.gradle.util.Matchers
import spock.lang.Specification
import org.gradle.api.tasks.Sync

class ApplicationPluginTest extends Specification {
    private final Project project = HelperUtil.createRootProject();
    private final ApplicationPlugin plugin = new ApplicationPlugin();

    public void appliesJavaPluginAndAddsConventionObject() {
        when:
        plugin.apply(project)

        then:
        project.plugins.hasPlugin(JavaPlugin.class)
        project.convention.getPlugin(ApplicationPluginConvention.class) != null
    }

    public void addsRunTasksToProject() {
        when:
        plugin.apply(project)

        then:
        def task = project.tasks[ApplicationPlugin.TASK_RUN_NAME]
        task instanceof JavaExec
        task.classpath == project.sourceSets[SourceSet.MAIN_SOURCE_SET_NAME].runtimeClasspath
        task Matchers.dependsOn('classes')
    }

    public void addsCreateStartScriptsTaskToProject() {
        when:
        plugin.apply(project)

        then:
        def task = project.tasks[ApplicationPlugin.TASK_START_SCRIPTS_NAME]
        task instanceof CreateStartScripts
        task.applicationName == project.name
        task.outputDir == project.file('build/scripts')
    }

    public void addsInstallTaskToProjectWithDefaultTarget() {
        when:
        plugin.apply(project)

        then:
        def task = project.tasks[ApplicationPlugin.TASK_INSTALL_NAME]
        task instanceof Sync
        task.destinationDir == project.file("build/install")
    }

    public void addsDistZipTaskToProject() {
        when:
        plugin.apply(project)

        then:
        def task = project.tasks[ApplicationPlugin.TASK_DIST_ZIP_NAME]
        task instanceof Zip
    }

    public void setMainClassNameSetsMainInRunTask() {
        when:
        plugin.apply(project)
        project.mainClassName = "Acme";

        then:
        def run = project.tasks[ApplicationPlugin.TASK_RUN_NAME]
        run.main == "Acme"
    }

    public void setMainClassNameSetsMainClassNameInCreateStartScriptsTask() {
        when:
        plugin.apply(project);
        project.mainClassName = "Acme"

        then:
        def startScripts = project.tasks[ApplicationPlugin.TASK_START_SCRIPTS_NAME]
        startScripts.mainClassName == "Acme"
    }
}
