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
package org.gradle.api.plugins.scala

import org.gradle.api.Project
import org.gradle.api.file.FileCollectionMatchers
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.reporting.ReportingExtension
import org.gradle.api.tasks.scala.ScalaDoc
import org.gradle.test.fixtures.file.LeaksFileHandles
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.TestUtil
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import spock.lang.Issue

import static org.gradle.api.tasks.TaskDependencyMatchers.dependsOn
import static org.hamcrest.CoreMatchers.equalTo
import static org.hamcrest.CoreMatchers.instanceOf
import static org.hamcrest.MatcherAssert.assertThat

@Ignore
@Issue("https://github.com/gradle/gradle-private/issues/3440")
class ScalaPlugin3Test {
    @Rule
    public TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider(getClass())

    private final Project project = TestUtil.create(temporaryFolder).rootProject()

    private final ScalaPlugin scalaPlugin = new ScalaPlugin()

    @LeaksFileHandles
    @Test
    void addsScalaDoc3TasksToTheProject() {
        scalaPlugin.apply(project)
        temporaryFolder.createFile('libs/scala3-library_3-3.0.1.jar)')
        project.dependencies.add('implementation', project.files('libs/scala3-library_3-3.0.1.jar)'))

        def task = project.tasks[ScalaPlugin.SCALA_DOC_TASK_NAME]
        assertThat(task, instanceOf(ScalaDoc.class))
        assertThat(task, dependsOn(JavaPlugin.CLASSES_TASK_NAME))
        assertThat(task.destinationDir, equalTo(project.file("$project.docsDir/scaladoc")))
        // This assertion is a little tricky, because `task.source` is an empty list since we didn't compile these files, so we check here if [] == []
        assertThat(task.source as List, equalTo(project.sourceSets.main.output.findAll { it.name.endsWith(".tasty") } as List)) // We take output of main (with tasty files)
        assertThat(task.classpath, FileCollectionMatchers.sameCollection(project.layout.files(project.sourceSets.main.output, project.sourceSets.main.compileClasspath)))
        assertThat(task.title, equalTo(project.extensions.getByType(ReportingExtension).apiDocTitle))
    }
}
