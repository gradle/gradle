/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.performance

import groovy.transform.CompileStatic
import org.apache.commons.io.FilenameUtils
import org.gradle.performance.fixture.BuildExperimentSpec
import org.gradle.performance.fixture.GradleBuildExperimentSpec
import org.gradle.performance.fixture.PerformanceTestRunner
import org.gradle.profiler.BuildMutator
import org.gradle.profiler.InvocationSettings
import org.gradle.profiler.ScenarioContext
import org.gradle.profiler.ide.IdeSyncAction
import org.gradle.profiler.ide.tools.AndroidStudioFinder

@CompileStatic
class AndroidSyncPerformanceTestFixture {

    // Studio JVM args provided by the performance test task
    // See `gradlebuild.integrationtests.ide.AndroidStudioSystemProperties`
    private static final String STUDIO_JVM_ARGS_SYSTEM_PROP = "studioJvmArgs"

    /**
     * Modifies a runner to execute an Android Studio sync.
     *
     * <h3>System requirements</h3>
     * <ul>
     *     <li>{@code ANDROID_SDK_ROOT} environment variable pointing to the Android SDK
     *         (on macOS typically {@code $HOME/Library/Android/sdk}).
     *     <li>An Android Studio installation (see below).
     * </ul>
     *
     * <h3>Android Studio installation</h3>
     * <p>The Studio home is resolved by {@code AndroidStudioFinder} in the following order:</p>
     * <ol>
     *     <li>{@code -PautoDownloadAndroidStudio=true} &mdash; the build automatically downloads
     *         and unpacks Android Studio; no local installation is needed.
     *     <li>{@code STUDIO_PROFILER_HOME} environment variable or {@code -PstudioHome} property
     *         &mdash; explicit path to an existing Android Studio installation.
     *     <li>(macOS only) Any {@code /Applications/Android Studio*.app} matching the conventional install location.
     * </ol>
     *
     * <h3>Options</h3>
     * <ul>
     *     <li>{@code -PrunAndroidStudioInHeadlessMode=true} &mdash; run Android Studio in headless mode.
     * </ul>
     *
     * <h3>Parameter passing</h3>
     * <p>
     * The options above are Gradle properties resolved at build time by the performance test task.
     * {@code AndroidStudioSystemProperties} converts them into JVM system properties
     * that are passed to the test JVM, such as {@code studioJvmArgs}.
     * This fixture then reads those system properties at runtime to configure the Studio sync execution.
     */
    static void configureStudio(PerformanceTestRunner runner) {
        runner.addInterceptor(new StudioExecutionInterceptor())
        assert System.getenv("ANDROID_SDK_ROOT") != null
        String androidSdkRootPath = System.getenv("ANDROID_SDK_ROOT")
        runner.addBuildMutator { invocation -> new LocalPropertiesMutator(invocation, FilenameUtils.separatorsToUnix(androidSdkRootPath)) }
    }

    private static class StudioExecutionInterceptor implements PerformanceTestRunner.ExecutionInterceptor {
        @Override
        void intercept(GradleBuildExperimentSpec.GradleBuilder builder) {
            builder.invocation {
                buildAction(new IdeSyncAction())
                useAndroidStudio(true)
                studioSandboxDir(getStudioSandbox(workingDirectory))
                studioInstallDir(AndroidStudioFinder.findStudioHome())
                studioJvmArgs(System.getProperty(STUDIO_JVM_ARGS_SYSTEM_PROP)?.tokenize(",") ?: [])
            }
        }

        @Override
        void handleFailure(Throwable failure, BuildExperimentSpec spec) {
            File studioSandboxDir = getStudioSandbox(spec.workingDirectory)
            File logFile = new File(studioSandboxDir, "logs/idea.log")
            String message = logFile.exists() ? "\n${logFile.text}" : "Android Studio log file '${logFile}' doesn't exist, nothing to print."
            println("[ANDROID STUDIO LOGS] $message")
        }

        private static File getStudioSandbox(File workingDirectory) {
            return new File(workingDirectory, "studio-sandbox")
        }
    }

    private static class LocalPropertiesMutator implements BuildMutator {
        private final String androidSdkRootPath
        private final InvocationSettings invocation

        LocalPropertiesMutator(InvocationSettings invocation, String androidSdkRootPath) {
            this.invocation = invocation
            this.androidSdkRootPath = androidSdkRootPath
        }

        @Override
        void beforeScenario(ScenarioContext context) {
            def localProperties = new File(invocation.projectDir, "local.properties")
            localProperties << "\nsdk.dir=$androidSdkRootPath\n"
        }
    }

}
