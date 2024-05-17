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
import org.gradle.internal.scan.UsedByScanPlugin;

import javax.annotation.Nullable;

/**
 * A {@code PlaceholderException} is used when an exception cannot be serialized or deserialized.
 */
@UsedByScanPlugin
public class PlaceholderException extends RuntimeException implements PlaceholderExceptionSupport {
    private final String exceptionClassName;
    private final Throwable getMessageException;
    private final String toString;
    private final Throwable toStringRuntimeEx;

    @UsedByScanPlugin("test-distribution")
    public PlaceholderException(String exceptionClassName, @Nullable String message, @Nullable Throwable getMessageException, @Nullable String toString,
                                @Nullable Throwable toStringException, @Nullable Throwable cause) {
        super(message, cause);
        this.exceptionClassName = exceptionClassName;
        this.getMessageException = getMessageException;
        this.toString = toString;
        this.toStringRuntimeEx = toStringException;
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

    public String toString() {
        if (toStringRuntimeEx != null) {
            throw UncheckedException.throwAsUncheckedException(toStringRuntimeEx);
        }
        return toString;
    }
}
