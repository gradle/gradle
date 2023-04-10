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

//file:noinspection ConfigurationAvoidance

package org.gradle.api.tasks.testing

import org.gradle.api.InvalidUserDataException
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.provider.AbstractProperty
import org.gradle.test.fixtures.AbstractProjectBuilderSpec
import org.gradle.util.TestUtil

class TestTest extends AbstractProjectBuilderSpec {

    def 'test default values'() {
        def task = project.tasks.create("test", Test)
        expect:
        task.allowTestClassStealing.get() == Boolean.TRUE
    }

    def 'fails if custom executable does not exist'() {
        def task = project.tasks.create("test", Test)
        task.testClassesDirs = TestFiles.fixed(new File("tmp"))
        task.binaryResultsDirectory.fileValue(new File("out"))
        def invalidExecutable = temporaryFolder.file("invalidExecutable")

        when:
        task.executable = invalidExecutable
        task.javaLauncher.get()

        then:
        def e = thrown(AbstractProperty.PropertyQueryException)
        def cause = TestUtil.getRootCause(e) as InvalidUserDataException
        cause.message.contains("The configured executable does not exist")
        cause.message.contains(invalidExecutable.absolutePath)
    }

    def "fails if custom executable is a directory"() {
        def testTask = project.tasks.create("test", Test)
        def executableDir = temporaryFolder.createDir("javac")

        when:
        testTask.executable = executableDir.absolutePath
        testTask.javaVersion

        then:
        def e = thrown(AbstractProperty.PropertyQueryException)
        def cause = TestUtil.getRootCause(e) as InvalidUserDataException
        cause.message.contains("The configured executable is a directory")
        cause.message.contains(executableDir.name)
    }

    def "fails if custom executable is not from a valid JVM"() {
        def testTask = project.tasks.create("test", Test)
        def invalidJavac = temporaryFolder.createFile("invalidJavac")

        when:
        testTask.executable = invalidJavac.absolutePath
        testTask.javaVersion

        then:
        def e = thrown(AbstractProperty.PropertyQueryException)
        assertHasMatchingCause(e, m -> m.startsWith("Toolchain installation '${invalidJavac.parentFile.parentFile.absolutePath}' could not be probed:"))
        assertHasMatchingCause(e, m -> m ==~ /Cannot run program .*java.*/)
    }
}
