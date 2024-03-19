/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.failure;

import com.google.common.collect.ImmutableList;
import org.gradle.api.GradleException;
import org.gradle.internal.problems.Failure;
import org.gradle.internal.problems.FailureFactory;
import org.gradle.internal.problems.StackTraceRelevance;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class DefaultFailureFactory implements FailureFactory {

    private final StackTraceClassifier stackTraceClassifier;

    public DefaultFailureFactory(StackTraceClassifier stackTraceClassifier) {
        this.stackTraceClassifier = stackTraceClassifier;
    }

    @Override
    public Failure create(Throwable failure, @Nullable Class<?> calledFrom) {
        ImmutableList<StackTraceElement> stackTrace = ImmutableList.copyOf(failure.getStackTrace());
        StackTraceClassifier calledFromClassifier = calledFrom == null ? null : new CalledFromDroppingStackTraceClassifier(calledFrom);
        List<StackTraceRelevance> relevances = classify(stackTrace, calledFromClassifier);
        return new DefaultFailure(failure,  stackTrace, relevances);
    }

    private List<StackTraceRelevance> classify(List<StackTraceElement> stackTrace, @Nullable StackTraceClassifier calledFromClassifier) {
        ArrayList<StackTraceRelevance> relevance = new ArrayList<StackTraceRelevance>(stackTrace.size());
        StackTraceClassifier cts = calledFromClassifier == null ? stackTraceClassifier : new CompositeStackTraceClassifier(ImmutableList.of(calledFromClassifier, stackTraceClassifier));
        for (StackTraceElement stackTraceElement : stackTrace) {
            StackTraceRelevance r = cts.classify(stackTraceElement);
            if (r == null) {
                throw new GradleException("Unable to classify stack trace element: " + stackTraceElement);
            }
            relevance.add(r);
        }

        return relevance;
    }

}
