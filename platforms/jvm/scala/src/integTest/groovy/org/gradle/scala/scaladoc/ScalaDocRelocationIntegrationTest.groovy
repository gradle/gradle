/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.scala.scaladoc

import org.gradle.api.plugins.scala.ScalaPlugin
import org.gradle.integtests.fixtures.AbstractTaskRelocationIntegrationTest
import org.gradle.scala.ScalaCompilationFixture

import java.nio.file.Files

class ScalaDocRelocationIntegrationTest extends AbstractTaskRelocationIntegrationTest {

    private classes = new ScalaCompilationFixture(testDirectory)

    @Override
    protected String getTaskName() {
        return ":${ScalaPlugin.SCALA_DOC_TASK_NAME}"
    }

    @Override
    protected void setupProjectInOriginalLocation() {
        classes.scalaVersion = '2.12.18'
        executer.beforeExecute {
            // Scaladoc leaks file handles if this is not activated: https://github.com/scala/scala/pull/5592
            // This requires Scala 2.12.2
            args '-Dscala.classpath.closeZip=true'
        }
        classes.baseline()
        buildScript(classes.buildScript())
    }

    @Override
    protected void moveFilesAround() {
        Files.move(file("src/main/scala").toPath(), file("src/main/new-scala").toPath())
        classes.sourceDir = 'src/main/new-scala'
        buildScript(classes.buildScript())
        // Move scala library dependency around on disk
        executer.requireOwnGradleUserHomeDir()
    }

    @Override
    protected extractResults() {
        return classes.basicClassSource.javadocLocation
    }
}
