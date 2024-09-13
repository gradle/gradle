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

package org.gradle.api.tasks

import org.gradle.api.InvalidUserDataException
import org.gradle.api.internal.provider.AbstractProperty
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.test.fixtures.AbstractProjectBuilderSpec
import org.gradle.util.TestUtil

class JavaExecTest extends AbstractProjectBuilderSpec {

    def 'Jvm arguments are empty by default'() {
        when:
        def task = project.tasks.create("run", JavaExec)

        then:
        task.jvmArgs.get().isEmpty()
    }

    def 'fails if custom executable does not exist'() {
        def task = project.tasks.create("run", JavaExec)
        def invalidExecutable = temporaryFolder.file("invalid")

        when:
        task.executable = invalidExecutable
        execute(task)

        then:
        def e = thrown(TaskExecutionException)
        def cause = TestUtil.getRootCause(e) as InvalidUserDataException
        cause.message.contains("The configured executable does not exist")
        cause.message.contains(invalidExecutable.absolutePath)
    }

    def 'fails if custom executable is a directory'() {
        def task = project.tasks.create("run", JavaExec)
        def executableDir = temporaryFolder.createDir("javac")

        when:
        task.executable = executableDir.absolutePath
        execute(task)

        then:
        def e = thrown(TaskExecutionException)
        def cause = TestUtil.getRootCause(e) as InvalidUserDataException
        cause.message.contains("The configured executable is a directory")
        cause.message.contains(executableDir.name)
    }

    def 'fails if custom executable is not from a valid JVM'() {
        def task = project.tasks.create("run", JavaExec)
        def invalidJavac = temporaryFolder.createFile("invalidJavac")

        when:
        task.executable = invalidJavac.absolutePath
        execute(task)

        then:
        def e = thrown(TaskExecutionException)
        assertHasMatchingCause(e, m -> m.startsWith("Toolchain installation '${invalidJavac.parentFile.parentFile.absolutePath}' could not be probed:"))
        assertHasMatchingCause(e, m -> m ==~ /Cannot run program .*java.*/)
    }

    def "fails if custom Java home does not exist"() {
        def javaCompile = project.tasks.create("compileJava", JavaCompile)
        javaCompile.destinationDirectory.fileValue(temporaryFolder.createDir())
        def invalidJavaHome = "invalidJavaHome"

        when:
        javaCompile.options.fork = true
        javaCompile.options.forkOptions.javaHome = new File("invalidJavaHome")
        javaCompile.createSpec()

        then:
        def e = thrown(AbstractProperty.PropertyQueryException)
        def cause = TestUtil.getRootCause(e) as InvalidUserDataException
        cause.message.contains("The configured Java home does not exist")
        cause.message.contains(invalidJavaHome)
    }

    def "fails if custom Java home is not a directory"() {
        def javaCompile = project.tasks.create("compileJava", JavaCompile)
        javaCompile.destinationDirectory.fileValue(temporaryFolder.createDir())
        def javaHomeFile = temporaryFolder.createFile("javaHome")

        when:
        javaCompile.options.fork = true
        javaCompile.options.forkOptions.javaHome = javaHomeFile
        javaCompile.createSpec()

        then:
        def e = thrown(AbstractProperty.PropertyQueryException)
        def cause = TestUtil.getRootCause(e) as InvalidUserDataException
        cause.message.contains("The configured Java home is not a directory")
        cause.message.contains(javaHomeFile.absolutePath)
    }

    def "fails if custom Java home is not a valid JVM"() {
        def javaCompile = project.tasks.create("compileJava", JavaCompile)
        javaCompile.destinationDirectory.fileValue(temporaryFolder.createDir())
        def javaHomeDir = temporaryFolder.createDir("javaHome")

        when:
        javaCompile.options.fork = true
        javaCompile.options.forkOptions.javaHome = javaHomeDir
        javaCompile.createSpec()

        then:
        def e = thrown(AbstractProperty.PropertyQueryException)
        assertHasMatchingCause(e, m -> m.startsWith("Toolchain installation '${javaHomeDir.absolutePath}' could not be probed:"))
        assertHasMatchingCause(e, m -> m ==~ /Cannot run program .*java.*/)
    }
}
