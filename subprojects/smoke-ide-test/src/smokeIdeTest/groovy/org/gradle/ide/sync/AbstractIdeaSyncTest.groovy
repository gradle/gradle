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

import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.junit4.JUnit4StarterRule
import com.intellij.ide.starter.junit4.JUnit4StarterRuleKt
import com.intellij.ide.starter.models.TestCase
import org.gradle.integtests.fixtures.executer.GradleDistribution
import org.gradle.integtests.fixtures.executer.IntegrationTestBuildContext
import org.gradle.integtests.fixtures.executer.UnderDevelopmentGradleDistribution
import org.gradle.test.fixtures.file.CleanupTestDirectory
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import org.junit.rules.TestName
import spock.lang.Specification
import spock.lang.Timeout

import java.nio.file.Path


/**
 * Tests that runs a project import to IDEA, with an provisioning of the desired version.
 *
 * Provisioned IDEs are cached in the {@link AbstractIdeaSyncTest#getIdeHome} directory.
 */
@Timeout(600)
@CleanupTestDirectory
abstract class AbstractIdeaSyncTest extends Specification {

    @Rule
    final TestName testName = new TestName()

    @Rule
    final JUnit4StarterRule testContextFactory = JUnit4StarterRuleKt.initJUnit4StarterRule()

    @Rule
    final TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider(getClass())

    private final GradleDistribution distribution = new UnderDevelopmentGradleDistribution(getBuildContext())

    def setup() {
        IdeStarterIntegration.INSTANCE.configureIdeHome(
            getBuildContext().gradleUserHomeDir.file("ide").toPath()
        )
    }

    IntegrationTestBuildContext getBuildContext() {
        return IntegrationTestBuildContext.INSTANCE
    }

    /**
     * Downloads, if it absent in {@link #getIdeHome} dir, Idea Community with a passed build type and version
     * and runs a project import to it.
     */
    protected void ideaSync(String buildType, String version) {
        TestCase testCase = new TestCase(
            IdeStarterIntegration.INSTANCE.getIdeaCommunity(buildType, version),
            IdeStarterIntegration.INSTANCE.getLocalProject(testDirectory.toPath()),
            Collections.EMPTY_LIST,
            false
        )

        IDETestContext testContext = testContextFactory
            .initializeTestContext(testName.methodName, testCase, false)
            .prepareProjectCleanImport()

        useLocalGradleDistributionIn(testContext)

        IdeStarterIntegration.INSTANCE.runSync(testContext)
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

    // This sets current Gradle distribution as a Gradle version to run with to IDEA
    private void useLocalGradleDistributionIn(IDETestContext testContext) {
        testContext.paths.configDir.resolve("options/gradle.default.xml").toFile() << """
            <application>
                <component name="GradleDefaultProjectSettings">
                    <option name="distributionType" value="LOCAL" />
                    <option name="gradleHome" value="${distribution.gradleHomeDir.absolutePath}" />
                </component>
            </application>
        """
    }

    private Path getIdeHome() {
        return getBuildContext().gradleUserHomeDir.file("ide").toPath()
    }
}
