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

package org.gradle.api.internal.changedetection.state.isolation;

import org.gradle.internal.exceptions.Contextual;

/**
 * Represents a problem while attempting to isolate an instance.
 */
@Contextual
public class IsolationException extends RuntimeException {
    private static final String MSG_FORMAT="Could not isolate value: [%s] of type: [%s]";

    public IsolationException(Object value) {
        super(String.format(MSG_FORMAT, value, value.getClass()));
    }

    public IsolationException(Object value, Throwable cause) {
        super(String.format(MSG_FORMAT, value, value.getClass()), cause);
    }
}
