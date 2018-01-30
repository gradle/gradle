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

package org.gradle.api.internal.changedetection.rules;

import com.google.common.collect.ImmutableList;
import org.gradle.api.NonNullApi;

import java.util.Iterator;

@NonNullApi
public class NoHistoryTaskUpToDateState implements TaskUpToDateState {

    public static final NoHistoryTaskUpToDateState INSTANCE = new NoHistoryTaskUpToDateState();

    private final TaskStateChanges noHistoryTaskStateChanges;

    private NoHistoryTaskUpToDateState() {
        final ImmutableList<TaskStateChange> changes = ImmutableList.<TaskStateChange>of(new DescriptiveChange("No history is available."));
        noHistoryTaskStateChanges = new TaskStateChanges() {
            @Override
            public Iterator<TaskStateChange> iterator() {
                return changes.iterator();
            }
        };
    }

    @Override
    public TaskStateChanges getInputFilesChanges() {
        throw new UnsupportedOperationException("Input file changes can only be queried when task history is available.");
    }

    @Override
    public boolean hasAnyOutputFileChanges() {
        return true;
    }

    @Override
    public TaskStateChanges getAllTaskChanges() {
        return noHistoryTaskStateChanges;
    }

    @Override
    public TaskStateChanges getRebuildChanges() {
        return noHistoryTaskStateChanges;
    }
}
