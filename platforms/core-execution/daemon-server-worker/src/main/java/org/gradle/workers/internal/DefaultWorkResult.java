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

package org.gradle.workers.internal;

import org.gradle.api.tasks.WorkResult;
import org.jspecify.annotations.Nullable;

import java.io.Serializable;

public class DefaultWorkResult implements WorkResult, Serializable {
    public static final DefaultWorkResult SUCCESS = new DefaultWorkResult(true, null);

    private final boolean didWork;
    private final Throwable exception;

    public DefaultWorkResult(boolean didWork, @Nullable Throwable exception) {
        this.didWork = didWork;
        this.exception = exception;
    }

    @Override
    public boolean getDidWork() {
        return didWork;
    }

    @Nullable
    public Throwable getException() {
        return exception;
    }

    public boolean isSuccess() {
        return exception == null;
    }
}
