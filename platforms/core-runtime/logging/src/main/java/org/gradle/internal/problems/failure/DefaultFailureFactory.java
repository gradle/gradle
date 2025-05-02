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

package org.gradle.internal.problems.failure;

import com.google.common.collect.ImmutableList;
import org.gradle.api.GradleException;
import org.gradle.api.JavaVersion;
import org.gradle.internal.InternalTransformer;
import org.gradle.internal.exceptions.MultiCauseException;
import org.gradle.util.internal.CollectionUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

public class DefaultFailureFactory implements FailureFactory {

    public static DefaultFailureFactory withDefaultClassifier() {
        return new DefaultFailureFactory(new CompositeStackTraceClassifier(
            new InternalStackTraceClassifier(),
            StackTraceClassifier.USER_CODE
        ));
    }

    private final StackTraceClassifier stackTraceClassifier;

    public DefaultFailureFactory(StackTraceClassifier stackTraceClassifier) {
        this.stackTraceClassifier = stackTraceClassifier;
    }

    @Override
    public Failure create(Throwable failure) {
        return new Job(stackTraceClassifier)
            .convert(failure);
    }

    private static final class Job {

        private final StackTraceClassifier stackTraceClassifier;

        private final Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());

        private final InternalTransformer<Failure, Throwable> recursiveConverter = new InternalTransformer<Failure, Throwable>() {
            @Override
            public Failure transform(Throwable throwable) {
                return convertRecursively(throwable);
            }
        };

        private Job(StackTraceClassifier stackTraceClassifier) {
            this.stackTraceClassifier = stackTraceClassifier;
        }

        public Failure convert(Throwable failure) {
            return convertRecursively(failure);
        }

        private Failure convertRecursively(Throwable failure) {
            if (!seen.add(failure)) {
                Throwable replacement = new Throwable("[CIRCULAR REFERENCE: " + failure + "]");
                replacement.setStackTrace(failure.getStackTrace());
                failure = replacement;
            }

            ImmutableList<StackTraceElement> stackTrace = ImmutableList.copyOf(failure.getStackTrace());
            List<StackTraceRelevance> relevances = classify(stackTrace, stackTraceClassifier);
            List<Failure> suppressed = convertSuppressed(failure);
            List<Failure> causes = convertCauses(failure);
            return new DefaultFailure(failure, stackTrace, relevances, suppressed, causes);
        }

        @SuppressWarnings("Since15")
        private List<Failure> convertSuppressed(Throwable parent) {
            // Short-circuit if suppressed exceptions are not supported by the current JVM
            if (!JavaVersion.current().isJava7Compatible()) {
                return ImmutableList.of();
            }

            return CollectionUtils.collect(parent.getSuppressed(), recursiveConverter);
        }

        private List<Failure> convertCauses(Throwable parent) {
            ImmutableList.Builder<Throwable> causes = new ImmutableList.Builder<Throwable>();
            if (parent instanceof MultiCauseException) {
                causes.addAll(((MultiCauseException) parent).getCauses());
            } else if (parent.getCause() != null) {
                causes.add(parent.getCause());
            }

            return CollectionUtils.collect(causes.build(), recursiveConverter);
        }

        private static List<StackTraceRelevance> classify(List<StackTraceElement> stackTrace, StackTraceClassifier classifier) {
            ArrayList<StackTraceRelevance> relevance = new ArrayList<StackTraceRelevance>(stackTrace.size());
            for (StackTraceElement stackTraceElement : stackTrace) {
                StackTraceRelevance r = classifier.classify(stackTraceElement);
                if (r == null) {
                    throw new GradleException("Unable to classify stack trace element: " + stackTraceElement);
                }
                relevance.add(r);
            }

            return relevance;
        }
    }
}
