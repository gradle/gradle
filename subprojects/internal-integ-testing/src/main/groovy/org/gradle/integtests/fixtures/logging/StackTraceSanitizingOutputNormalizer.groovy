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
 * Removes harmless stack traces from the output.
 *
 * For example, the following output:
 *
 * ```
 * java.io.FileNotFoundException: https://dl.google.com/android/repository/sys-img/android-desktop/sys-img2-1.xml
 * 	at sun.net.www.protocol.http.HttpURLConnection.getInputStream0(HttpURLConnection.java:1896)
 * ```
 * */
class StackTraceSanitizingOutputNormalizer implements OutputNormalizer {
    private static final PATTERN = Pattern.compile(
        "java.io.FileNotFoundException: .*sys-img2-1.xml\n*(\\s+at .*\n)*",
        Pattern.MULTILINE
    )

    @Override
    String normalize(String output, ExecutionMetadata executionMetadata) {
        executionMetadata.tempSampleProjectDir.name.contains('build-android-app')
            ? PATTERN.matcher(output).replaceAll("")
            : output
    }
}
