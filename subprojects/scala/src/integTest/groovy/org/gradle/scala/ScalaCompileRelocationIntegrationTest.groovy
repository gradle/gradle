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

package org.gradle.scala

import org.gradle.integtests.fixtures.AbstractTaskRelocationIntegrationTest
import spock.lang.Ignore

import static org.gradle.util.JarUtils.jarWithContents

@Ignore("This test does not work yet")
class ScalaCompileRelocationIntegrationTest extends AbstractTaskRelocationIntegrationTest {

    @Override
    protected String getTaskName() {
        return ":compile"
    }

    @Override
    protected void setupProjectInOriginalLocation() {
        file("libs").createDir()
        file("libs/lib1.jar") << jarWithContents("data.txt": "data1")
        file("libs/lib2.jar") << jarWithContents("data.txt": "data2")
        file("src/main/scala/sub-dir").createDir()
        file("src/main/scala/Foo.java") << "class Foo {}"

        buildFile << buildFileWithClasspath("libs")
    }

    private static String buildFileWithClasspath(String classpath) {
        """
            task compile(type: ScalaCompile) {
                sourceCompatibility = JavaVersion.current()
                targetCompatibility = JavaVersion.current()
                destinationDir = file("build/classes")
                source "src/main/scala"
                classpath = files('$classpath')
                scalaClasspath = files()
                zincClasspath = files()
            }
        """
    }

    @Override
    protected void moveFilesAround() {
        file("src/main/scala/Foo.java").moveToDirectory(file("src/main/scala/sub-dir"))
        file("libs").renameTo(file("lobs"))
        buildFile.text = buildFileWithClasspath("lobs")
    }

    @Override
    protected extractResults() {
        return file("build/classes/Foo.class").bytes
    }
}
