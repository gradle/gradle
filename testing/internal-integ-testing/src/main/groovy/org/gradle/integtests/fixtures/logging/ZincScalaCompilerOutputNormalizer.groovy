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
 * Removes Scala Zinc compilation time logging from the build-init Scala samples output.
 * Also removes the potential warning around using `-target` instead of `-release`.
 * */
class ZincScalaCompilerOutputNormalizer implements OutputNormalizer {

    private static final PATTERN = Pattern.compile(
        "(Scala Compiler interface compilation took ([0-9]+ hrs )?([0-9]+ mins )?[0-9.]+ secs\n\n)" +
            "|(\\[Warn] : -target is deprecated: Use -release instead to compile against the correct platform API.\none warning found\n\n)",
        Pattern.MULTILINE
    )

    @Override
    String normalize(String output, ExecutionMetadata executionMetadata) {
        executionMetadata.tempSampleProjectDir.name.startsWith('building-scala')
            ? normalizeScalaOutput(output)
            : output
    }

    private String normalizeScalaOutput(String output) {
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
