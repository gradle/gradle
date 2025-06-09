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

package org.gradle.integtests.fixtures.logging

import org.gradle.exemplar.executor.ExecutionMetadata
import org.gradle.exemplar.test.normalizer.OutputNormalizer
import org.gradle.internal.os.OperatingSystem

/**
 * Windows consoles do not support registered trademark symbols, so we output a different message for the --scan recommendation on Windows.
 * We use this normalizer so our samples tests don't need to care about this.
 */
class BuildScanRecommendationOutputNormalizer implements OutputNormalizer {
    @Override
    String normalize(String commandOutput, ExecutionMetadata executionMetadata) {
        if (OperatingSystem.current().isWindows()) {
            return commandOutput.replace(
                "> Run with --scan to generate a Build Scan (Powered by Develocity). Build Scan and Develocity are registered trademarks of Gradle, Inc.",
                "> Run with --scan to generate a Build Scan® (Powered by Develocity®)."
            )
        }

        return commandOutput;
    }
}
