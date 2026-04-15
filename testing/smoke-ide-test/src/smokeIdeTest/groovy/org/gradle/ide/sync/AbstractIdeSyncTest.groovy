/*
 * Copyright 2024 the original author or authors.
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
package org.gradle.ide.sync

import org.gradle.api.internal.file.TestFiles
import org.gradle.ide.starter.IdeScenario
import org.gradle.ide.sync.fixtures.IsolatedProjectsIdeSyncFixture
import org.gradle.initialization.DefaultBuildCancellationToken
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.fixtures.executer.GradleDistribution
import org.gradle.integtests.fixtures.executer.IntegrationTestBuildContext
import org.gradle.integtests.fixtures.executer.UnderDevelopmentGradleDistribution
import org.gradle.process.internal.DefaultClientExecHandleBuilder
import org.gradle.process.internal.ExecHandleState
import org.gradle.test.fixtures.file.CleanupTestDirectory
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.jspecify.annotations.Nullable
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Timeout

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.Executors

/**
 * Tests that runs a project import to IDE, with an provisioning of the desired version.
 *
 * Provisioned IDEs are cached in the {@link AbstractIdeSyncTest#getIdeHome} directory.
 * @see <a href="https://github.com/gradle/gradle-ide-starter">gradle-ide-starter</a>
 */
// gradle-ide-starter timeout + 30sec for wrap up
@Timeout(630)
@CleanupTestDirectory
abstract class AbstractIdeSyncTest extends Specification {

    private enum IDE {
        ANDROID_STUDIO,
        INTELLIJ_IDEA_ULTIMATE,
    }

    @Rule
    final TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider(getClass())

    private final GradleDistribution distribution = new UnderDevelopmentGradleDistribution(getBuildContext())

    Integer ideXmxMb = null

    // By default, an IDE is being closed immediately when the job is finished.
    // For debugging purposes sometimes it's desirable to keep it opened.
    boolean ideKeepAlive = false

    IntegrationTestBuildContext getBuildContext() {
        return IntegrationTestBuildContext.INSTANCE
    }

    IsolatedProjectsIdeSyncFixture getReport() {
        return new IsolatedProjectsIdeSyncFixture(projectDirectory)
    }

    /**
     * Runs a full sync with Android Studio as an external process.
     * Optionally, an {@link IdeScenario} may be provided.
     * The IDE distribution is provisioned by IdeProvisioningPlugin.
     */
    protected void androidStudioSync(
        @Nullable IdeScenario scenario = null
    ) {
        ideSync(IDE.ANDROID_STUDIO, scenario)
    }

    /**
     * Runs a full sync with IntelliJ IDEA Ultimate as an external process.
     * Optionally, an {@link IdeScenario} may be provided.
     * The IDE distribution is provisioned by IdeProvisioningPlugin.
     */
    protected void ideaSync(
        @Nullable IdeScenario scenario = null
    ) {
        ideSync(IDE.INTELLIJ_IDEA_ULTIMATE, scenario)
    }

    private void ideSync(IDE ide, IdeScenario scenario) {
        def scenarioFile = writeScenario(scenario)
        def gradleDist = distribution.gradleHomeDir.toPath()
        runIdeStarterWith(gradleDist, projectDirectory.toPath(), ideHome, testDirectory.toPath(), scenarioFile, ide)
    }

    protected TestFile getTestDirectory() {
        temporaryFolder.testDirectory
    }

    protected TestFile getProjectDirectory() {
        testDirectory.createDir("project-under-test")
    }

    protected TestFile file(Object... path) {
        if (path.length == 1 && path[0] instanceof TestFile) {
            return path[0] as TestFile
        }
        testDirectory.file(path)
    }

    protected TestFile projectFile(Object... path) {
        if (path.length == 1 && path[0] instanceof TestFile) {
            return path[0] as TestFile
        }
        projectDirectory.file(path)
    }

    private void runIdeStarterWith(
        Path gradleDist,
        Path testProject,
        Path ideHome,
        Path testHome,
        @Nullable Path scenario,
        IDE ide
    ) {
        def args = [
            "--gradle-dist=$gradleDist",
            "--project=$testProject",
            "--ide-home=$ideHome",
            "--test-home=$testHome",
        ]

        if (scenario != null) {
            args += "--ide-scenario=$scenario"
        }

        if (ideXmxMb != null) {
            args += "--ide-xmx=$ideXmxMb"
        }

        if (ideKeepAlive) {
            args += "--ide-keep-alive"
        }


        def archivePath
        def ideType
        switch (ide) {
            case IDE.ANDROID_STUDIO -> {
                ideType = "as-0"
                archivePath = System.getProperty("android.studio.archive")
            }
            case IDE.INTELLIJ_IDEA_ULTIMATE -> {
                ideType = "iu-0"
                archivePath = System.getProperty("idea.ultimate.archive")
            }
        }
        args += "--ide=$ideType"
        args += "--ide-archive=$archivePath"

        DefaultClientExecHandleBuilder builder = new DefaultClientExecHandleBuilder(
            TestFiles.pathToFileResolver(), Executors.newCachedThreadPool(), new DefaultBuildCancellationToken()
        )

        builder
            .setExecutable(findIdeStarter().toString())
            .args(args)
            .setWorkingDir(testDirectory)
            .setStandardOutput(System.out)
            .setErrorOutput(System.err)
            .environment("JAVA_HOME", AvailableJavaHomes.jdk21.javaHome.absolutePath)

        System.err.println("Running IDE sync with: ${builder.commandLine.join(' ')}")
        def handle = builder.build().start()
        if (handle.state == ExecHandleState.STARTED) {
            Runtime.getRuntime().addShutdownHook {
                if (handle.state == ExecHandleState.STARTED) {
                    handle.abort()
                }
            }
        }
        def result = handle.waitForFinish()
        System.err.println("IDE sync process finished: $result")
        result.rethrowFailure().assertNormalExitValue()
    }

    private static Path findIdeStarter() {
        def ideStarterPath = System.getProperty("ide.starter.path")
        assert ideStarterPath != null
        def ideStarterCandidates = Files.newDirectoryStream(Paths.get(ideStarterPath)).asList()
        switch (ideStarterCandidates.size()) {
            case 1:
                def path = ideStarterCandidates[0].resolve("bin/app").toAbsolutePath()
                assert Files.isRegularFile(path): "Unexpected gradle-ide-starter layout"
                return path
            case 0:
                throw new IllegalStateException("gradle-ide-starter is missing from '$ideStarterPath'")
            default:
                throw new IllegalStateException("More than one gradle-ide-starter found in '$ideStarterPath': $ideStarterCandidates")
        }
    }

    private Path getIdeHome() {
        def ideHome = getBuildContext().gradleUserHomeDir.file("ide")
        if (!ideHome.exists()) {
            ideHome.mkdirs()
        }
        return ideHome.toPath()
    }

    @Nullable
    private Path writeScenario(@Nullable IdeScenario scenario) {
        if (scenario == null) {
            return null
        }
        def scenarioFile = file("scenario.json").touch().toPath()
        scenario.writeTo(scenarioFile)
        return scenarioFile
    }
}
