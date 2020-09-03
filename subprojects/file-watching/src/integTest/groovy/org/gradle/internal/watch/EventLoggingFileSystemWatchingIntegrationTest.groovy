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

import org.gradle.integtests.fixtures.daemon.DaemonLogsAnalyzer

class EventLoggingFileSystemWatchingIntegrationTest extends AbstractFileSystemWatchingIntegrationTest {

    def setup() {
        executer.requireDaemon()
    }

    def "logs handled events between builds only if something changed in the VFS"() {
        buildFile << """
            plugins {
                id('java')
            }
        """
        def sourceFile = file("src/main/java/MyClass.java")
        sourceFile.text = "public class MyClass { }"
        def vfsLogs = enableVerboseVfsLogs()

        when:
        withWatchFs().run("assemble")
        def daemon = new DaemonLogsAnalyzer(executer.daemonBaseDir).daemon
        then:
        vfsLogs.retainedFilesInCurrentBuild >= 4
        // Make sure the daemon is already idle before making the file changes
        daemon.becomesIdle()

        when:
        sourceFile.text = "public class MyClass { private void doStuff() {} }"
        waitForChangesToBePickedUp()
        sourceFile.text = "public class MyClass { private void doStuff1() {} }"
        waitForChangesToBePickedUp()
        then:
        // The first modification removes the VFS entry, so the second modification doesn't have an effect on the VFS.
        daemon.log.count("Handling VFS change MODIFIED ${file().absolutePath}") <= 1
    }
}
