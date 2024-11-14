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

import org.gradle.testdistribution.LocalOnly
import org.gradle.initialization.StartParameterBuildOptions
import org.gradle.integtests.fixtures.daemon.DaemonFixture
import org.gradle.integtests.fixtures.daemon.DaemonLogsAnalyzer
import org.gradle.test.fixtures.ConcurrentTestUtil

@LocalOnly
class LoggingFileSystemWatchingIntegrationTest extends AbstractFileSystemWatchingIntegrationTest {

    def setup() {
        executer.requireDaemon()
    }

    def "verbose VFS logging can be enabled"() {
        buildFile << """
            apply plugin: "java"
        """
        when:
        withWatchFs().run("assemble", "-D${StartParameterBuildOptions.VfsVerboseLoggingOption.GRADLE_PROPERTY}=true")
        then:
        !(result.output =~ /Received \d+ file system events since last build while watching \d+ locations/)
        !(result.output =~ /Virtual file system retained information about \d+ files, \d+ directories and \d+ missing files since last build/)
        result.output =~ /VFS> Statistics since last build:/
        result.output =~ /VFS> > Stat: Executed stat\(\) x 0. getUnixMode\(\) x 0/
        result.output =~ /VFS> > FileHasher: Hashed 0 files \(0 bytes\)/
        result.output =~ /VFS> > DirectorySnapshotter: Snapshot 0 directory hierarchies \(visited 0 directories, 0 files and 0 failed files\)/
        result.output =~ /Received \d+ file system events during the current build while watching \d+ locations/
        result.output =~ /Virtual file system retains information about \d+ files, \d+ directories and \d+ missing files until next build/
        result.output =~ /VFS> Statistics during current build:/
        result.output =~ /VFS> > Stat: Executed stat\(\) x .*. getUnixMode\(\) x .*/
        result.output =~ /VFS> > FileHasher: Hashed .* files \(.* bytes\)/
        result.output =~ /VFS> > DirectorySnapshotter: Snapshot .* directory hierarchies \(visited .* directories, .* files and .* failed files\)/

        when:
        withWatchFs().run("assemble", "-D${StartParameterBuildOptions.VfsVerboseLoggingOption.GRADLE_PROPERTY}=true")
        then:
        result.output =~ /VFS> Statistics since last build:/
        result.output =~ /VFS> Statistics during current build:/
        result.output =~ /Received \d+ file system events since last build while watching \d+ locations/
        result.output =~ /Virtual file system retained information about \d+ files, \d+ directories and \d+ missing files since last build/
        result.output =~ /Received \d+ file system events during the current build while watching \d+ locations/
        result.output =~ /Virtual file system retains information about \d+ files, \d+ directories and \d+ missing files until next build/

        when:
        withWatchFs().run("assemble", "-D${StartParameterBuildOptions.VfsVerboseLoggingOption.GRADLE_PROPERTY}=false")
        then:
        !(result.output =~ /VFS> Statistics since last build:/)
        !(result.output =~ /VFS> Statistics during current build:/)
        !(result.output =~ /Received \d+ file system events since last build while watching \d+ locations/)
        !(result.output =~ /Virtual file system retained information about \d+ files, \d+ directories and \d+ missing files since last build/)
        !(result.output =~ /Received \d+ file system events during the current build while watching \d+ locations/)
        !(result.output =~ /Virtual file system retains information about \d+ files, \d+ directories and \d+ missing files until next build/)
    }

    def "logs handled events between builds only if something changed in the VFS"() {
        buildFile << """
            plugins {
                id('java')
            }
        """
        def sourceFile = file("src/main/java/MyClass.java")
        sourceFile.text = "public class MyClass { }"

        when:
        // We do a warmup run so even on Windows, the VFS really contains all the files we expect it to contain.
        withWatchFs().run("assemble")
        waitForChangesToBePickedUp()
        def vfsLogs = enableVerboseVfsLogs()
        withWatchFs().run("assemble")
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
        // On Windows, it may take some extra time until the event occurs
        ConcurrentTestUtil.poll {
            // The first modification removes the VFS entry, so the second modification doesn't have an effect on the VFS.
            assert daemon.log.count("Handling VFS change MODIFIED ${sourceFile.absolutePath}") == 1
        }
    }

    private DaemonFixture getDaemon() {
        new DaemonLogsAnalyzer(executer.daemonBaseDir).daemon
    }
}
