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

package org.gradle.tooling.events.task.internal;

import org.gradle.tooling.events.task.TaskExecutionResult;
import org.gradle.tooling.model.internal.Exceptions;

import javax.annotation.Nullable;
import java.util.List;

public abstract class TaskExecutionDetails {

    private static final TaskExecutionDetails UNSUPPORTED = new TaskExecutionDetails() {
        @Override
        public boolean isIncremental() {
            throw Exceptions.unsupportedMethod(TaskExecutionResult.class.getSimpleName() + ".isIncremental()");
        }

        @Override
        public List<String> getExecutionReasons() {
            throw Exceptions.unsupportedMethod(TaskExecutionResult.class.getSimpleName() + ".getExecutionReasons()");
        }
    };

    abstract boolean isIncremental();

    @Nullable
    abstract List<String> getExecutionReasons();

    public static TaskExecutionDetails of(final boolean incremental, final List<String> executionReasons) {
        return new TaskExecutionDetails() {
            @Override
            public boolean isIncremental() {
                return incremental;
            }

            @Override
            public List<String> getExecutionReasons() {
                return executionReasons;
            }
        };
    }

    public static TaskExecutionDetails unsupported() {
        return UNSUPPORTED;
    }

}
