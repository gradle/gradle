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
import org.gradle.execution.MultipleBuildFailures;

import java.util.ArrayList;
import java.util.List;

/**
 * An exception analyser that deals specifically with MultipleBuildFailures and transforms each component failure.
 */
public class MultipleBuildFailuresExceptionAnalyser implements ExceptionAnalyser {
    private final ExceptionAnalyser delegate;

    public MultipleBuildFailuresExceptionAnalyser(ExceptionAnalyser delegate) {
        this.delegate = delegate;
    }

    public Throwable transform(Throwable exception) {
        // TODO:PARALLEL Make MultipleBuildFailures a generic concept (annotation? marker interface?)
        if (exception instanceof MultipleBuildFailures) {
            MultipleBuildFailures multipleBuildFailures = (MultipleBuildFailures) exception;
            List<Throwable> transformedCauses = new ArrayList<Throwable>(multipleBuildFailures.getCauses().size());
            for (Throwable cause : multipleBuildFailures.getCauses()) {
                transformedCauses.add(transform(cause));
            }
            multipleBuildFailures.replaceCauses(transformedCauses);
            return exception;
        }

        return delegate.transform(exception);
    }
}
