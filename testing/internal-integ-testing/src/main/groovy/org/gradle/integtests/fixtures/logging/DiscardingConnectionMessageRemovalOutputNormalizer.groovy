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

/**
 * Remove lines like:
 * Timed out waiting for finished message from client socket connection from /127.0.0.1:35693 to /127.0.0.1:57026. Discarding connection.
 */
class DiscardingConnectionMessageRemovalOutputNormalizer implements OutputNormalizer {
    @Override
    String normalize(String commandOutput, ExecutionMetadata executionMetadata) {
        return commandOutput.readLines()
            .findAll { !isDiscardingConnectionMessage(it) }
            .join(System.getProperty("line.separator"))
    }

    private static boolean isDiscardingConnectionMessage(String line) {
        line = line.trim()
        return line.startsWith("Timed out waiting for finished message from client socket connection") &&
            line.endsWith("Discarding connection.")
    }
}
