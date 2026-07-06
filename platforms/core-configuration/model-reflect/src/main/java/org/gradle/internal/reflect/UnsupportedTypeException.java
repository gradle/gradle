/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.internal.reflect;

import org.gradle.internal.exceptions.Contextual;
import org.gradle.internal.exceptions.ResolutionProvider;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import java.util.Collections;
import java.util.List;

/**
 * Thrown when a type is not supported in a given context — for example, when a
 * property value type or Kotlin delegate result type cannot survive a
 * configuration cache serialization round-trip.
 */
@Contextual
public final class UnsupportedTypeException extends RuntimeException implements ResolutionProvider {
    @Nullable
    private final String msgSummary;
    @Nullable
    private final String msgDetails;
    private final List<String> resolutions;

    public UnsupportedTypeException(String msgSummary, String msgDetails, List<String> resolutions) {
        super(msgSummary + " " + msgDetails);
        this.msgSummary = msgSummary;
        this.msgDetails = msgDetails;
        this.resolutions = Collections.unmodifiableList(resolutions);
    }

    public UnsupportedTypeException(String message, Throwable cause) {
        super(message, cause);
        this.msgSummary = null;
        this.msgDetails = null;
        this.resolutions = Collections.emptyList();
    }

    @NonNull
    @Override
    public List<String> getResolutions() {
        return resolutions;
    }

    public String getDetailsForProblem() {
        if (msgSummary != null && msgDetails != null) {
            return "\n  " + msgSummary + "\n  " + msgDetails;
        } else {
            return getMessage();
        }
    }
}
