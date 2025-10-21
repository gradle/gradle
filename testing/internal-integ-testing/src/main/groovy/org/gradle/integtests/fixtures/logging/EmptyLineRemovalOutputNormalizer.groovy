/*
 * Copyright 2020 the original author or authors.
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

class EmptyLineRemovalOutputNormalizer implements OutputNormalizer {
    private static final List<String> SAMPLES_IGNORING_EMPTY_LINE = [
        "useCacheabilityAnnotations",
        "avoidInternal",
        "multiproject-basic-multiproject_groovy_test",
        "multiproject-basic-multiproject_kotlin_test",
        "multiproject_kotlin_listProjects",
        "multiproject_groovy_listProjects",
        "maven-migration-multi-module_kotlin_project",
        "maven-migration-multi-module_groovy_projects"
    ]

    @Override
    String normalize(String commandOutput, ExecutionMetadata executionMetadata) {
        if (SAMPLES_IGNORING_EMPTY_LINE.any { executionMetadata.tempSampleProjectDir.name.contains(it) }) {
            return commandOutput.readLines()
                .collect { it.trim() }
                .findAll { !it.isEmpty() }
                .join(System.getProperty("line.separator"))
        } else {
            return commandOutput
        }
    }
}
