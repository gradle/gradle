/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.api.tasks.testing

import org.gradle.api.InvalidUserDataException
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.test.fixtures.AbstractProjectBuilderSpec

class TestTest extends AbstractProjectBuilderSpec {

    def 'javaLauncher is annotated with @Nested and @Optional'() {
        given:
        def launcherMethod = Test.class.getMethod('getJavaLauncher', [] as Class[])

        expect:
        launcherMethod.isAnnotationPresent(Nested)
        launcherMethod.isAnnotationPresent(Optional)
    }

    def 'fails if custom executable does not exist'() {
        def testTask = project.tasks.create("test", Test)
        def invalidJava = "invalidjava"

        when:
        testTask.executable = invalidJava
        testTask.javaVersion

        then:
        def e = thrown(InvalidUserDataException)
        e.message.contains("The configured executable does not exist")
        e.message.contains(invalidJava)
    }

    def "fails if custom executable is a directory"() {
        def testTask = project.tasks.create("test", Test)
        def executableDir = temporaryFolder.createDir("javac")

        when:
        testTask.executable = executableDir.absolutePath
        testTask.javaVersion

        then:
        def e = thrown(InvalidUserDataException)
        e.message.contains("The configured executable is a directory")
        e.message.contains(executableDir.name)
    }

    def "fails if custom executable is not from a valid JVM"() {
        def testTask = project.tasks.create("test", Test)
        def invalidJavac = temporaryFolder.createFile("invalidJavac")

        when:
        testTask.executable = invalidJavac.absolutePath
        testTask.javaVersion

        then:
        def e = thrown(InvalidUserDataException)
        e.message.contains("Specific installation toolchain")
        e.message.contains(invalidJavac.parentFile.parentFile.absolutePath)
    }
}
