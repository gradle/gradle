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
package org.gradle.integtests.fixtures.logging

import groovy.transform.CompileStatic
import org.gradle.exemplar.executor.ExecutionMetadata
import org.gradle.exemplar.test.normalizer.OutputNormalizer

/**
 * Rewrites {@code .gradle.kts'} to {@code .gradle'} on lines that begin with
 * {@code "Execution failed for task"}. Lets a single expected-output file
 * cover both Kotlin and Groovy DSL variants of a failing snippet.
 * <p>
 * Local copy of exemplar's GradleTaskFailureBuildFileNormalizer (added in
 * samples-check 2.0.0). Re-implemented here because {@code :internal-distribution-testing}
 * targets JVM 8 for tooling-API tests and cannot consume the Java-17 2.0.0 jar.
 */
@CompileStatic
class GradleTaskFailureBuildFileNormalizer implements OutputNormalizer {
    private static final String TASK_FAILURE_PREFIX = "Execution failed for task"

    @Override
    String normalize(String commandOutput, ExecutionMetadata executionMetadata) {
        StringBuilder result = new StringBuilder(commandOutput.length())
        String[] lines = commandOutput.split("\\r?\\n", -1)
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                result.append('\n')
            }
            String line = lines[i]
            if (line.startsWith(TASK_FAILURE_PREFIX)) {
                result.append(line.replace(".gradle.kts'", ".gradle'"))
            } else {
                result.append(line)
            }
        }
        return result.toString()
    }
}
