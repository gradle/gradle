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

package org.gradle.performance

import org.apache.commons.io.FileUtils
import org.gradle.profiler.BuildContext
import org.gradle.profiler.BuildMutator
import org.gradle.profiler.InvocationSettings
import org.gradle.profiler.Phase

class SaveScanSpoolFile implements BuildMutator {
    final InvocationSettings invocationSettings
    final String testId

    SaveScanSpoolFile(InvocationSettings invocationSettings, String testId) {
        this.invocationSettings = invocationSettings
        this.testId = testId.replaceAll(/[- ]/, '_')
    }

    @Override
    void beforeBuild(BuildContext context) {
        spoolDir().deleteDir()
    }

    @Override
    void afterBuild(BuildContext context, Throwable t) {
        def spoolDir = this.spoolDir()
        if (context.phase == Phase.MEASURE && (context.iteration == invocationSettings.buildCount) && spoolDir.exists()) {
            def targetDirectory = new File("build/scan-dumps/$testId")
            targetDirectory.deleteDir()
            FileUtils.moveToDirectory(spoolDir, targetDirectory, true)
        }
    }

    private File spoolDir() {
        new File(invocationSettings.gradleUserHome, "build-scan-data")
    }
}
