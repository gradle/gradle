/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.process.internal

import org.apache.commons.io.FileUtils
import org.gradle.api.internal.file.TestFiles
import org.gradle.internal.classloader.ClasspathUtil
import org.gradle.process.ExecResult
import org.gradle.test.fixtures.concurrent.ConcurrentSpec
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import org.gradle.util.TestUtil
import org.junit.Rule

class DefaultExecActionFactoryTest extends ConcurrentSpec {
    @Rule
    public final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())
    def resolver = TestFiles.resolver(tmpDir.testDirectory)
    def fileCollectionFactory = TestFiles.fileCollectionFactory(tmpDir.testDirectory)
    def instantiator = TestUtil.instantiatorFactory()
    def factory =
        DefaultExecActionFactory
            .of(resolver, fileCollectionFactory, executorFactory, TestFiles.tmpDirTemporaryFileProvider(tmpDir.createDir("tmp")))
            .forContext()
            .withInstantiator(instantiator.decorateLenient())
            .withObjectFactory(TestUtil.objectFactory())
            .build()

    def javaexec() {
        File testFile = tmpDir.file("someFile")
        List files = ClasspathUtil.getClasspath(getClass().classLoader).asFiles

        when:
        ExecResult result = factory.javaexec { spec ->
            spec.classpath(files as Object[])
            spec.mainClass = SomeMain.name
            spec.args testFile.absolutePath
        }

        then:
        testFile.isFile()
        result.exitValue == 0
    }

    def javaexecWithNonZeroExitValueShouldThrowException() {
        when:
        factory.javaexec { spec ->
            spec.mainClass = 'org.gradle.UnknownMain'
        }

        then:
        thrown(ExecException)
    }

    def javaexecWithNonZeroExitValueAndIgnoreExitValueShouldNotThrowException() {
        when:
        ExecResult result = factory.javaexec { spec ->
            spec.mainClass = 'org.gradle.UnknownMain'
            spec.ignoreExitValue = true
        }

        then:
        result.exitValue != 0
    }

    @Requires(UnitTestPreconditions.NotWindows)
    def exec() {
        File testFile = tmpDir.file("someFile")

        when:
        ExecResult result = factory.exec { spec ->
            spec.executable = "touch"
            spec.workingDir = tmpDir.getTestDirectory()
            spec.args testFile.name
        }

        then:
        testFile.isFile()
        result.exitValue == 0
    }

    @Requires(UnitTestPreconditions.NotWindows)
    def execWithNonZeroExitValueShouldThrowException() {
        when:
        factory.exec { spec ->
            spec.executable = "touch"
            spec.workingDir = tmpDir.getTestDirectory()
            spec.args tmpDir.testDirectory.name + "/nonexistentDir/someFile"
        }

        then:
        thrown(ExecException)
    }

    @Requires(UnitTestPreconditions.NotWindows)
    def execWithNonZeroExitValueAndIgnoreExitValueShouldNotThrowException() {
        when:
        ExecResult result = factory.exec { spec ->
            spec.ignoreExitValue = true
            spec.executable = "touch"
            spec.workingDir = tmpDir.getTestDirectory()
            spec.args tmpDir.testDirectory.name + "/nonexistentDir/someFile"
        }

        then:
        result.exitValue != 0
    }

    class SomeMain {
        static void main(String[] args) {
            FileUtils.touch(new File(args[0]))
        }
    }
}
