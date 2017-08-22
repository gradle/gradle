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

package org.gradle.scala.compile

import org.gradle.integtests.fixtures.AbstractTaskRelocationIntegrationTest
import org.gradle.scala.ScalaCompilationFixture

import java.nio.file.Files

class ScalaCompileRelocationIntegrationTest extends AbstractTaskRelocationIntegrationTest {

    private classes = new ScalaCompilationFixture(testDirectory)
    def differentSubdir = file("src/main/scala/other")

    @Override
    protected String getTaskName() {
        return ":compileScala"
    }

    @Override
    protected void setupProjectInOriginalLocation() {
        classes.baseline()
        differentSubdir.createDir()
        buildScript(classes.buildScript())
    }

    @Override
    protected void moveFilesAround() {
        classes.classDependingOnBasicClassSource.source.moveToDirectory(differentSubdir)
        Files.move(file("src/main/scala").toPath(), file("src/main/new-scala").toPath())
        classes.sourceDir = 'src/main/new-scala'
        buildScript(classes.buildScript())
        // Move zinc and scala library dependencies around on disk
        executer.requireOwnGradleUserHomeDir()
    }

    @Override
    protected extractResults() {
        return classes.classDependingOnBasicClassSource.compiledClass.bytes
    }
}
