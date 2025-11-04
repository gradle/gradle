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

/**
 * Trait to provide custom JUnit Test Engines to integration tests.
 * <p>
 * Note that this trait can <strong>NOT BE SHARED</strong> by multiple test classes in the same project
 * that share a class name prefix (e.g. MyClassRedTest and MyClassBlueTest) because of the way our testing
 * infrastructure sets up test directories.  Both of these test classes would use the same engine copy-to
 * dir, and thus interfere with each other.
 */
@SelfType(AbstractIntegrationSpec)
trait TestEnginesFixture {
    private static final String ENGINE_COPY_TO_DIR_NAME = "test-engine-build"

    private static File engineBuildDir
    private static String engineJarLibPath

    abstract List<TestEngines> getEnginesToSetup()

    def setupSpec() {
        // Create a custom test directory provider to isolate the engine build as a sibling of the test method dirs
        TestNameTestDirectoryProvider customTestDirectoryProvider = new TestNameTestDirectoryProvider.ParentDirectoryProvider(this.getClass())

        // Copy required test engine source to the root of the test directory structure for this test class
        TestResources resources = new TestResources(customTestDirectoryProvider, TestEngines.class, TestEngines.class)
        assert resources.maybeCopy("shared")
        getEnginesToSetup().forEach {
            assert resources.maybeCopy(it.name)
        }

        // Switch to engine build directory for this setup
        GradleDistribution distribution = new UnderDevelopmentGradleDistribution(IntegrationTestBuildContext.INSTANCE)
        GradleExecuter engineBuilder = new GradleContextualExecuter(distribution, customTestDirectoryProvider, IntegrationTestBuildContext.INSTANCE)
        engineBuildDir = customTestDirectoryProvider.testDirectory.file(ENGINE_COPY_TO_DIR_NAME)
        engineBuilder.inDirectory(engineBuildDir)
            .withRepositoryMirrors()
            .withConsole(ConsoleOutput.Verbose)

        // Build the test engine jar
        engineBuilder.withTasks("build").run()

        // And make the built jar's path available
        engineJarLibPath = engineBuildDir.file("build/libs/${ENGINE_COPY_TO_DIR_NAME}.jar").absolutePath
    }

    def cleanupSpec() {
        engineBuildDir.deleteDir()
    }

    String enableEngineForSuite() {
        return """
                useJUnitJupiter()

                dependencies {
                    implementation files("${TextUtil.normaliseFileSeparators(engineJarLibPath)}")
                }
        """
    }

    enum TestEngines {
        BASIC_RESOURCE_BASED("rbt-engine"),
        RESOURCE_AND_CLASS_BASED("resource-and-class-engine"),
        FAILS_DISCOVERY_RESOURCE_BASED("fails-discovery-rbt-engine"),
        FAILS_EXECUTION_RESOURCE_BASED("fails-execution-rbt-engine")

        private final String name

        TestEngines(final String name) {
            this.name = name
        }

        String getName() {
            return name
        }
    }
}
