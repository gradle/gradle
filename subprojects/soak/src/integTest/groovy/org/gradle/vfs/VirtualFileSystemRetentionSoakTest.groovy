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
import org.gradle.profiler.BuildContext
import org.gradle.profiler.Phase
import org.gradle.profiler.ScenarioContext
import org.gradle.profiler.mutations.ApplyAbiChangeToJavaSourceFileMutator
import org.gradle.soak.categories.SoakTest
import org.junit.experimental.categories.Category

@Category(SoakTest)
class VirtualFileSystemRetentionSoakTest extends DaemonIntegrationSpec {

    def "file watching works with multiple builds on the same daemon"() {
        def subprojects = (1..50).collect { "project$it" }
        def rootProject = multiProjectBuild("javaProject", subprojects) {
            buildFile << """
                allprojects {
                    apply plugin: 'java-library'
                }
            """
        }
        def sourceFiles = subprojects.collectMany { projectDir ->
            (1..100).collect {
                def sourceFile = rootProject.file("${projectDir}/src/main/java/my/domain/Dummy${it}.java")
                sourceFile << """
                    package my.domain;


                    public class Dummy${it} {
                        public void doNothing() {
                        }
                    }
                """
                return sourceFile
            }
        }

        def scenarioContext = new ScenarioContext(UUID.randomUUID(), "abiChange")

        executer.requireIsolatedDaemons()
        executer.beforeExecute {
            withArgument("-D${VirtualFileSystemServices.VFS_RETENTION_ENABLED_PROPERTY}=true")
        }

        when:
        succeeds("assemble")
        def daemon = daemons.daemon

        then:
        daemon.assertIdle()

        when:
        (1..100).each { iteration ->
            sourceFiles.each { sourceFile ->
                applyAbiChangeTo(
                    sourceFile,
                    scenarioContext.withBuild(Phase.MEASURE, iteration)
                )
            }
            succeeds("assemble")
            daemons.daemon.logFile == daemon.logFile
            daemon.assertIdle()
            assertWatchingSucceeded()
            assert receivedFileSystemEvents >= 5000
        }
        then:
        assertWatchingSucceeded()
        daemons.daemon.logFile == daemon.logFile
        daemon.assertIdle()
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

    private static void applyAbiChangeTo(File sourceFile, BuildContext context) {
        new ApplyAbiChangeToJavaSourceFileMutator(sourceFile).beforeBuild(context)
    }
}
