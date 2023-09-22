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

package org.gradle.internal.watch

import com.gradle.enterprise.testing.annotations.LocalOnly
import org.gradle.integtests.fixtures.AbstractContinuousIntegrationTest
import org.gradle.integtests.fixtures.FileSystemWatchingFixture
import org.gradle.integtests.fixtures.FileSystemWatchingHelper
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions

@LocalOnly
@Requires(value = IntegTestPreconditions.NotNoDaemonExecutor, reason = "There is no shared state without a daemon")
class ContinuousBuildFileWatchingIntegrationTest extends AbstractContinuousIntegrationTest implements FileSystemWatchingFixture {

    def setup() {
        executer.requireIsolatedDaemons()
    }

    def "file system watching picks up changes causing a continuous build to rebuild"() {
        given:
        // Do not drop the VFS in the first build, since there is only one continuous build invocation.
        // FileSystemWatchingFixture automatically sets the argument for the first build.
        executer.withArgument(FileSystemWatchingHelper.getDropVfsArgument(false))
        executer.beforeExecute {
            withWatchFs()
        }

        def numberOfFilesInVfs = 4 // source file, class file, JAR manifest, JAR file
        def vfsLogs = enableVerboseVfsLogs()

        when:
        buildFile << """
            plugins {
                id('java')
            }
        """
        def sourceFile = file("src/main/java/Thing.java")
        sourceFile << "class Thing {}"

        then:
        succeeds("build")
        executedAndNotSkipped ":compileJava", ":build"
        vfsLogs.getRetainedFilesInCurrentBuild() >= numberOfFilesInVfs

        when:
        sourceFile.text = "class Thing { public void doStuff() {} }"

        then:
        buildTriggeredAndSucceeded()
        vfsLogs.getRetainedFilesSinceLastBuild() >= numberOfFilesInVfs - 1
        vfsLogs.getRetainedFilesInCurrentBuild() >= numberOfFilesInVfs
        executedAndNotSkipped(":compileJava")
        executed(":build")
    }
}
