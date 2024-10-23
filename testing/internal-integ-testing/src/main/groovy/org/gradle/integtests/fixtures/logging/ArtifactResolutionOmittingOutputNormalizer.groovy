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
import org.gradle.internal.jvm.Jvm
import org.gradle.exemplar.executor.ExecutionMetadata
import org.gradle.exemplar.test.normalizer.OutputNormalizer

import javax.annotation.Nullable

@CompileStatic
class ArtifactResolutionOmittingOutputNormalizer implements OutputNormalizer {
    @Override
    String normalize(String commandOutput, @Nullable ExecutionMetadata executionMetadata) {
        List<String> lines = commandOutput.readLines()
        if (lines.empty) {
            return ""
        }
        boolean seenWarning = false
        List<String> result = new ArrayList<String>()
        for (String line : lines) {
            if (line.matches('Download .+')) {
                // ignore
            } else if (!seenWarning && !Jvm.current().javaVersion.java7Compatible && line == 'Support for reading or changing file permissions is only available on this platform using Java 7 or later.') {
                // ignore this warning once only on java < 7
                seenWarning = true
            } else {
                result << line
            }
        }
        return result.join("\n")
    }

    String normalize(String output) {
        return normalize(output, null)
    }
}
