/*
 * Copyright 2018 the original author or authors.
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
import org.gradle.integtests.fixtures.executer.OutputScrapingExecutionResult
import org.gradle.internal.logging.ConsoleRenderer
import org.gradle.exemplar.executor.ExecutionMetadata
import org.gradle.exemplar.test.normalizer.OutputNormalizer

import java.util.regex.Pattern

@CompileStatic
class SampleOutputNormalizer implements OutputNormalizer {
    private static final String NORMALIZED_SAMPLES_PATH = "/home/user/gradle/samples"
    private static final String NORMALIZED_SAMPLES_FILE_URL = "file:///home/user/gradle/samples/"

    @Override
    String normalize(String commandOutput, ExecutionMetadata executionMetadata) {
        // Replace temp sample project dir file paths
        String escapedFilePath = Pattern.quote(executionMetadata.tempSampleProjectDir.canonicalPath)
        commandOutput = commandOutput.replaceAll(escapedFilePath, NORMALIZED_SAMPLES_PATH)

        // Replace sample project dir clickable file:/// URLs
        String escapedFileUrl = Pattern.quote(new ConsoleRenderer().asClickableFileUrl(executionMetadata.tempSampleProjectDir))
        commandOutput = commandOutput.replaceAll(escapedFileUrl, NORMALIZED_SAMPLES_FILE_URL)

        return OutputScrapingExecutionResult.from(commandOutput, "").normalizedOutput
    }
}
