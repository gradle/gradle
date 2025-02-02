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

import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.fixtures.executer.GradleDistribution
import org.gradle.integtests.fixtures.executer.IntegrationTestBuildContext
import org.gradle.integtests.fixtures.executer.UnderDevelopmentGradleDistribution
import org.gradle.test.fixtures.file.CleanupTestDirectory
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Timeout

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Tests that runs a project import to IDE, with an provisioning of the desired version.
 *
 * Provisioned IDEs are cached in the {@link AbstractIdeSyncTest#getIdeHome} directory.
 * @see <a href="https://github.com/gradle/gradle-ide-starter">gradle-ide-starter</a>
 */
@Timeout(600)
@CleanupTestDirectory
abstract class AbstractIdeSyncTest extends Specification {

    // https://youtrack.jetbrains.com/articles/IDEA-A-21/IDEA-Latest-Builds-And-Release-Notes
    final static String IDEA_COMMUNITY_VERSION = "2024.3-rc"
    // https://developer.android.com/studio/archive
    final static String ANDROID_STUDIO_VERSION = "2024.3.1.6"

    @Rule
    final TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider(getClass())

    private final GradleDistribution distribution = new UnderDevelopmentGradleDistribution(getBuildContext())

    IntegrationTestBuildContext getBuildContext() {
        return IntegrationTestBuildContext.INSTANCE
    }

    /**
     * Runs a full sync process for the build-under-test with a given Android Studio version.
     */
    protected void androidStudioSync(String version) {
        ideSync("ai-$version")
    }

    /**
     * Runs a full sync process for the build-under-test with a given IntelliJ IDEA Community version.
     * <p>
     * The version can be optionally suffixed with a "build type", which is one of {@code release}, {@code rc}, {@code eap}.
     * For instance, {@code 2024.2-eap}. When the build type is not provided, it defaults to {@code release}.
     * <p>
     */
    protected void ideaSync(String version) {
        ideSync("ic-$version")
    }

    /**
     * Runs a full sync with a given IDE as an external process.
     * The IDE distribution is automatically downloaded if required.
     */
    private void ideSync(String ide) {
        def gradleDist = distribution.gradleHomeDir.toPath()
        runIdeStarterWith(gradleDist, testDirectory.toPath(), ideHome, ide)
    }

    protected TestFile getTestDirectory() {
        temporaryFolder.testDirectory
    }

    protected TestFile file(Object... path) {
        if (path.length == 1 && path[0] instanceof TestFile) {
            return path[0] as TestFile
        }
        testDirectory.file(path)
    }

    private void runIdeStarterWith(
        Path gradleDist,
        Path testProject,
        Path ideHome,
        String ide
    ) {
        def gradleDistOption = "--gradle-dist=$gradleDist"
        def projectOption = "--project=$testProject"
        def ideHomeOption = "--ide-home=$ideHome"
        def ideOption = "--ide=$ide"

        def syncProcessBuilder = new ProcessBuilder(findIdeStarter().toString(), gradleDistOption, projectOption, ideHomeOption, ideOption)
            .directory(testDirectory)
            .redirectError(ProcessBuilder.Redirect.INHERIT)
            .redirectOutput(ProcessBuilder.Redirect.INHERIT)

        syncProcessBuilder.environment().put("JAVA_HOME", AvailableJavaHomes.jdk17.javaHome.absolutePath)

        def syncProcess = syncProcessBuilder.start()
        Runtime.getRuntime().addShutdownHook {
            try {
                syncProcess.destroy();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        assert syncProcess.waitFor() == 0
    }

    private static Path findIdeStarter() {
        def ideStarterDirs = Files.newDirectoryStream(Paths.get("build/ideStarter")).asList()
        switch (ideStarterDirs.size()) {
            case 1:
                def path = ideStarterDirs[0].resolve("bin/app").toAbsolutePath()
                assert Files.isRegularFile(path): "Unexpected gradle-ide-starter layout"
                return path
            case 0:
                throw new IllegalStateException("gradle-ide-starter is missing")
            default:
                throw new IllegalStateException("More than one gradle-ide-starter found: $ideStarterDirs")
        }
    }

    private Path getIdeHome() {
        def ideHome = getBuildContext().gradleUserHomeDir.file("ide")
        if (!ideHome.exists()) {
            ideHome.mkdirs()
        }
        return ideHome.toPath()
    }
}
