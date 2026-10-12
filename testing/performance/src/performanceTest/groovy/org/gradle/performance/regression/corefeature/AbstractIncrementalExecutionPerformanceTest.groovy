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
        // The current property name must be passed as well: build options resolve it before the deprecated one,
        // so a test project enabling the configuration cache in its gradle.properties (e.g. nowInAndroidBuild)
        // would otherwise silently win over the deprecated -D flag and keep the cache on in the "disabled" iterations.
        // The deprecated property is kept for baseline versions older than 8.1.
        runner.args.add("-D${StartParameterBuildOptions.ConfigurationCacheOption.PROPERTY_NAME}=${configurationCachingEnabled}")
        runner.args.add("-D${StartParameterBuildOptions.ConfigurationCacheOption.DEPRECATED_PROPERTY_NAME}=${configurationCachingEnabled}")
    }

    protected boolean configureBuildArchives(boolean useFileSystemSensitiveArchives) {
        if (useFileSystemSensitiveArchives) {
            runner.addBuildMutator { invocationSettings ->
                new EnableFileSystemSensitiveArchivesMutator(invocationSettings.projectDir)
            }
        }
    }

    protected static configurationCachingMessage(boolean configurationCachingEnabled) {
        return configurationCachingEnabled ? " with configuration caching" : ""
    }

    protected static withFileSystemSensitiveArchives(boolean reproducibleArchivesDisabled) {
        return reproducibleArchivesDisabled ? " with file system sensitive archives" : ""
    }

    class EnableFileSystemSensitiveArchivesMutator implements BuildMutator {

        private final File projectDir

        EnableFileSystemSensitiveArchivesMutator(File projectDir) {
            this.projectDir = projectDir
        }

        @Override
        void beforeScenario(ScenarioContext context) {
            def groovySettingsFile = new File(projectDir, "settings.gradle")
            def kotlinSettingsFile = new File(projectDir, "settings.gradle.kts")
            if (groovySettingsFile.exists()) {
                enableFileSystemSensitiveArchives(
                    groovySettingsFile,
                    "tasks.withType(AbstractArchiveTask)",
                    "preserveFileTimestamps",
                    "reproducibleFileOrder"
                )
            } else if (kotlinSettingsFile.exists()) {
                enableFileSystemSensitiveArchives(
                    kotlinSettingsFile,
                    "tasks.withType<AbstractArchiveTask>()",
                    "isPreserveFileTimestamps",
                    "isReproducibleFileOrder"
                )
            } else {
                throw new IllegalStateException("No settings file found in project directory: $projectDir")
            }
        }

        private void enableFileSystemSensitiveArchives(File settingsFile, String withType, String preserveFileTimestamps, String reproducibleFileOrder) {
            settingsFile << """
                |settings.gradle.lifecycle.beforeProject {
                |    ${withType}.configureEach {
                |        $preserveFileTimestamps = true
                |        $reproducibleFileOrder = false
                |        useFileSystemPermissions()
                |    }
                |}
            """.stripMargin()
        }
    }
}
