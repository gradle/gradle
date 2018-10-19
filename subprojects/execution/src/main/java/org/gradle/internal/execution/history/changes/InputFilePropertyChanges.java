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

package org.gradle.internal.execution.history.changes;

import org.gradle.api.Describable;
import org.gradle.internal.execution.history.BeforeExecutionState;
import org.gradle.internal.execution.history.ExecutionState;
import org.gradle.internal.execution.history.PreviousExecutionState;
import org.gradle.internal.fingerprint.FileCollectionFingerprint;

import java.util.SortedMap;

public class InputFilePropertyChanges extends AbstractPropertyChanges<FileCollectionFingerprint> {

    public InputFilePropertyChanges(PreviousExecutionState previous, BeforeExecutionState current, Describable executable) {
        super(previous, current, "Input file", executable);
    }

    @Override
    protected SortedMap<String, ? extends FileCollectionFingerprint> getProperties(ExecutionState execution) {
        return execution.getInputFileProperties();
    }
}
