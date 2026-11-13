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

package org.gradle.api.problems.fixtures

import org.gradle.api.Action
import org.gradle.api.problems.AdditionalData
import org.gradle.api.problems.ProblemSpec
import org.gradle.api.problems.Severity

/**
 * A mock implementation of {@link ProblemSpec} that supports fluent interface
 * and tracks all method calls for verification in tests.
 */
class FakeProblemBuilder implements ProblemSpec {
    String contextualLabel
    String documentedAt
    String fileLocation
    String lineInFileLocationPath
    Integer lineInFileLocationLine
    Integer lineInFileLocationColumn
    Integer lineInFileLocationLength
    String offsetInFileLocationPath
    Integer offsetInFileLocationOffset
    Integer offsetInFileLocationLength
    boolean stackLocationCalled = false
    String details
    String solution
    Class<? extends AdditionalData> additionalDataType
    Action<?> additionalDataConfig
    Throwable exception
    Severity severity

    @Override
    ProblemSpec contextualLabel(String contextualLabel) {
        this.contextualLabel = contextualLabel
        return this
    }

    @Override
    ProblemSpec documentedAt(String url) {
        this.documentedAt = url
        return this
    }

    @Override
    ProblemSpec fileLocation(String path) {
        this.fileLocation = path
        return this
    }

    @Override
    ProblemSpec lineInFileLocation(String path, int line) {
        this.lineInFileLocationPath = path
        this.lineInFileLocationLine = line
        return this
    }

    @Override
    ProblemSpec lineInFileLocation(String path, int line, int column) {
        this.lineInFileLocationPath = path
        this.lineInFileLocationLine = line
        this.lineInFileLocationColumn = column
        return this
    }

    @Override
    ProblemSpec lineInFileLocation(String path, int line, int column, int length) {
        this.lineInFileLocationPath = path
        this.lineInFileLocationLine = line
        this.lineInFileLocationColumn = column
        this.lineInFileLocationLength = length
        return this
    }

    @Override
    ProblemSpec offsetInFileLocation(String path, int offset, int length) {
        this.offsetInFileLocationPath = path
        this.offsetInFileLocationOffset = offset
        this.offsetInFileLocationLength = length
        return this
    }

    @Override
    ProblemSpec stackLocation() {
        this.stackLocationCalled = true
        return this
    }

    @Override
    ProblemSpec details(String details) {
        this.details = details
        return this
    }

    @Override
    ProblemSpec solution(String solution) {
        this.solution = solution
        return this
    }

    @Override
    <T extends AdditionalData> ProblemSpec additionalData(Class<T> type, Action<? super T> config) {
        this.additionalDataType = type
        this.additionalDataConfig = config
        return this
    }

    @Override
    ProblemSpec withException(Throwable t) {
        this.exception = t
        return this
    }

    @Override
    ProblemSpec severity(Severity severity) {
        this.severity = severity
        return this
    }
}
