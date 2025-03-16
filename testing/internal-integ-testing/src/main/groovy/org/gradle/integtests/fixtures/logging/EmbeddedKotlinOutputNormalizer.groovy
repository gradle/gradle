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

package org.gradle.integtests.fixtures.logging

import org.gradle.exemplar.executor.ExecutionMetadata
import org.gradle.exemplar.test.normalizer.OutputNormalizer

import java.util.regex.Pattern

/**
 * Removes harmless warnings from the build-init Kotlin samples output.
 *
 * The warnings are produced by the Kotlin compiler when mixing Java versions:
 * <ul>
 *     <li>'compileJava' task (current target is 16) and 'compileKotlin' task (current target is 1.8) jvm target compatibility should be set to the same Java version.</li>
 *     <li>'compileTestJava' task (current target is 16) and 'compileTestKotlin' task (current target is 1.8) jvm target compatibility should be set to the same Java version.</li>
 * </ul>
 * */
class EmbeddedKotlinOutputNormalizer implements OutputNormalizer {

    private static final PATTERN = Pattern.compile(
        "'compile(Test)?Java' task \\(current target is [0-9.]+\\) and 'compile(Test)?Kotlin' task \\(current target is [0-9.]+\\) jvm target compatibility should be set to the same Java version.\n\n",
        Pattern.MULTILINE
    )

    @Override
    String normalize(String output, ExecutionMetadata executionMetadata) {
        executionMetadata.tempSampleProjectDir.name.startsWith('building-kotlin')
            ? normalizeKotlinOutput(output)
            : output
    }

    private String normalizeKotlinOutput(String output) {
        PATTERN.matcher(output)
            .replaceAll("")
            .with {
                // remove starting empty line
                it.startsWith('\n')
                    ? it.substring(1)
                    : it
            }
    }
}
