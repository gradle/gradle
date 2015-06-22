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
package org.gradle.api.plugins.mirah

import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.mirah.MirahCompile
import org.gradle.util.TestUtil
import org.junit.Test

import static org.gradle.api.tasks.TaskDependencyMatchers.dependsOn
import static org.gradle.util.WrapUtil.toLinkedSet
import static org.hamcrest.Matchers.*
import static org.junit.Assert.assertThat
import static org.junit.Assert.assertTrue

class MirahPluginTest {
    private final Project project = TestUtil.createRootProject()
    private final MirahPlugin mirahPlugin = new MirahPlugin()

    @Test void appliesTheJavaPluginToTheProject() {
        mirahPlugin.apply(project)
        assertTrue(project.getPlugins().hasPlugin(JavaPlugin))
    }

    @Test void addsMirahConventionToEachSourceSetAndAppliesMappings() {
        mirahPlugin.apply(project)

        def sourceSet = project.sourceSets.main
        assertThat(sourceSet.mirah.displayName, equalTo("main Mirah source"))
        assertThat(sourceSet.mirah.srcDirs, equalTo(toLinkedSet(project.file("src/main/mirah"))))

        sourceSet = project.sourceSets.test
        assertThat(sourceSet.mirah.displayName, equalTo("test Mirah source"))
        assertThat(sourceSet.mirah.srcDirs, equalTo(toLinkedSet(project.file("src/test/mirah"))))
    }

    @Test void addsCompileTaskForEachSourceSet() {
        mirahPlugin.apply(project)

        def task = project.tasks['compileMirah']
        assertThat(task, instanceOf(MirahCompile.class))
        assertThat(task.description, equalTo('Compiles the main Mirah source.'))
        assertThat(task.classpath, equalTo(project.sourceSets.main.compileClasspath))
        assertThat(task.source as List, equalTo(project.sourceSets.main.mirah  as List))
        assertThat(task, dependsOn(JavaPlugin.COMPILE_JAVA_TASK_NAME))

        task = project.tasks['compileTestMirah']
        assertThat(task, instanceOf(MirahCompile.class))
        assertThat(task.description, equalTo('Compiles the test Mirah source.'))
        assertThat(task.classpath, equalTo(project.sourceSets.test.compileClasspath))
        assertThat(task.source as List, equalTo(project.sourceSets.test.mirah as List))
        assertThat(task, dependsOn(JavaPlugin.COMPILE_TEST_JAVA_TASK_NAME, JavaPlugin.CLASSES_TASK_NAME))
    }

    @Test public void dependenciesOfJavaPluginTasksIncludeMirahCompileTasks() {
        mirahPlugin.apply(project)

        def task = project.tasks[JavaPlugin.CLASSES_TASK_NAME]
        assertThat(task, dependsOn(hasItem('compileMirah')))

        task = project.tasks[JavaPlugin.TEST_CLASSES_TASK_NAME]
        assertThat(task, dependsOn(hasItem('compileTestMirah')))
    }
}
