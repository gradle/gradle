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

package org.gradle.buildinit.plugins

import org.gradle.buildinit.plugins.fixtures.ScriptDslFixture
import org.gradle.buildinit.plugins.internal.modifiers.BuildInitDsl
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.test.fixtures.file.TestFile

class AbstractInitIntegrationSpec extends AbstractIntegrationSpec {
    final def targetDir = testDirectory.createDir("some-thing")

    def setup() {
        executer.withRepositoryMirrors()
        executer.beforeExecute {
            executer.inDirectory(targetDir)
            executer.ignoreMissingSettingsFile()
        }
    }

    void assertTestPassed(String className, String name) {
        def result = new DefaultTestExecutionResult(targetDir)
        result.assertTestClassesExecuted(className)
        result.testClass(className).assertTestPassed(name)
    }

    void assertFunctionalTestPassed(String className, String name) {
        def result = new DefaultTestExecutionResult(targetDir, 'build', '', '', 'functionalTest')
        result.assertTestClassesExecuted(className)
        result.testClass(className).assertTestPassed(name)
    }

    protected void commonFilesGenerated(BuildInitDsl scriptDsl) {
        dslFixtureFor(scriptDsl).assertGradleFilesGenerated()
        targetDir.file(".gitignore").assertIsFile()
        targetDir.file(".gitattributes").assertIsFile()
    }
    protected void commonJvmFilesGenerated(BuildInitDsl scriptDsl) {
        commonFilesGenerated(scriptDsl)
        targetDir.file("src/main/resources").assertIsDir()
        targetDir.file("src/test/resources").assertIsDir()
    }

    protected ScriptDslFixture dslFixtureFor(BuildInitDsl dsl) {
        ScriptDslFixture.of(dsl, targetDir)
    }

    protected TestFile pom() {
        targetDir.file("pom.xml") << """
      <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
        <modelVersion>4.0.0</modelVersion>
        <groupId>util</groupId>
        <artifactId>util</artifactId>
        <version>2.5</version>
        <packaging>jar</packaging>
      </project>"""
    }
}
