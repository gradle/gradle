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

import org.gradle.api.internal.tasks.JvmConstants
import org.gradle.api.reporting.ReportingExtension
import org.gradle.api.tasks.scala.ScalaDoc
import org.gradle.test.fixtures.AbstractProjectBuilderSpec

import static org.gradle.api.file.FileCollectionMatchers.sameCollection
import static org.gradle.api.tasks.TaskDependencyMatchers.dependsOn
import static org.hamcrest.MatcherAssert.assertThat

class Scala3PluginTest extends AbstractProjectBuilderSpec {

    def 'adds Scaladoc test to project for Scala 3'() {
        when:
        project.pluginManager.apply(ScalaPlugin)

        temporaryFolder.createFile('libs/scala3-library_3-3.0.1.jar)')
        project.dependencies.add('implementation', project.files('libs/scala3-library_3-3.0.1.jar)'))

        then:
        def task = project.tasks[ScalaPlugin.SCALA_DOC_TASK_NAME]
        task instanceof ScalaDoc
        task dependsOn(JvmConstants.CLASSES_TASK_NAME, JvmConstants.COMPILE_JAVA_TASK_NAME, 'compileScala')
        task.destinationDir.asFile.get() == project.file("$project.docsDir/scaladoc")
        // This assertion is a little tricky, because `task.source` is an empty list since we didn't compile these files, so we check here if [] == []
        task.source as List  == project.sourceSets.main.output.findAll { it.name.endsWith(".tasty") } as List // We take output of main (with tasty files)
        assertThat(task.classpath, sameCollection(project.layout.files(project.sourceSets.main.output, project.sourceSets.main.compileClasspath)))
        task.title.get() == project.extensions.getByType(ReportingExtension).apiDocTitle
    }
}
