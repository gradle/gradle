/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.initialization;

import org.gradle.api.internal.ExceptionAnalyser;
import org.gradle.api.internal.LocationAwareException;
import org.gradle.api.internal.MultiCauseException;
import org.gradle.execution.CompositeTaskExecutionException;

import java.util.ArrayList;
import java.util.List;

public class MultiCauseExceptionAnalyser implements ExceptionAnalyser {
    private final ExceptionAnalyser delegate;

    public MultiCauseExceptionAnalyser(ExceptionAnalyser delegate) {
        this.delegate = delegate;
    }

    public Throwable transform(Throwable exception) {
        // TODO:PARALLEL Remove the special case: this should apply to all multi cause exceptions
        if (exception instanceof CompositeTaskExecutionException) {
            MultiCauseException multiCauseException = (MultiCauseException) exception;
            List<Throwable> transformedCauses = new ArrayList<Throwable>(multiCauseException.getCauses().size());
            for (Throwable cause : multiCauseException.getCauses()) {
                transformedCauses.add(transform(cause));
            }
            multiCauseException.initCauses(transformedCauses);

            return new LocationAwareException(exception, exception, null, null);
        }

        return delegate.transform(exception);
    }
}
