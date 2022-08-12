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

import org.gradle.integtests.fixtures.ForkCapableRelocationIntegrationTest
import org.gradle.scala.ScalaCompilationFixture
import org.gradle.test.fixtures.file.TestFile

class ScalaCompileRelocationIntegrationTest extends ForkCapableRelocationIntegrationTest {

    @Override
    protected String getTaskName() {
        return ":compileScala"
    }

    @Override
    String getDaemonConfiguration() {
        // Scala compiler always runs in a daemon
        return ""
    }

    @Override
    String getForkOptionsObject() {
        return "compileScala.scalaCompileOptions.forkOptions"
    }

    @Override
    protected void setupProjectIn(TestFile projectDir) {
        def classes = new ScalaCompilationFixture(projectDir)
        classes.baseline()
        projectDir.file("build.gradle") << classes.buildScript()
    }

    @Override
    protected void prepareForRelocation(TestFile projectDir) {
        // Move Zinc and Scala library dependencies around on disk
        executer.requireOwnGradleUserHomeDir()
    }

    @Override
    protected extractResultsFrom(TestFile projectDir) {
        def classes = new ScalaCompilationFixture(projectDir)
        return classes.classDependingOnBasicClassSource.compiledClass.bytes
    }
}
