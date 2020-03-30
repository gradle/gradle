/*
 * Copyright 2019 the original author or authors.
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
import org.gradle.samples.executor.ExecutionMetadata
import org.gradle.samples.test.normalizer.OutputNormalizer

@CompileStatic
class DependencyInsightOutputNormalizer implements OutputNormalizer {

    @Override
    String normalize(String output, ExecutionMetadata executionMetadata) {
        output.replaceAll("org\\.gradle\\.jvm\\.version[ ]+= [0-9]+", "org.gradle.jvm.version = 11")
            .replaceAll("org\\.gradle\\.jvm\\.version[ ]'[0-9]+'", "org.gradle.jvm.version '11'")
            .replaceAll("'org\\.gradle\\.jvm\\.version' with value '[0-9]+'", "'org.gradle.jvm.version' with value '11'")
            .replaceAll("compatib(le|ility) with Java [0-9]+", "compatib\$1 with Java 11")

    }
}
