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
import org.gradle.profiler.studio.AndroidStudioSyncAction
import org.gradle.profiler.studio.tools.StudioFinder

@CompileStatic
class AndroidSyncPerformanceTestFixture {

    /**
     * Modifies a runner to execute an android studio sync.
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
            File sandboxDir = new File(builder.workingDirectory, "studio-sandbox")
            builder.invocation {
                buildAction(new AndroidStudioSyncAction())
                useAndroidStudio(true)
                studioSandboxDir(sandboxDir)
                studioInstallDir(StudioFinder.findStudioHome())
                studioJvmArgs(System.getProperty("studioJvmArgs")?.tokenize(",") ?: [])
            }
        }

        @Override
        void handleFailure(Throwable failure, BuildExperimentSpec spec) {
            File studioSandboxDir = new File(spec.workingDirectory, "studio-sandbox")
            File logFile = new File(studioSandboxDir, "/logs/idea.log")
            String message = logFile.exists() ? "\n${logFile.text}" : "Android Studio log file '${logFile}' doesn't exist, nothing to print."
            println("[ANDROID STUDIO LOGS] $message")
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
