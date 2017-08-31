/*
 * Copyright 2017 the original author or authors.
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

import com.google.common.collect.AbstractIterator;
import org.gradle.api.GradleException;
import org.gradle.api.Task;

import java.util.Iterator;

public class ErrorHandlingTaskStateChanges implements TaskStateChanges {
    private final Task task;
    private final TaskStateChanges delegate;

    ErrorHandlingTaskStateChanges(Task task, TaskStateChanges delegate) {
        this.task = task;
        this.delegate = delegate;
    }

    @Override
    public Iterator<TaskStateChange> iterator() {
        final Iterator<TaskStateChange> iterator;
        try {
            iterator = delegate.iterator();
        } catch (Exception ex) {
            throw new GradleException(String.format("Cannot determine task state changes for %s", task), ex);
        }
        return new AbstractIterator<TaskStateChange>() {
            @Override
            protected TaskStateChange computeNext() {
                try {
                    if (iterator.hasNext()) {
                        return iterator.next();
                    }
                } catch (Exception ex) {
                    throw new GradleException(String.format("Cannot determine task state changes for %s", task), ex);
                }
                return endOfData();
            }
        };
    }
}
