/*
 * Copyright 2023 the original author or authors.
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

import org.apache.commons.lang3.StringUtils
import org.gradle.exemplar.executor.ExecutionMetadata
import org.gradle.exemplar.test.normalizer.OutputNormalizer

class ProgressLoggingOutputNormalizer implements OutputNormalizer {
    public static final String PROGRESS_LOG_LINE_PREFIX = "> Progress:";

    static String normalize(String commandOutput) {
        List<String> result = new ArrayList<>();
        final List<String> lines = Arrays.asList(commandOutput.split("\\r?\\n", -1));
        int i = 0;
        while (i < lines.size()) {
            String line = lines.get(i);
            if (!line.startsWith(PROGRESS_LOG_LINE_PREFIX)) {
                result.add(line);
            }
            i++;
        }

        return StringUtils.join(result, "\n");
    }

    @Override
    String normalize(String commandOutput, ExecutionMetadata executionMetadata) {
        return normalize(commandOutput);
    }
}
