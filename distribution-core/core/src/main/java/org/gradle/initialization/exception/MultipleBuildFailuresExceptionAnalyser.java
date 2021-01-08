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

package org.gradle.initialization.exception;

import org.gradle.execution.MultipleBuildFailures;

import java.util.ArrayList;
import java.util.List;

/**
 * An exception analyser that deals specifically with MultipleBuildFailures and transforms each component failure.
 */
public class MultipleBuildFailuresExceptionAnalyser implements ExceptionAnalyser {
    private final ExceptionCollector collector;

    public MultipleBuildFailuresExceptionAnalyser(ExceptionCollector collector) {
        this.collector = collector;
    }

    @Override
    public RuntimeException transform(Throwable exception) {
        List<Throwable> failures = new ArrayList<Throwable>();
        if (exception instanceof MultipleBuildFailures) {
            MultipleBuildFailures multipleBuildFailures = (MultipleBuildFailures) exception;
            for (Throwable cause : multipleBuildFailures.getCauses()) {
                collector.collectFailures(cause, failures);
            }
            if (failures.size() == 1 && failures.get(0) instanceof RuntimeException) {
                return (RuntimeException) failures.get(0);
            }
            multipleBuildFailures.replaceCauses(failures);
            return multipleBuildFailures;
        } else {
            collector.collectFailures(exception, failures);
            if (failures.size() == 1 && failures.get(0) instanceof RuntimeException) {
                return (RuntimeException) failures.get(0);
            }
            return new MultipleBuildFailures(failures);
        }
    }
}
