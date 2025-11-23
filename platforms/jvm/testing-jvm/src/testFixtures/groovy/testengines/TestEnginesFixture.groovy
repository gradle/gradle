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
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.TestResources
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.integtests.fixtures.executer.GradleDistribution
import org.gradle.integtests.fixtures.executer.GradleExecuter
import org.gradle.integtests.fixtures.executer.IntegrationTestBuildContext
import org.gradle.integtests.fixtures.executer.UnderDevelopmentGradleDistribution
import org.gradle.test.fixtures.file.TestDirectoryProvider
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider.UniquePerTestClassDirectoryProvider
import org.gradle.util.internal.TextUtil

/**
 * Trait to provide custom JUnit {@link org.junit.platform.engine.TestEngine TestEngine}s to integration tests.
 */
@SelfType(AbstractIntegrationSpec)
trait TestEnginesFixture {
    private static final String ENGINE_COPY_TO_DIR_NAME = "test-engine-build"

    private static File engineBuildDir
    private static String engineJarLibPath
    private static File perClassDir

    abstract List<TestEngines> getEnginesToSetup()

    def setupSpec() {
        // Create a custom test directory provider to isolate the engine build per test class
        TestDirectoryProvider testClassDirectoryProvider = new UniquePerTestClassDirectoryProvider(this.getClass())
        TestResources resources = new TestResources(testClassDirectoryProvider, TestEngines.class, this.getClass())
        perClassDir = testClassDirectoryProvider.getTestDirectory().getParentFile()

        assert resources.maybeCopy("shared")
        getEnginesToSetup().forEach {
            assert resources.maybeCopy(it.name)
        }

        // Switch to engine build directory for this setup
        GradleDistribution distribution = new UnderDevelopmentGradleDistribution(IntegrationTestBuildContext.INSTANCE)
        GradleExecuter engineBuilder = new GradleContextualExecuter(distribution, testClassDirectoryProvider, IntegrationTestBuildContext.INSTANCE)
        engineBuildDir = testClassDirectoryProvider.testDirectory.file(ENGINE_COPY_TO_DIR_NAME)

        // Build the test engine jar
        engineBuilder.inDirectory(engineBuildDir)
            .withRepositoryMirrors()
            .withTasks("build")
            .run()

        // And make the built jar's path available
        engineJarLibPath = engineBuildDir.file("build/libs/${ENGINE_COPY_TO_DIR_NAME}.jar").absolutePath
    }

    def cleanupSpec() {
        assert perClassDir.deleteDir()
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
        BASIC_RESOURCE_BASED_DYNAMIC("dynamic-rbt-engine"),
        BASIC_RESOURCE_BASED_PARALLEL("parallel-rbt-engine"),
        MULTI_FILE_RESOURCE_BASED("multi-file-rbt-engine"),
        RESOURCE_AND_CLASS_BASED("resource-and-class-engine"),
        FAILS_DISCOVERY_RESOURCE_BASED("fails-discovery-rbt-engine"),
        FAILS_EXECUTION_RESOURCE_BASED("fails-execution-rbt-engine"),
        MATCHES_NOTHING_ENGINE("matches-nothing-engine")

        private final String name

        TestEngines(final String name) {
            this.name = name
        }

        String getName() {
            return name
        }
    }
}
