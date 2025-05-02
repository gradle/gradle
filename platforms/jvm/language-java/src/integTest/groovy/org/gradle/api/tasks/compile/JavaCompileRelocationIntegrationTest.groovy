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

package org.gradle.api.tasks.compile

import org.gradle.integtests.fixtures.ForkCapableRelocationIntegrationTest
import org.gradle.test.fixtures.file.TestFile

import static org.gradle.util.JarUtils.jarWithContents

class JavaCompileRelocationIntegrationTest extends ForkCapableRelocationIntegrationTest {

    @Override
    protected String getTaskName() {
        return ":compile"
    }

    @Override
    String getDaemonConfiguration() {
        return "compile.options.fork = true"
    }

    @Override
    String getForkOptionsObject() {
        return "compile.options.forkOptions"
    }

    @Override
    protected void setupProjectIn(TestFile projectDir) {
        projectDir.file("libs").createDir()
        projectDir.file("libs/lib1.jar") << jarWithContents("data.txt": "data1")
        projectDir.file("libs/lib2.jar") << jarWithContents("data.txt": "data2")
        projectDir.file("src/main/java/sub-dir").createDir()
        projectDir.file("src/main/java/Foo.java") << "public class Foo {}"

        projectDir.file("build.gradle") << """
            task compile(type: JavaCompile) {
                sourceCompatibility = JavaVersion.current()
                targetCompatibility = JavaVersion.current()
                destinationDirectory = file("build/classes")
                source "src/main/java"
                classpath = files('libs')
            }
        """
    }

    @Override
    protected extractResultsFrom(TestFile projectDir) {
        return projectDir.file("build/classes/Foo.class").bytes
    }
}
