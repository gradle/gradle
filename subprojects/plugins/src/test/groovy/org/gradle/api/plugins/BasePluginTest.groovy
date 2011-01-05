/*
 * Copyright 2010 the original author or authors.
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

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.internal.tasks.DefaultTaskDependency
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.Upload
import org.gradle.util.HelperUtil
import org.junit.Test
import static org.gradle.util.Matchers.*
import static org.hamcrest.Matchers.*
import static org.junit.Assert.*
import org.gradle.api.Task
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.bundling.Zip
import org.gradle.api.tasks.bundling.Tar
import org.gradle.api.artifacts.Dependency
import org.gradle.util.WrapUtil
import org.gradle.api.internal.artifacts.configurations.Configurations

/**
 * @author Hans Dockter
 */
class BasePluginTest {
    private final Project project = HelperUtil.createRootProject()
    private final BasePlugin plugin = new BasePlugin()

    @Test public void addsConventionObject() {
        plugin.apply(project)

        assertThat(project.convention.plugins.base, instanceOf(BasePluginConvention))
    }

    @Test public void createsTasksAndAppliesMappings() {
        plugin.apply(project)

        def task = project.tasks[BasePlugin.CLEAN_TASK_NAME]
        assertThat(task, instanceOf(Delete))
        assertThat(task, dependsOn())
        assertThat(task.targetFiles.files, equalTo([project.buildDir] as Set))

        task = project.tasks[BasePlugin.ASSEMBLE_TASK_NAME]
        assertThat(task, instanceOf(DefaultTask))
    }

    @Test public void addsRulesWhenAConfigurationIsAdded() {
        plugin.apply(project)

        assertThat(project.tasks.rules.size(), equalTo(3))
    }

    @Test public void addsImplicitTasksForConfiguration() {
        plugin.apply(project)

        Task producer = [getName: {-> 'producer'}] as Task
        PublishArtifact artifactStub = [getBuildDependencies: {-> new DefaultTaskDependency().add(producer) }] as PublishArtifact

        project.configurations.getByName('archives').addArtifact(artifactStub)

        def task = project.tasks['buildArchives']
        assertThat(task, instanceOf(DefaultTask))
        assertThat(task, dependsOn('producer'))

        task = project.tasks['uploadArchives']
        assertThat(task, instanceOf(Upload))
        assertThat(task, dependsOn('producer'))

        project.configurations.add('conf').addArtifact(artifactStub)

        task = project.tasks['buildConf']
        assertThat(task, instanceOf(DefaultTask))
        assertThat(task, dependsOn('producer'))

        task = project.tasks['uploadConf']
        assertThat(task, instanceOf(Upload))
        assertThat(task, dependsOn('producer'))
        assertThat(task.configuration, sameInstance(project.configurations.conf))
    }

    @Test public void addsACleanRule() {
        plugin.apply(project)

        Task test = project.task('test')
        test.outputs.files(project.buildDir)

        Task cleanTest = project.tasks['cleanTest']
        assertThat(cleanTest, instanceOf(Delete))
        assertThat(cleanTest.delete, equalTo([test.outputs.files] as Set))
    }

    @Test public void appliesMappingsForArchiveTasks() {
        plugin.apply(project)

        project.version = '1.0'

        def task = project.tasks.add('someJar', Jar)
        assertThat(task.destinationDir, equalTo(project.libsDir))
        assertThat(task.version, equalTo(project.version))
        assertThat(task.baseName, equalTo(project.archivesBaseName))

        assertThat(project.tasks[BasePlugin.ASSEMBLE_TASK_NAME], dependsOn('someJar'))

        task = project.tasks.add('someZip', Zip)
        assertThat(task.destinationDir, equalTo(project.distsDir))
        assertThat(task.version, equalTo(project.version))
        assertThat(task.baseName, equalTo(project.archivesBaseName))

        assertThat(project.tasks[BasePlugin.ASSEMBLE_TASK_NAME], dependsOn('someJar', 'someZip'))

        task = project.tasks.add('someTar', Tar)
        assertThat(task.destinationDir, equalTo(project.distsDir))
        assertThat(task.version, equalTo(project.version))
        assertThat(task.baseName, equalTo(project.archivesBaseName))

        assertThat(project.tasks[BasePlugin.ASSEMBLE_TASK_NAME], dependsOn('someJar', 'someZip', 'someTar'))
    }

    @Test public void usesNullVersionWhenProjectVersionNotSpecified() {
        plugin.apply(project)

        def task = project.tasks.add('someJar', Jar)
        assertThat(task.version, nullValue())

        project.version = '1.0'

        task = project.tasks.add('someOtherJar', Jar)
        assertThat(task.version, equalTo('1.0'))
    }

    @Test public void addsConfigurationsToTheProject() {
        plugin.apply(project)

        assertThat(project.status, equalTo("integration"))

        def configuration = project.configurations.getByName(Dependency.DEFAULT_CONFIGURATION)
        assertThat(Configurations.getNames(configuration.extendsFrom, false), equalTo(WrapUtil.toSet(Dependency.ARCHIVES_CONFIGURATION)))
        assertTrue(configuration.visible)
        assertTrue(configuration.transitive)

        configuration = project.configurations.getByName(Dependency.ARCHIVES_CONFIGURATION)
        assertThat(Configurations.getNames(configuration.extendsFrom, false), equalTo(WrapUtil.toSet()))
        assertTrue(configuration.visible)
        assertTrue(configuration.transitive)
    }
}
