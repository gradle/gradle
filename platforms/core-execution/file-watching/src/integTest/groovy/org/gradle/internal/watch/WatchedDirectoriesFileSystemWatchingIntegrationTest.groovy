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

import com.google.common.collect.ImmutableSet
import com.gradle.enterprise.testing.annotations.LocalOnly
import org.apache.commons.io.FileUtils
import org.gradle.cache.GlobalCacheLocations
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.internal.os.OperatingSystem
import org.gradle.internal.service.scopes.VirtualFileSystemServices
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.test.fixtures.server.http.MavenHttpRepository
import org.gradle.test.fixtures.server.http.RepositoryHttpServer
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import org.gradle.util.internal.TextUtil
import org.junit.Rule
import spock.lang.Issue

@LocalOnly
class WatchedDirectoriesFileSystemWatchingIntegrationTest extends AbstractFileSystemWatchingIntegrationTest {
    @Rule
    public final RepositoryHttpServer server = new RepositoryHttpServer(temporaryFolder)

    def setup() {
        executer.requireDaemon()
    }

    def "watches the project directory"() {
        buildFile << """
            apply plugin: "application"

            application.mainClass = "Main"
        """

        def mainSourceFileRelativePath = "src/main/java/Main.java"
        def mainSourceFile = file(mainSourceFileRelativePath)
        mainSourceFile.text = sourceFileWithGreeting("Hello World!")

        when:
        withWatchFs().run "run", "--info"
        then:
        assertWatchableHierarchies([ImmutableSet.of(testDirectory)])
    }

    def "watches the project directory when buildSrc is present"() {
        def taskSourceFile = file("buildSrc/src/main/java/PrinterTask.java")
        taskSourceFile.text = taskWithGreeting("Hello from original task!")

        buildFile << """
            task hello(type: PrinterTask)
        """

        when:
        withWatchFs().run "hello", "--info"
        then:
        outputContains "Hello from original task!"
        assertWatchableHierarchies([ImmutableSet.of(testDirectory), ImmutableSet.of(testDirectory, file("buildSrc"))])
    }

    def "works with composite build"() {
        buildTestFixture.withBuildInSubDir()
        def includedBuild = singleProjectBuild("includedBuild") {
            buildFile << """
                apply plugin: 'java'
            """
        }
        def consumer = singleProjectBuild("consumer") {
            buildFile << """
                apply plugin: 'java'

                dependencies {
                    implementation "org.test:includedBuild:1.0"
                }
            """
            settingsFile << """
                includeBuild("../includedBuild")
            """
        }
        executer.beforeExecute {
            inDirectory(consumer)
        }
        def expectedWatchableHierarchies = [
            ImmutableSet.of(consumer),
            ImmutableSet.of(consumer, includedBuild)
        ]

        when:
        withWatchFs().run "assemble", "--info"
        then:
        executedAndNotSkipped(":includedBuild:jar")
        assertWatchableHierarchies(expectedWatchableHierarchies)

        when:
        withWatchFs().run("assemble", "--info")
        then:
        skipped(":includedBuild:jar")
        // configuration cache registers all build directories at startup so the cache fingerprint can be checked
        def expectedWatchableCount = GradleContextualExecuter.isConfigCache() ? 4 : 2
        assertWatchableHierarchies([ImmutableSet.of(consumer, includedBuild)] * expectedWatchableCount)
        when:
        includedBuild.file("src/main/java/NewClass.java")  << "public class NewClass {}"
        withWatchFs().run("assemble")
        then:
        executedAndNotSkipped(":includedBuild:jar")
    }

    def "works with GradleBuild task"() {
        buildTestFixture.withBuildInSubDir()
        def buildInBuild = singleProjectBuild("buildInBuild") {
            buildFile << """
                apply plugin: 'java'
            """
        }
        def consumer = singleProjectBuild("consumer") {
            buildFile << """
                apply plugin: 'java'

                task buildInBuild(type: GradleBuild) {
                    startParameter.currentDir = file('../buildInBuild')
                }
            """
        }
        executer.beforeExecute {
            inDirectory(consumer)
        }
        def expectedWatchableHierarchies = [
            ImmutableSet.of(consumer),
            ImmutableSet.of(consumer, buildInBuild)
        ]

        when:
        withWatchFs().run "buildInBuild", "--info"
        then:
        assertWatchableHierarchies(expectedWatchableHierarchies)

        when:
        withWatchFs().run "buildInBuild", "--info"
        then:
        // configuration cache registers all build directories at startup so the cache fingerprint can be checked
        def expectedWatchableCount = GradleContextualExecuter.isConfigCache() ? 2 : 1
        assertWatchableHierarchies([ImmutableSet.of(consumer)] * expectedWatchableCount + [ImmutableSet.of(consumer, buildInBuild)])
    }

    def "gracefully handle the root project directory not being available"() {
        settingsFile << """
            throw new RuntimeException("Boom")
        """

        when:
        withWatchFs().fails("help")
        then:
        failureHasCause("Boom")
    }

    def "root project dir does not need to exist"() {
        def settingsDir = file("gradle")
        def settingsFile = settingsDir.file("settings.gradle")
        settingsFile << """
            rootProject.projectDir = new File(settingsDir, '../root')
            include 'sub'
            project(':sub').projectDir = new File(settingsDir, '../sub')
        """
        file("sub/build.gradle") << "task thing"

        when:
        inDirectory(settingsDir)
        withWatchFs().run("thing")
        then:
        executed ":sub:thing"

    }

    def "detects when a task removes the build directory #buildDir"() {
        buildFile << """
            apply plugin: 'base'

            project.buildDir = file("${buildDir}")

            task myClean(type: Delete) {
                delete buildDir
            }

            task producer {
                def outputFile = new File(buildDir, "some/file/in/buildDir/output.txt")
                outputs.file(outputFile)
                doLast {
                    outputFile.parentFile.mkdirs()
                    outputFile.text = "Output"
                }
            }
        """

        when:
        withWatchFs().run "producer"
        then:
        executedAndNotSkipped ":producer"

        when:
        withWatchFs().run "myClean"
        withWatchFs().run "producer"
        then:
        executedAndNotSkipped ":producer"

        where:
        buildDir << ["build", "build/myProject"]
    }

    @Issue("https://github.com/gradle/gradle/issues/12614")
    def "can remove watched directory after all files inside have been removed"() {
        // This test targets Windows, where watched directories can't be deleted.

        def projectDir = file("projectDir")
        projectDir.file("build.gradle") << """
            apply plugin: "java-library"
        """
        projectDir.file("settings.gradle").createFile()

        def mainSourceFile = projectDir.file("src/main/java/Main.java")
        mainSourceFile.text = sourceFileWithGreeting("Hello World!")

        when:
        inDirectory(projectDir)
        withWatchFs().run "assemble"
        then:
        executedAndNotSkipped ":assemble"

        when:
        FileUtils.cleanDirectory(projectDir)
        waitForChangesToBePickedUp()
        then:
        projectDir.delete()
    }

    def "the caches dir in the Gradle user home is part of the global caches"() {
        def globalCachesLocation = executer.gradleUserHomeDir.file('caches').absolutePath
        buildFile << """
            assert services.get(${GlobalCacheLocations.name}).isInsideGlobalCache('${TextUtil.escapeString(globalCachesLocation)}')
        """

        expect:
        succeeds "help"
    }

    def "does not watch files in #repositoryType file repositories"() {
        def repo = this."${repositoryType}"("repo")
        def moduleA = repo.module('group', 'projectA', '9.1')
        moduleA.publish()

        def projectDir = file("project")
        projectDir.file("build.gradle") << """
            configurations { implementation }
            repositories { ${repositoryType} { url "${repo.uri}" } }
            dependencies { implementation 'group:projectA:9.1' }

            task retrieve(type: Sync) {
                from configurations.implementation
                into 'build'
            }
        """
        executer.beforeExecute { inDirectory(projectDir) }

        when:
        withWatchFs().run "retrieve", "--info"
        then:
        assertWatchedHierarchies([projectDir])

        where:
        repositoryType | artifactFileProperty
        "maven"        | "artifactFile"
        "mavenLocal"   | "artifactFile"
        "ivy"          | "jarFile"
    }

    def "does not watch mavenLocal when not declared and dependency is copied into cache"() {
        server.start()
        def mavenRepository = maven("repo")
        def mavenHttpRepository = new MavenHttpRepository(server, mavenRepository)
        m2.generateGlobalSettingsFile()
        def artifactId = "foo-watch-test"
        def remoteModule = mavenHttpRepository.module('watched.directories.maven.local.test', artifactId, "1.0").publish()
        def m2Module = m2.mavenRepo().module('watched.directories.maven.local.test', artifactId, "1.0").publish()

        def projectDir = file("projectDir")

        projectDir.file("build.gradle") << """
            repositories {
                maven { url "${mavenHttpRepository.uri}" }
            }
            configurations { compile }
            dependencies {
                compile 'watched.directories.maven.local.test:$artifactId:1.0'
            }
            task retrieve(type: Sync) {
                from configurations.compile
                into 'build'
            }
        """
        executer.beforeExecute { inDirectory(projectDir) }

        remoteModule.pom.expectHead()
        remoteModule.pom.sha1.expectGet()
        remoteModule.artifact.expectHead()
        remoteModule.artifact.sha1.expectGet()

        when:
        using m2
        withWatchFs().run 'retrieve', "--info"

        then:
        projectDir.file("build/$artifactId-1.0.jar").assertIsCopyOf(m2Module.artifactFile)
        assertWatchedHierarchies([projectDir])
    }

    def "stops watching hierarchies when the limit has been reached"() {
        buildTestFixture.withBuildInSubDir()
        def includedBuild = singleProjectBuild("includedBuild") {
            buildFile << """
                apply plugin: 'java'
            """
        }
        def consumer = singleProjectBuild("consumer") {
            buildFile << """
                apply plugin: 'java'

                dependencies {
                    implementation "org.test:includedBuild:1.0"
                }
            """
            settingsFile << """
                includeBuild("../includedBuild")
            """
        }
        executer.beforeExecute {
            inDirectory(consumer)
        }
        file("consumer/gradle.properties") << "systemProp.${VirtualFileSystemServices.MAX_HIERARCHIES_TO_WATCH_PROPERTY.systemPropertyName}=1"

        when:
        withWatchFs().run "assemble", "--info"
        then:
        executedAndNotSkipped(":includedBuild:jar")
        assertWatchedHierarchies([includedBuild])
        postBuildOutputContains("Watching too many directories in the file system (watching 2, limit 1), dropping some state from the virtual file system")
    }

    def "does not show unsupported watching hierarchies warning for test directory"() {
        buildFile << """
            task myTask {
                def inputFile = file("input.txt")
                def outputFile = file("output.txt")
                inputs.file(inputFile)
                outputs.file(outputFile)
                doLast {
                    outputFile.text = inputFile.text
                }
            }
        """
        file("input.txt").text = "input"

        when:
        run "myTask", "--debug"
        then:
        assertWatchedHierarchies([testDirectory])
        result.assertNotPostBuildOutput("Some of the file system contents retained in the virtual file system are on file systems that Gradle doesn't support watching.")
    }

    @Requires(UnitTestPreconditions.Windows)
    def "does not start watching on unsupported file system"() {
        def testDirectoryProviderOnUnsupportedDrive = TestNameTestDirectoryProvider.forFatDrive(getClass())
        def projectDir = testDirectoryProviderOnUnsupportedDrive.createDir("project")
        projectDir.file('build.gradle') << """
            task myTask {
                def inputFile = file("input.txt")
                def outputFile = file("output.txt")
                inputs.file(inputFile)
                outputs.file(outputFile)
                doLast {
                    outputFile.text = inputFile.text
                }
            }
        """
        projectDir.file("input.txt").text = "input"
        executer.inDirectory(projectDir)

        when:
        run "myTask", "--info"
        then:
        assertWatchedHierarchies([])
        // We don't remove actual usable content at the end of the build, though we remove some dangling paths.
        // This is because the project directory is underneath the unsupported file system, so after invalidating D: the project root node is removed from the VFS.
        result.assertHasPostBuildOutput("Some of the file system contents retained in the virtual file system are on file systems that Gradle doesn't support watching.")
        outputContains("Not watching ${projectDir.absolutePath} since the file system is not supported")

        cleanup:
        testDirectoryProviderOnUnsupportedDrive.cleanup()
    }

    void assertWatchableHierarchies(List<Set<File>> expectedWatchableHierarchies) {
        assert determineWatchableHierarchies(output) == expectedWatchableHierarchies
    }

    void assertWatchedHierarchies(Iterable<File> expected) {
        if (!hierarchicalWatcher) {
            // No hierarchies to expect
            return
        }
        def watchedHierarchies = output.readLines()
            .find { it.contains("Watched directory hierarchies: [") }
            .with { line ->
                def matcher = line =~ /Watched directory hierarchies: \[(.*)]/
                String directories = matcher[0][1]
                return (directories.empty
                    ? []
                    : directories.split(', ').collect { new File(it) }
                ) as Set
            }

        assert watchedHierarchies == (expected as Set)
    }

    private static boolean isHierarchicalWatcher() {
        !OperatingSystem.current().linux
    }

    private static List<Set<File>> determineWatchableHierarchies(String output) {
        output.readLines()
            .findAll { it.contains("] as hierarchies to watch") }
            .collect { line ->
                def matcher = line =~ /Now considering \[(.*)] as hierarchies to watch/
                String directories = matcher[0][1]
                return directories.split(', ').collect { new File(it) } as Set
            }
    }
}
