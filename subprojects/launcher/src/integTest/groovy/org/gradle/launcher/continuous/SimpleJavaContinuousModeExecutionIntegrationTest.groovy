/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.launcher.continuous

import org.gradle.integtests.fixtures.jvm.TestJvmComponent
import org.gradle.language.fixtures.TestJavaComponent
import org.gradle.test.fixtures.file.TestFile
import spock.lang.Ignore

class SimpleJavaContinuousModeExecutionIntegrationTest extends AbstractContinuousModeExecutionIntegrationTest {
    TestJvmComponent app = new TestJavaComponent()
    TestFile sourceDir = file("src/main")

    def setup() {
        buildFile << """
    apply plugin: 'java'
"""
    }

    def "can build in continuous mode when no source dir present"() {
        when:
        assert !sourceDir.exists()
        then:
        succeeds("build")
        ":compileJava" in skippedTasks
        ":build" in executedTasks
    }

    def "can build in continuous mode when source dir is removed"() {
        when:
        app.writeSources(sourceDir)
        then:
        sourceDir.exists()
        succeeds("build")
        executedAndNotSkipped ":compileJava", ":build"
        when:
        sourceDir.deleteDir()
        assert !sourceDir.exists()
        then:
        succeeds()
        ":compileJava" in skippedTasks
        ":build" in executedTasks
    }

    def "build is not triggered when a new directory is created in the source inputs"() {
        when:
        app.writeSources(sourceDir)
        then:
        sourceDir.exists()
        succeeds("build")
        executedAndNotSkipped ":compileJava", ":build"
        when:
        sourceDir.file("newdirectory").createDir()
        then:
        noBuildTriggered()
    }

    def "after compilation failure, fixing file retriggers build"() {
        given:
        def sourceFiles = app.writeSources(sourceDir)
        def sourceFile = sourceFiles.get(0)
        when:
        sourceFile << "/* Broken compile"
        then:
        fails("build")
        when:
        sourceFile << "*/"
        then:
        succeeds()
        executedAndNotSkipped(":compileJava", ":build")
    }

    @Ignore("We skip execution of tasks with no sources")
    def "creation of initial source file triggers build"() {
        when:
        // NOTE: We do not watch directories that do not exist
        sourceDir.createDir()
        assert sourceDir.exists()
        then:
        succeeds("build")
        ":compileJava" in skippedTasks
        ":build" in executedTasks
        when:
        app.writeSources(sourceDir)
        then:
        succeeds()
        executedAndNotSkipped ":compileJava", ":jar", ":build"
    }
}
