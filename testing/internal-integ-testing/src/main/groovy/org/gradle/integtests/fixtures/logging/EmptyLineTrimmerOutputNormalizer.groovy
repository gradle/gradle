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

import org.gradle.exemplar.executor.ExecutionMetadata
import org.gradle.exemplar.test.normalizer.OutputNormalizer

import java.util.regex.Pattern

/**
 * Allows a blank empty line in your sample output to match a blank line with any number of spaces in the actual output.
 *
 * This exists to avoid having to measure out "prefix" whitespace indentation and match it exactly in your sample output.
 */
class EmptyLineTrimmerOutputNormalizer implements OutputNormalizer {
    private static final Pattern EMPTY_LINES = Pattern.compile(/^\s+$/, Pattern.MULTILINE);

    @Override
    String normalize(String commandOutput, ExecutionMetadata executionMetadata) {
        EMPTY_LINES.matcher(commandOutput).replaceAll("")
    }
}
