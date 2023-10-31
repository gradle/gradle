/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.internal.execution.steps;

import com.google.common.collect.ImmutableSortedMap;
import org.gradle.internal.execution.Executable;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;

public class WorkDeterminedContext extends WorkspaceContext {
    private final ImmutableSortedMap<String, CurrentFileCollectionFingerprint> inputFileProperties;
    private final Executable executable;

    public WorkDeterminedContext(WorkspaceContext parent, Executable  executable) {
        this(parent, parent.getInputFileProperties(), executable);
    }

    public WorkDeterminedContext(WorkspaceContext parent, ImmutableSortedMap<String, CurrentFileCollectionFingerprint> inputFileProperties, Executable  executable) {
        super(parent);
        this.inputFileProperties = inputFileProperties;
        this.executable = executable;
    }

    protected WorkDeterminedContext(WorkDeterminedContext parent) {
        this(parent, parent.getInputFileProperties(), parent.getExecutable());
    }

    @Override
    public ImmutableSortedMap<String, CurrentFileCollectionFingerprint> getInputFileProperties() {
        return inputFileProperties;
    }

    public Executable  getExecutable() {
        return executable;
    }
}
