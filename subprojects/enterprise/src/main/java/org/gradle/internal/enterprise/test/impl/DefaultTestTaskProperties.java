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

package org.gradle.internal.enterprise.test.impl;

import com.google.common.collect.ImmutableList;
import org.gradle.internal.enterprise.test.CandidateClassFile;
import org.gradle.internal.enterprise.test.InputFileProperty;
import org.gradle.internal.enterprise.test.OutputFileProperty;
import org.gradle.internal.enterprise.test.TestTaskFilters;
import org.gradle.internal.enterprise.test.TestTaskForkOptions;
import org.gradle.internal.enterprise.test.TestTaskProperties;

import java.util.stream.Stream;

class DefaultTestTaskProperties implements TestTaskProperties {

    private final boolean usingJUnitPlatform;
    private final long forkEvery;
    private final TestTaskFilters filters;
    private final TestTaskForkOptions forkOptions;
    private final ImmutableList<CandidateClassFile> candidateClassFiles;
    private final ImmutableList<InputFileProperty> inputFileProperties;
    private final ImmutableList<OutputFileProperty> outputFileProperties;

    DefaultTestTaskProperties(
        boolean usingJUnitPlatform,
        long forkEvery,
        TestTaskFilters filters,
        TestTaskForkOptions forkOptions,
        ImmutableList<CandidateClassFile> candidateClassFiles,
        ImmutableList<InputFileProperty> inputFileProperties,
        ImmutableList<OutputFileProperty> outputFileProperties
    ) {
        this.usingJUnitPlatform = usingJUnitPlatform;
        this.forkEvery = forkEvery;
        this.filters = filters;
        this.forkOptions = forkOptions;
        this.candidateClassFiles = candidateClassFiles;
        this.inputFileProperties = inputFileProperties;
        this.outputFileProperties = outputFileProperties;
    }

    @Override
    public boolean isUsingJUnitPlatform() {
        return usingJUnitPlatform;
    }

    @Override
    public long getForkEvery() {
        return forkEvery;
    }

    @Override
    public TestTaskFilters getFilters() {
        return filters;
    }

    @Override
    public TestTaskForkOptions getForkOptions() {
        return forkOptions;
    }

    @Override
    public Stream<CandidateClassFile> getCandidateClassFiles() {
        return candidateClassFiles.stream();
    }

    public Stream<InputFileProperty> getInputFileProperties() {
        return inputFileProperties.stream();
    }

    public Stream<OutputFileProperty> getOutputFileProperties() {
        return outputFileProperties.stream();
    }
}
