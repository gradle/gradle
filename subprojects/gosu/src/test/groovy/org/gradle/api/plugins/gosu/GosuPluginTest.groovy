/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.plugins.gosu

import org.gradle.api.Project
import org.gradle.api.file.FileCollectionMatchers
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.reporting.ReportingExtension
import org.gradle.api.tasks.gosu.GosuCompile
import org.gradle.api.tasks.gosu.GosuDoc
import org.gradle.util.TestUtil
import org.junit.Ignore
import org.junit.Test

import static org.gradle.api.tasks.TaskDependencyMatchers.dependsOn
import static org.gradle.util.WrapUtil.toLinkedSet
import static org.hamcrest.Matchers.*
import static org.junit.Assert.assertThat
import static org.junit.Assert.assertTrue

class GosuPluginTest {
    private final Project project = TestUtil.createRootProject()
    private final GosuPlugin gosuPlugin = new GosuPlugin()

    @Test
    void appliesTheJavaPluginToTheProject() {
        gosuPlugin.apply(project)
        assertTrue(project.getPlugins().hasPlugin(JavaPlugin))
    }

    @Test
    void addsGosuConventionToEachSourceSetAndAppliesMappings() {
        gosuPlugin.apply(project)

        def sourceSet = project.sourceSets.main
        assertThat(sourceSet.gosu.displayName, equalTo("main Gosu source"))
        assertThat(sourceSet.gosu.srcDirs, equalTo(toLinkedSet(project.file("src/main/gosu"))))

        sourceSet = project.sourceSets.test
        assertThat(sourceSet.gosu.displayName, equalTo("test Gosu source"))
        assertThat(sourceSet.gosu.srcDirs, equalTo(toLinkedSet(project.file("src/test/gosu"))))
    }

    @Test void addsCompileTaskForEachSourceSet() {
        gosuPlugin.apply(project)

        def task = project.tasks['compileGosu']
        assertThat(task, instanceOf(GosuCompile.class))
        assertThat(task.description, equalTo('Compiles the main Gosu source.'))
        assertThat(task.classpath, equalTo(project.sourceSets.main.compileClasspath))
        assertThat(task.source as List, equalTo(project.sourceSets.main.gosu  as List))
        assertThat(task, dependsOn(JavaPlugin.COMPILE_JAVA_TASK_NAME))

        task = project.tasks['compileTestGosu']
        assertThat(task, instanceOf(GosuCompile.class))
        assertThat(task.description, equalTo('Compiles the test Gosu source.'))
        assertThat(task.classpath, equalTo(project.sourceSets.test.compileClasspath))
        assertThat(task.source as List, equalTo(project.sourceSets.test.gosu as List))
        assertThat(task, dependsOn(JavaPlugin.COMPILE_TEST_JAVA_TASK_NAME, JavaPlugin.CLASSES_TASK_NAME))
    }

    @Test
    public void dependenciesOfJavaPluginTasksIncludeGosuCompileTasks() {
        gosuPlugin.apply(project)

        def task = project.tasks[JavaPlugin.CLASSES_TASK_NAME]
        assertThat(task, dependsOn(hasItem('compileGosu')))

        task = project.tasks[JavaPlugin.TEST_CLASSES_TASK_NAME]
        assertThat(task, dependsOn(hasItem('compileTestGosu')))
    }

    @Test
    void addsGosuDocTasksToTheProject() {
        gosuPlugin.apply(project)

        def task = project.tasks[GosuPlugin.GOSU_DOC_TASK_NAME]
        assertThat(task, instanceOf(GosuDoc.class))
        assertThat(task, dependsOn(JavaPlugin.CLASSES_TASK_NAME))
        assertThat(task.destinationDir, equalTo(project.file("$project.docsDir/gosudoc")))
        assertThat(task.source as List, equalTo(project.sourceSets.main.gosu as List))
        assertThat(task.classpath, FileCollectionMatchers.sameCollection(project.files(project.sourceSets.main.output, project.sourceSets.main.compileClasspath)))
        assertThat(task.title, equalTo(project.extensions.getByType(ReportingExtension).apiDocTitle))
    }

    @Test
    @Ignore
    void configuresGosuDocTasksDefinedByTheBuildScript() {
        gosuPlugin.apply(project)

        def task = project.task('otherGosudoc', type: GosuDoc.class)
        assertThat(task, instanceOf(GosuDoc.class))
        assertThat(task, dependsOn(JavaPlugin.CLASSES_TASK_NAME)) //TODO:KM why is this expected behavior?
        assertThat(task.classpath, FileCollectionMatchers.sameCollection(project.files(project.sourceSets.main.output, project.sourceSets.main.compileClasspath)))
    }
}
