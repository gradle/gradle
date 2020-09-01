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

import org.gradle.integtests.fixtures.AbstractContinuousIntegrationTest
import org.gradle.integtests.fixtures.FileSystemWatchingFixture
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.internal.service.scopes.VirtualFileSystemServices

import static org.junit.Assume.assumeFalse

class ContinuousBuildFileWatchingIntegrationTest extends AbstractContinuousIntegrationTest implements FileSystemWatchingFixture {

    def setup() {
        assumeFalse("No shared state without a daemon", GradleContextualExecuter.noDaemon)
        assumeFalse("The test manually enables file system watching", GradleContextualExecuter.watchFs)

        executer.requireIsolatedDaemons()
        executer.beforeExecute {
            withWatchFs()
            withVerboseVfsLog()
            // Do not drop the VFS in the first build, since there is only one continuous build invocation.
            withArgument("-D${VirtualFileSystemServices.VFS_DROP_PROPERTY}=false")
        }
    }

    def "file system watching picks up changes causing a continuous build to rebuild"() {
        def numberOfFilesInVfs = 4 // source file, class file, JAR manifest, JAR file

        when:
        def sourceFile = file("src/main/java/Thing.java")
        buildFile << """
            plugins {
                id('java')
            }
        """
        sourceFile << "class Thing {}"

        then:
        succeeds("build")
        executedAndNotSkipped ":compileJava", ":build"
        vfsLogs.getRetainedFilesInCurrentBuild() >= numberOfFilesInVfs

        when:
        sourceFile.text = "class Thing { public void doStuff() {} }"

        then:
        succeeds()
        vfsLogs.getReceivedFileSystemEventsSinceLastBuild() >= 1
        vfsLogs.getRetainedFilesSinceLastBuild() >= numberOfFilesInVfs - 1
        vfsLogs.getRetainedFilesInCurrentBuild() >= numberOfFilesInVfs
        executedAndNotSkipped(":compileJava")
        executed(":build")
    }
}
