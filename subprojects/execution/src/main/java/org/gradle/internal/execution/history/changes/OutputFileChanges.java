/*
 * Copyright 2016 the original author or authors.
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

import com.google.common.collect.ImmutableSortedMap;
import org.gradle.internal.change.ChangeDetectorVisitor;
import org.gradle.internal.change.ChangeVisitor;
import org.gradle.internal.execution.history.BeforeExecutionState;
import org.gradle.internal.execution.history.ExecutionState;
import org.gradle.internal.execution.history.PreviousExecutionState;
import org.gradle.internal.fingerprint.FileCollectionFingerprint;

public class OutputFileChanges extends AbstractFingerprintBasedChanges {

    public OutputFileChanges(PreviousExecutionState previous, BeforeExecutionState current) {
        super(previous, current, "Output");
    }

    @Override
    protected ImmutableSortedMap<String, ? extends FileCollectionFingerprint> getFingerprints(ExecutionState execution) {
        return execution.getOutputFileProperties();
    }

    public boolean hasAnyChanges() {
        ChangeDetectorVisitor changeDetectorVisitor = new ChangeDetectorVisitor();
        accept(changeDetectorVisitor, true);
        return changeDetectorVisitor.hasAnyChanges();
    }

    @Override
    public boolean accept(ChangeVisitor visitor) {
        return accept(visitor, false);
    }
}
