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

package org.gradle.internal.serialize;

import org.gradle.internal.UncheckedException;

import javax.annotation.Nullable;

/**
 * A {@code PlaceholderException} is used when an assertion error cannot be serialized or deserialized.
 */
public class PlaceholderAssertionError extends AssertionError implements PlaceholderExceptionSupport {
    private final String exceptionClassName;
    private final Throwable getMessageException;
    private final String toString;
    private final Throwable toStringRuntimeEx;

    public PlaceholderAssertionError(String exceptionClassName,
                                     @Nullable String message,
                                     @Nullable Throwable getMessageException,
                                     @Nullable String toString,
                                     @Nullable Throwable toStringException,
                                     @Nullable Throwable cause) {
        super(message);
        this.exceptionClassName = exceptionClassName;
        this.getMessageException = getMessageException;
        this.toString = toString;
        this.toStringRuntimeEx = toStringException;
        initCause(cause);
    }

    @Override
    public String getExceptionClassName() {
        return exceptionClassName;
    }

    @Override
    public String getMessage() {
        if (getMessageException != null) {
            throw UncheckedException.throwAsUncheckedException(getMessageException);
        }
        return super.getMessage();
    }

    @Override
    public String toString() {
        if (toStringRuntimeEx != null) {
            throw UncheckedException.throwAsUncheckedException(toStringRuntimeEx);
        }
        return toString;
    }
}
