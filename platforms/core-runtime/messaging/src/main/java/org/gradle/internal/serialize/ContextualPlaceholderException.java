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

package org.gradle.internal.serialize;

import org.gradle.internal.exceptions.Contextual;

import javax.annotation.Nullable;

@Contextual
public class ContextualPlaceholderException extends PlaceholderException {
    public ContextualPlaceholderException(String exceptionClassName, @Nullable String message, @Nullable Throwable getMessageException, @Nullable String toString, @Nullable Throwable toStringException, @Nullable Throwable cause) {
        super(exceptionClassName, message, getMessageException, toString, toStringException, cause);
    }
}
