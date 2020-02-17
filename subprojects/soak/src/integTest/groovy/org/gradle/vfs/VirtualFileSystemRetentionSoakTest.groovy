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

package org.gradle.vfs

import org.gradle.integtests.fixtures.daemon.DaemonIntegrationSpec
import org.gradle.internal.service.scopes.VirtualFileSystemServices
import org.gradle.soak.categories.SoakTest
import org.gradle.test.fixtures.file.TestFile
import org.junit.experimental.categories.Category

@Category(SoakTest)
class VirtualFileSystemRetentionSoakTest extends DaemonIntegrationSpec {

    private static final int NUMBER_OF_SUBPROJECTS = 50
    private static final int NUMBER_OF_SOURCES_PER_SUBPROJECT = 100
    private static final int TOTAL_NUMBER_OF_SOURCES = NUMBER_OF_SUBPROJECTS * NUMBER_OF_SOURCES_PER_SUBPROJECT
    private static final double LOST_EVENTS_RATIO = 0.75

    List<TestFile> sourceFiles

    def setup() {
        // Use 20 minutes idle timeout since the test may be running longer with an idle daemon
        executer.withDaemonIdleTimeoutSecs(1200)
        def subprojects = (1..NUMBER_OF_SUBPROJECTS).collect { "project$it" }
        def rootProject = multiProjectBuild("javaProject", subprojects) {
            buildFile << """
                allprojects {
                    apply plugin: 'java-library'
                }
            """
        }
        sourceFiles = subprojects.collectMany { projectDir ->
            (1..NUMBER_OF_SOURCES_PER_SUBPROJECT).collect {
                def sourceFile = rootProject.file("${projectDir}/src/main/java/my/domain/Dummy${it}.java")
                modifySourceFile(sourceFile, 0)
                return sourceFile
            }
        }

        executer.beforeExecute {
            withArgument("-D${VirtualFileSystemServices.VFS_RETENTION_ENABLED_PROPERTY}=true")
        }
    }

    def "file watching works with multiple builds on the same daemon"() {
        when:
        succeeds("assemble")
        def daemon = daemons.daemon
        then:
        daemon.assertIdle()

        expect:
        (1..100).each { iteration ->
            changeAllSourceFiles(iteration)
            waitForChangesToBePickedUp()
            succeeds("assemble")
            assert daemons.daemon.logFile == daemon.logFile
            daemon.assertIdle()
            assertWatchingSucceeded()
            assert receivedFileSystemEvents >= TOTAL_NUMBER_OF_SOURCES * LOST_EVENTS_RATIO
        }
    }

    def "file watching works with many changes between two builds"() {
        when:
        succeeds("assemble")
        def daemon = daemons.daemon
        then:
        daemon.assertIdle()

        when:
        (1..100).each { iteration ->
            changeAllSourceFiles(iteration)
            waitForChangesToBePickedUp()
        }
        then:
        succeeds("assemble")
        daemons.daemon.logFile == daemon.logFile
        daemon.assertIdle()
        assertWatchingSucceeded()
        receivedFileSystemEvents >= TOTAL_NUMBER_OF_SOURCES * 100 * LOST_EVENTS_RATIO
    }

    private static void waitForChangesToBePickedUp() {
        Thread.sleep(1000)
    }

    private void changeAllSourceFiles(int iteration) {
        sourceFiles.each { sourceFile ->
            modifySourceFile(sourceFile, iteration)
        }
    }

    private int getReceivedFileSystemEvents() {
        String eventsSinceLastBuild = result.getOutputLineThatContains("file system events since last build")
        def numberMatcher = eventsSinceLastBuild =~ /Received (\d+) file system events since last build/
        return numberMatcher[0][1] as int
    }

    private void assertWatchingSucceeded() {
        outputDoesNotContain("Couldn't create watch service")
        outputDoesNotContain("Couldn't fetch file changes, dropping VFS state")
        outputDoesNotContain("Dropped VFS state due to lost state")
    }

    private static void modifySourceFile(TestFile sourceFile, int numberOfMethods) {
        String className = sourceFile.name - ".java"
        sourceFile.text = """
                    package my.domain;

                    public class ${className} {
                        ${ (1..numberOfMethods).collect { "public void doNothing${it}() {}" }.join("\n")}
                    }
                """
    }
}
