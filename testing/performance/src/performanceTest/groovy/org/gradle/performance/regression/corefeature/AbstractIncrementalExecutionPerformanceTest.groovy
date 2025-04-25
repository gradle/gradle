/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.performance.regression.corefeature

import org.gradle.initialization.StartParameterBuildOptions
import org.gradle.internal.os.OperatingSystem
import org.gradle.performance.AbstractCrossVersionPerformanceTest
import org.gradle.performance.fixture.CrossVersionPerformanceTestRunner
import org.gradle.profiler.BuildMutator
import org.gradle.profiler.ScenarioContext

class AbstractIncrementalExecutionPerformanceTest extends AbstractCrossVersionPerformanceTest {

    def setup() {
        runner.useToolingApi = true
        if (OperatingSystem.current().linux) {
            runner.warmUpRuns = 10
            runner.runs = 40
        } else {
            // Reduce the number of iterations on Windows and macOS, since the performance tests are slower there
            runner.warmUpRuns = 5
            runner.runs = 20
        }
    }

    protected boolean enableConfigurationCaching(boolean configurationCachingEnabled) {
        // use the deprecated property so it works with previous versions
        runner.args.add("-D${StartParameterBuildOptions.ConfigurationCacheOption.DEPRECATED_PROPERTY_NAME}=${configurationCachingEnabled}")
    }

    protected boolean enableReproducibleArchives(CrossVersionPerformanceTestRunner runner) {
        runner.addBuildMutator { invocationSettings ->
            new EnableReproducibleArchivesMutator(invocationSettings.projectDir)
        }
    }

    protected static configurationCachingMessage(boolean configurationCachingEnabled) {
        return configurationCachingEnabled ? " with configuration caching" : ""
    }

    class EnableReproducibleArchivesMutator implements BuildMutator {

        private final File projectDir

        EnableReproducibleArchivesMutator(File projectDir) {
            this.projectDir = projectDir
        }

        @Override
        void beforeScenario(ScenarioContext context) {
            def groovySettingsFile = new File(projectDir, "settings.gradle")
            def kotlinSettingsFile = new File(projectDir, "settings.gradle.kts")
            if (groovySettingsFile.exists()) {
                applyReproducibleArchives(
                    groovySettingsFile,
                    "tasks.withType(AbstractArchiveTask)",
                    "preserveFileTimestamps",
                    "reproducibleFileOrder"
                )
            } else if (kotlinSettingsFile.exists()) {
                applyReproducibleArchives(
                    kotlinSettingsFile,
                    "tasks.withType<AbstractArchiveTask>()",
                    "isPreserveFileTimestamps",
                    "isReproducibleFileOrder"
                )
            } else {
                throw new IllegalStateException("No settings file found in project directory: $projectDir")
            }
        }

        private void applyReproducibleArchives(File settingsFile, String withType, String preserveFileTimestamps, String reproducibleFileOrder) {
            settingsFile << """
                |settings.gradle.lifecycle.beforeProject {
                |    ${withType}.configureEach {
                |        $preserveFileTimestamps = false
                |        $reproducibleFileOrder = true
                |        dirPermissions { }
                |        filePermissions { }
                |    }
                |}
            """.stripMargin()
        }
    }
}
