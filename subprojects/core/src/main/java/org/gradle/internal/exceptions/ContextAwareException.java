/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.exceptions;

import org.gradle.api.GradleException;

/**
 * Marker for exception types that contains {@link Contextual} exceptions as causes.
 *
 * On most build failures (when the build doesn't fail too early or too late), Gradle's exception reporting
 * will have the outermost exception be a contextual exception. In that case, it will format the exception in a
 * tree, using each contextual exception as an indented line, and then showing the first non-contextual exception
 * after the last contextual exception.
 *
 * @see org.gradle.internal.buildevents.ContextAwareExceptionHandler
 */
public class ContextAwareException extends GradleException {

    public ContextAwareException(Throwable t) {
        initCause(t);
    }

}
