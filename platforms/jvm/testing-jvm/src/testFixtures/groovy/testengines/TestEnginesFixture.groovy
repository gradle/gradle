/*
 * Copyright 2025 the original author or authors.
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
//file:noinspection unused

package testengines

import groovy.transform.SelfType
import org.gradle.api.logging.configuration.ConsoleOutput
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.TestResources
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.integtests.fixtures.executer.GradleDistribution
import org.gradle.integtests.fixtures.executer.GradleExecuter
import org.gradle.integtests.fixtures.executer.IntegrationTestBuildContext
import org.gradle.integtests.fixtures.executer.UnderDevelopmentGradleDistribution
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.internal.TextUtil

@SelfType(AbstractIntegrationSpec)
trait TestEnginesFixture {
    private static String engineJarLibPath

    abstract List<TestEngines> getEnginesToSetup()

    def setupSpec() {
        // Create a custom test directory provider to isolate the engine build as a sibling of the test method dirs
        TestNameTestDirectoryProvider customTestDirectoryProvider = new TestNameTestDirectoryProvider.ParentDirectoryProvider(this.getClass())

        // Copy required test engine source to the root of the test directory structure for this test class
        TestResources resources = new TestResources(customTestDirectoryProvider, TestEngines.class, TestEngines.class)
        getEnginesToSetup().forEach {
            assert resources.maybeCopy(TestEngines.BASIC_RESOURCE_BASED.name)
        }

        // Switch to engine build directory for this setup
        GradleDistribution distribution = new UnderDevelopmentGradleDistribution(IntegrationTestBuildContext.INSTANCE)
        GradleExecuter engineBuilder = new GradleContextualExecuter(distribution, customTestDirectoryProvider, IntegrationTestBuildContext.INSTANCE)
        File engineBuildDir = customTestDirectoryProvider.testDirectory.file(TestEngines.ENGINE_COPY_TO_DIR_NAME)
        engineBuilder.inDirectory(engineBuildDir)
            .withRepositoryMirrors()
            .withConsole(ConsoleOutput.Verbose)

        // Build the test engine jar
        engineBuilder.withTasks("build").run()

        // And make the built jar's path available
        engineJarLibPath = engineBuildDir.file("build/libs/${TestEngines.ENGINE_COPY_TO_DIR_NAME}.jar").absolutePath
    }

    String enableEngineForSuite() {
        return """
                useJUnitJupiter()

                dependencies {
                    implementation files("${TextUtil.normaliseFileSeparators(engineJarLibPath)}")
                }
        """
    }
}
