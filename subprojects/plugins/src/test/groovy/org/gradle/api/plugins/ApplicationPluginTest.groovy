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

import org.gradle.api.distribution.plugins.DistributionPlugin
import org.gradle.api.file.CopySpec
import org.gradle.api.internal.tasks.JvmConstants
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskDependencyMatchers
import org.gradle.api.tasks.application.CreateStartScripts
import org.gradle.api.tasks.bundling.Tar
import org.gradle.api.tasks.bundling.Zip
import org.gradle.test.fixtures.AbstractProjectBuilderSpec
import org.gradle.util.TestUtil

class ApplicationPluginTest extends AbstractProjectBuilderSpec {
    private final ApplicationPlugin plugin = TestUtil.newInstance(ApplicationPlugin)

    def "applies JavaPlugin and adds convention object with default values"() {
        when:
        plugin.apply(project)

        then:
        project.plugins.hasPlugin(JavaPlugin.class)

        project.convention.getPlugin(ApplicationPluginConvention.class) != null
        project.applicationName == project.name
        project.mainClassName == null
        project.applicationDefaultJvmArgs == []
        project.applicationDistribution instanceof CopySpec

        def application = project.extensions.getByName('application')
        application instanceof JavaApplication
        application.applicationName.get() == project.name
        application.applicationDistribution.is(project.applicationDistribution)
    }

    def "adds run task to project"() {
        when:
        plugin.apply(project)

        then:
        def task = project.tasks[ApplicationPlugin.TASK_RUN_NAME]
        task instanceof JavaExec
        task.classpath.from.files == [project.sourceSets[SourceSet.MAIN_SOURCE_SET_NAME].runtimeClasspath.files]
        task TaskDependencyMatchers.dependsOn('classes', JvmConstants.COMPILE_JAVA_TASK_NAME)
    }

    void "adds startScripts task to project"() {
        when:
        plugin.apply(project)

        then:
        def task = project.tasks[ApplicationPlugin.TASK_START_SCRIPTS_NAME]
        task instanceof CreateStartScripts
        task.applicationName.get() == project.applicationName
        task.outputDir == project.file('build/scripts')
        task.defaultJvmOpts == []
    }

    def "adds distZip task to project"() {
        when:
        plugin.apply(project)

        then:
        def task = project.tasks[ApplicationPlugin.TASK_DIST_ZIP_NAME]
        task instanceof Zip
        task.archiveFileName.get() == "${project.applicationName}.zip"
    }

    def "adds distTar task to project"() {
        when:
        plugin.apply(project)

        then:
        def task = project.tasks[ApplicationPlugin.TASK_DIST_TAR_NAME]
        task instanceof Tar
        task.archiveFileName.get() == "${project.applicationName}.tar"
    }

    void "applicationName is configurable"() {
        when:
        plugin.apply(project)
        project.applicationName = "SuperApp";

        then:
        def startScriptsTask = project.tasks[ApplicationPlugin.TASK_START_SCRIPTS_NAME]
        startScriptsTask.applicationName.get() == 'SuperApp'

        def installTest = project.tasks[DistributionPlugin.TASK_INSTALL_NAME]
        installTest.destinationDir == project.file("build/install/SuperApp")

        def distZipTask = project.tasks[ApplicationPlugin.TASK_DIST_ZIP_NAME]
        distZipTask.archiveFileName.get() == "SuperApp.zip"
    }

    void "executableDir is configurable"() {
        when:
        plugin.apply(project)
        project.applicationName = "myApp";
        project.executableDir = "custom_bin";

        then:
        def startScripts = project.tasks[ApplicationPlugin.TASK_START_SCRIPTS_NAME]
        startScripts.executableDir.get() == "custom_bin"
    }

    void "mainClassName in project delegates to mainClassName in startScripts task"() {
        when:
        plugin.apply(project);
        project.mainClassName = "Acme"

        then:
        def startScripts = project.tasks[ApplicationPlugin.TASK_START_SCRIPTS_NAME]
        startScripts.mainClassName == "Acme"
    }

    void "applicationDefaultJvmArgs in project delegates to jvmArgs in run task"() {
        when:
        plugin.apply(project)
        project.applicationDefaultJvmArgs = ['-Dfoo=bar', '-Xmx500m']

        then:
        def run = project.tasks[ApplicationPlugin.TASK_RUN_NAME]
        run.jvmArgs == ['-Dfoo=bar', '-Xmx500m']
    }

    void "applicationDefaultJvmArgs in project delegates to defaultJvmOpts in startScripts task"() {
        when:
        plugin.apply(project)
        project.applicationDefaultJvmArgs = ['-Dfoo=bar', '-Xmx500m']

        then:
        def startScripts = project.tasks[ApplicationPlugin.TASK_START_SCRIPTS_NAME]
        startScripts.defaultJvmOpts == ['-Dfoo=bar', '-Xmx500m']
    }

    void "module path inference is turned on for all tasks by default"() {
        when:
        plugin.apply(project)

        then:
        project.tasks.getByName("compileJava").modularity.inferModulePath.get()
        project.tasks.getByName("compileTestJava").modularity.inferModulePath.get()
        project.tasks.getByName("test").modularity.inferModulePath.get()
        project.tasks.getByName("run").modularity.inferModulePath.get()
        project.tasks.getByName("startScripts").modularity.inferModulePath.get()
    }

    void "module path inference can be turned off for all tasks"() {
        when:
        plugin.apply(project)
        project.extensions.getByType(JavaPluginExtension).modularity.inferModulePath.set(false)

        then:
        !project.tasks.getByName("compileJava").modularity.inferModulePath.get()
        !project.tasks.getByName("compileTestJava").modularity.inferModulePath.get()
        !project.tasks.getByName("test").modularity.inferModulePath.get()
        !project.tasks.getByName("run").modularity.inferModulePath.get()
        !project.tasks.getByName("startScripts").modularity.inferModulePath.get()
    }
}
