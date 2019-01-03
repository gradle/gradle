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

package org.gradle.internal.execution;

import com.google.common.collect.Iterables;
import org.gradle.internal.execution.history.OutputFilesRepository;
import org.gradle.internal.snapshot.FileSystemSnapshot;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class TestOutputFilesRepository implements OutputFilesRepository {

    private List<FileSystemSnapshot> outputFileFingerprints = new ArrayList<>();

    @Override
    public boolean isGeneratedByGradle(File file) {
        return true;
    }

    @Override
    public void recordOutputs(Iterable<? extends FileSystemSnapshot> outputFileFingerprints) {
        Iterables.addAll(this.outputFileFingerprints, outputFileFingerprints);
    }

    public List<FileSystemSnapshot> getOutputFileFingerprints() {
        return outputFileFingerprints;
    }
}
