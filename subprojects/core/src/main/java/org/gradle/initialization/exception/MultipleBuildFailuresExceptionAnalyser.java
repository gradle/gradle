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
import org.gradle.internal.exception.ExceptionAnalyser;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
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
    public RuntimeException transform(Throwable failure) {
        return transform(Collections.singletonList(failure));
    }

    @Nullable
    @Override
    public RuntimeException transform(List<Throwable> failures) {
        if (failures.isEmpty()) {
            return null;
        }

        List<Throwable> result = new ArrayList<>(failures.size());
        for (Throwable failure : failures) {
            if (failure instanceof MultipleBuildFailures) {
                for (Throwable cause : ((MultipleBuildFailures) failure).getCauses()) {
                    collector.collectFailures(cause, result);
                }
            } else {
                collector.collectFailures(failure, result);
            }
        }
        if (result.size() == 1 && result.get(0) instanceof RuntimeException) {
            return (RuntimeException) result.get(0);
        } else {
            return new MultipleBuildFailures(result);
        }
    }
}
