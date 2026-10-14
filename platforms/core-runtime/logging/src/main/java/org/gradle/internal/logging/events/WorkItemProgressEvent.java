/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.logging.events;

import org.gradle.api.logging.LogLevel;

/**
 * Reports the submission or completion of work items, each of which counts as a unit of build progress.
 */
public class WorkItemProgressEvent extends OutputEvent {

    public enum Kind {
        SUBMITTED, SUCCEEDED, FAILED
    }

    private final Kind kind;
    private final int count;

    public WorkItemProgressEvent(Kind kind, int count) {
        this.kind = kind;
        this.count = count;
    }

    public Kind getKind() {
        return kind;
    }

    public int getCount() {
        return count;
    }

    @Override
    public String toString() {
        return WorkItemProgressEvent.class.getSimpleName() + " " + kind + " " + count;
    }

    @Override
    public LogLevel getLogLevel() {
        return LogLevel.LIFECYCLE;
    }
}
