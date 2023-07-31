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
import org.gradle.util.internal.DefaultGradleVersion

class ApplicationPluginTest extends AbstractProjectBuilderSpec {
    private final ApplicationPlugin plugin = TestUtil.newInstance(ApplicationPlugin)

    def "applies JavaPlugin and checks default values"() {
        when:
        plugin.apply(project)

        then:
        project.plugins.hasPlugin(JavaPlugin.class)

        def application = project.extensions.getByName('application')
        application instanceof JavaApplication
        application.applicationName == project.name
        application.applicationDefaultJvmArgs == []
        application.applicationDistribution instanceof CopySpec
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

    def "adds startScripts task to project"() {
        when:
        plugin.apply(project)

        then:
        def task = project.tasks[ApplicationPlugin.TASK_START_SCRIPTS_NAME]
        task instanceof CreateStartScripts
        task.applicationName.get() == project.applicationName
        task.outputDir.getAsFile().get() == project.file('build/scripts')
        task.defaultJvmOpts.get() == []
        task.gitRef.get() == DefaultGradleVersion.current().getGitRevision()
    }

    def "adds distZip task to project"() {
        when:
        plugin.apply(project)

        then:
        def task = project.tasks[ApplicationPlugin.TASK_DIST_ZIP_NAME]
        task instanceof Zip
        task.archiveFileName.get() == "${project.application.applicationName}.zip"
    }

    def "adds distTar task to project"() {
        when:
        plugin.apply(project)

        then:
        def task = project.tasks[ApplicationPlugin.TASK_DIST_TAR_NAME]
        task instanceof Tar
        task.archiveFileName.get() == "${project.application.applicationName}.tar"
    }

    def "applicationName is configurable"() {
        when:
        plugin.apply(project)
        project.application.applicationName = "SuperApp"

        then:
        def startScriptsTask = project.tasks[ApplicationPlugin.TASK_START_SCRIPTS_NAME]
        startScriptsTask.applicationName.get() == 'SuperApp'

        def installTest = project.tasks[DistributionPlugin.TASK_INSTALL_NAME]
        installTest.destinationDir == project.file("build/install/SuperApp")

        def distZipTask = project.tasks[ApplicationPlugin.TASK_DIST_ZIP_NAME]
        distZipTask.archiveFileName.get() == "SuperApp.zip"
    }

    def "executableDir is configurable"() {
        when:
        plugin.apply(project)
        project.application.applicationName = "myApp"
        project.application.executableDir = "custom_bin"

        then:
        def startScripts = project.tasks[ApplicationPlugin.TASK_START_SCRIPTS_NAME]
        startScripts.executableDir.get() == "custom_bin"
    }

    def "applicationDefaultJvmArgs in project delegates to jvmArgs in run task"() {
        when:
        plugin.apply(project)
        project.application.applicationDefaultJvmArgs = ['-Dfoo=bar', '-Xmx500m']

        then:
        def run = project.tasks[ApplicationPlugin.TASK_RUN_NAME]
        run.jvmArgs == ['-Dfoo=bar', '-Xmx500m']
    }

    def "applicationDefaultJvmArgs in project delegates to defaultJvmOpts in startScripts task"() {
        when:
        plugin.apply(project)
        project.application.applicationDefaultJvmArgs = ['-Dfoo=bar', '-Xmx500m']

        then:
        def startScripts = project.tasks[ApplicationPlugin.TASK_START_SCRIPTS_NAME]
        startScripts.defaultJvmOpts.get() == ['-Dfoo=bar', '-Xmx500m']
    }

    def "module path inference is turned on for all tasks by default"() {
        when:
        plugin.apply(project)

        then:
        project.tasks.getByName("compileJava").modularity.inferModulePath.get()
        project.tasks.getByName("compileTestJava").modularity.inferModulePath.get()
        project.tasks.getByName("test").modularity.inferModulePath.get()
        project.tasks.getByName("run").modularity.inferModulePath.get()
        project.tasks.getByName("startScripts").modularity.inferModulePath.get()
    }

    def "module path inference can be turned off for all tasks"() {
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
