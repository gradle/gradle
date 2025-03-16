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
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.junit.Rule

@LocalOnly
class ChangesDuringTheBuildFileSystemWatchingIntegrationTest extends AbstractFileSystemWatchingIntegrationTest {
    @Rule
    BlockingHttpServer server = new BlockingHttpServer()

    VerboseVfsLogAccessor vfsLogs

    def setup() {
        executer.requireDaemon()
        executer.beforeExecute {
            vfsLogs = enableVerboseVfsLogs()
        }
        server.start()
        settingsFile  << """
            import org.gradle.internal.file.FileType
            import org.gradle.internal.snapshot.*
            import org.gradle.internal.vfs.*
            import org.gradle.api.flow.*
            import org.gradle.api.services.*

            class VfsPlugin implements Plugin<Settings> {
                final FlowScope flowScope
                final FlowProviders flowProviders

                @Inject
                VfsPlugin(FlowScope flowScope, FlowProviders flowProviders) {
                    this.flowScope = flowScope
                    this.flowProviders = flowProviders
                }

                @Override
                public void apply(Settings project) {
                    def projectDir = project.rootDir
                    def vfsService = project.gradle.services.get(VirtualFileSystem)
                    def projectDirAsResultOfBuild = flowProviders.buildWorkResult.map { projectDir }
                    flowScope.always(
                        VfsAction.class,
                        spec -> {
                            spec.getParameters().getRootDir().set(projectDirAsResultOfBuild)
                            spec.getParameters().getVfsService().set(vfsService)
                        }
                    );
                }
            }

            class VfsAction implements FlowAction<VfsAction.Parameters> {
                interface Parameters extends FlowParameters {
                    @Input
                    Property<VirtualFileSystem> getVfsService();

                    @Input
                    Property<File> getRootDir();
                }

                @Override
                public void execute(Parameters parameters) {
                    def projectRoot = parameters.rootDir.get().absolutePath
                    def vfs = parameters.vfsService.get()
                    int filesInVfs = 0
                    vfs.root.rootSnapshots().forEach { snapshot ->
                        snapshot.accept(new FileSystemSnapshotHierarchyVisitor() {
                            @Override
                            SnapshotVisitResult visitEntry(FileSystemLocationSnapshot fileSnapshot) {
                                if (fileSnapshot.type == FileType.RegularFile && fileSnapshot.absolutePath.startsWith(projectRoot)) {
                                    println("Found file in VFS: \$fileSnapshot.absolutePath")
                                    filesInVfs++
                                }
                                return SnapshotVisitResult.CONTINUE
                            }
                        })
                    }
                    println("Project files in VFS: \$filesInVfs")
                }
            }

            apply type: VfsPlugin
        """
        buildFile << """
            task waitForUserChanges {
                doLast {
                    ${server.callFromBuild("userInput")}
                }
            }
        """
    }

    def "detects input file change just before the task is executed"() {
        def inputFile = file("input.txt")
        buildFile << """
            def inputFile = file("input.txt")
            def outputFile = file("build/output.txt")

            task consumer {
                inputs.file(inputFile)
                outputs.file(outputFile)
                doLast {
                    outputFile.text = inputFile.text
                }
                dependsOn(waitForUserChanges)
            }
        """

        when:
        runWithFileSystemWatchingAndMakeChangesWhen("consumer", "userInput") {
            inputFile.text = "initial"
            waitForChangesToBePickedUp()
        }
        then:
        executedAndNotSkipped(":consumer")
        // TODO: sometimes, the changes from the same build are picked up
        projectFilesInVfs >= 1

        when:
        runWithFileSystemWatchingAndMakeChangesWhen("consumer", "userInput") {
            inputFile.text = "changed"
            waitForChangesToBePickedUp()
        }
        then:
        executedAndNotSkipped(":consumer")
        vfsLogs.receivedFileSystemEventsInCurrentBuild >= 1
        assertExpectedProjectFilesInVfs(2)
    }

    def "detects input file change after the task has been executed"() {
        def inputFile = file("input.txt")
        def outputFile = file("build/output.txt")
        buildFile << """
            def inputFile = file("input.txt")
            def outputFile = file("build/output.txt")

            task consumer {
                inputs.file(inputFile)
                outputs.file(outputFile)
                doLast {
                    // Make sure the creation event for the build directory arrived.
                    // See https://github.com/gradle/gradle-private/issues/3537.
                    Thread.sleep(40)
                    outputFile.text = inputFile.text
                }
            }

            waitForUserChanges.dependsOn(consumer)
        """

        when:
        inputFile.text = "initial"
        runWithFileSystemWatchingAndMakeChangesWhen("waitForUserChanges", "userInput") {
            inputFile.text = "changed"
            waitForChangesToBePickedUp()
        }
        then:
        executedAndNotSkipped(":consumer")
        outputFile.text == "initial"
        assertExpectedProjectFilesInVfs(1)

        when:
        runWithFileSystemWatchingAndMakeChangesWhen("waitForUserChanges", "userInput") {
            inputFile.text = "changedAgain"
            waitForChangesToBePickedUp()
        }
        then:
        executedAndNotSkipped(":consumer")
        outputFile.text == "changed"
        vfsLogs.receivedFileSystemEventsInCurrentBuild >= 1
        assertExpectedProjectFilesInVfs(1)

        when:
        server.expect("userInput")
        withWatchFs().run("waitForUserChanges")

        then:
        executedAndNotSkipped(":consumer")
        outputFile.text == "changedAgain"
        assertExpectedProjectFilesInVfs(2)
    }

    private void assertExpectedProjectFilesInVfs(int expected) {
        if (GradleContextualExecuter.isConfigCache()) {
            // Cc watches also settings.gradle and build.gradle, so they are added to VFS.
            expected += 2
        }
        assert getProjectFilesInVfs() == expected
    }

    private void runWithFileSystemWatchingAndMakeChangesWhen(String task, String expectedCall, Closure action) {
        def handle = withWatchFs().executer.withTasks(task).start()
        def userInput = server.expectAndBlock(expectedCall)
        userInput.waitForAllPendingCalls()
        action()
        userInput.releaseAll()
        result = handle.waitForFinish()
    }

    int getProjectFilesInVfs() {
        def retainedInformation = result.getOutputLineThatContains("Project files in VFS: ")
        def numberMatcher = retainedInformation =~ /Project files in VFS: (\d+)/
        return numberMatcher[0][1] as int
    }
}
