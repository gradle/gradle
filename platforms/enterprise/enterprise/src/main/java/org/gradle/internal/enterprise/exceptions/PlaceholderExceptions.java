/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.internal.enterprise.exceptions;

import org.gradle.internal.scan.UsedByScanPlugin;
import org.gradle.internal.serialize.PlaceholderAssertionError;
import org.gradle.internal.serialize.PlaceholderException;
import org.gradle.internal.serialize.PlaceholderExceptionSupport;
import org.jspecify.annotations.Nullable;

@UsedByScanPlugin
public class PlaceholderExceptions {

    @UsedByScanPlugin
    public static String getExceptionClassName(Throwable t) {
        if (t instanceof PlaceholderExceptionSupport) {
            return ((PlaceholderExceptionSupport) t).getExceptionClassName();
        } else {
            return t.getClass().getName();
        }
    }

    @UsedByScanPlugin
    public static Throwable createException(
        String originalClassName,
        @Nullable String message,
        @Nullable Throwable getMessageException,
        @Nullable String toString,
        @Nullable Throwable toStringException,
        @Nullable Throwable cause
    ) {
        return new PlaceholderException(originalClassName, message, getMessageException, toString, toStringException, cause);
    }

    @UsedByScanPlugin
    public static Throwable createAssertionError(
        String originalClassName,
        @Nullable String message,
        @Nullable Throwable getMessageException,
        @Nullable String toString,
        @Nullable Throwable toStringException,
        @Nullable Throwable cause
    ) {
        return new PlaceholderAssertionError(originalClassName, message, getMessageException, toString, toStringException, cause);
    }
}
