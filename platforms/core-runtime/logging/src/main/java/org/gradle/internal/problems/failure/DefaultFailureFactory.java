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
import org.gradle.api.problems.internal.InternalProblem;
import org.gradle.api.problems.internal.ProblemLocator;
import org.gradle.internal.InternalTransformer;
import org.gradle.internal.exceptions.MultiCauseException;
import org.gradle.util.internal.CollectionUtils;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

public class DefaultFailureFactory implements FailureFactory {

    public static DefaultFailureFactory withDefaultClassifier(ProblemLocator problemLocator) {
        return new DefaultFailureFactory(new CompositeStackTraceClassifier(
            new InternalStackTraceClassifier(),
            StackTraceClassifier.USER_CODE
        ), problemLocator);
    }

    public static DefaultFailureFactory withDefaultClassifier() {
        return withDefaultClassifier(ProblemLocator.EMPTY_LOCATOR);
    }

    private final StackTraceClassifier stackTraceClassifier;
    private final ProblemLocator problemLocator;

    public DefaultFailureFactory(StackTraceClassifier stackTraceClassifier, ProblemLocator problemLocator) {
        this.stackTraceClassifier = stackTraceClassifier;
        this.problemLocator = problemLocator;
    }

    @Override
    public Failure create(Throwable failure) {
        return new Job(stackTraceClassifier, problemLocator)
            .convert(failure);
    }

    private static final class Job {

        private final StackTraceClassifier stackTraceClassifier;
        private final ProblemLocator problemLocator;

        private final Set<Throwable> seen;

        private final InternalTransformer<Failure, Throwable> recursiveConverter = new InternalTransformer<Failure, Throwable>() {
            @Override
            public Failure transform(Throwable throwable) {
                return convertRecursively(throwable);
            }
        };

        private Job(StackTraceClassifier stackTraceClassifier, ProblemLocator problemLocator) {
            this(stackTraceClassifier, problemLocator, null);
        }

        private Job(StackTraceClassifier stackTraceClassifier, ProblemLocator problemLocator, @Nullable Set<Throwable> parentSeen) {
            this.stackTraceClassifier = stackTraceClassifier;
            this.problemLocator = problemLocator;
            this.seen = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());
            if (parentSeen != null) {
                this.seen.addAll(parentSeen);
            }
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
            SuppressedAndCauses suppressedAndCauses = getSuppressedAndCauses(failure);
            List<Failure> suppressed = convertSuppressed(suppressedAndCauses);
            List<Failure> causes = convertCauses(suppressedAndCauses);
            List<InternalProblem> problems = ImmutableList.copyOf(problemLocator.findAll(failure));
            return new DefaultFailure(failure, stackTrace, relevances, suppressed, causes, problems);
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

        private static SuppressedAndCauses getSuppressedAndCauses(Throwable failure) {
            Throwable[] suppressed = getSuppressed(failure);
            List<Throwable> causes = getCauses(failure);
            return new SuppressedAndCauses(suppressed, causes);
        }

        @Nullable
        @SuppressWarnings("Since15")
        private static Throwable[] getSuppressed(Throwable parent) {
            // Short-circuit if suppressed exceptions are not supported by the current JVM
            if (!JavaVersion.current().isJava7Compatible()) {
                return null;
            }

            return parent.getSuppressed();
        }

        private static List<Throwable> getCauses(Throwable parent) {
            ImmutableList.Builder<Throwable> causes = new ImmutableList.Builder<Throwable>();
            if (parent instanceof MultiCauseException) {
                causes.addAll(((MultiCauseException) parent).getCauses());
            } else if (parent.getCause() != null) {
                causes.add(parent.getCause());
            }

            return causes.build();
        }

        private List<Failure> convertSuppressed(SuppressedAndCauses suppressedAndCauses) {
            Throwable[] suppressed = suppressedAndCauses.suppressed;
            if (suppressed == null) {
                return ImmutableList.of();
            }

            return CollectionUtils.collect(
                suppressed,
                determineRecursiveConverter(suppressedAndCauses.childCount())
            );

        }

        private List<Failure> convertCauses(SuppressedAndCauses suppressedAndCauses) {
            List<Throwable> causes = suppressedAndCauses.causes;
            if (causes.isEmpty()) {
                return ImmutableList.of();
            }
            return CollectionUtils.collect(
                causes,
                determineRecursiveConverter(suppressedAndCauses.childCount())
            );
        }

        private InternalTransformer<Failure, Throwable> determineRecursiveConverter(int size) {
            if (size <= 1) {
                return recursiveConverter;
            } else {
                // when we branch, we need to have separate seen sets on each branch, since we there cannot be cycles between branches
                return multiChildTransformer();
            }
        }

        private InternalTransformer<Failure, Throwable> multiChildTransformer() {
            return new InternalTransformer<Failure, Throwable>() {
                @Override
                public Failure transform(Throwable throwable) {
                    return new Job(stackTraceClassifier, problemLocator, seen).convert(throwable);
                }
            };
        }

        private static class SuppressedAndCauses {
            private final Throwable[] suppressed;
            private final List<Throwable> causes;

            public SuppressedAndCauses(
                @Nullable Throwable[] suppressed,
                List<Throwable> causes
            ) {
                this.causes = causes;
                this.suppressed = suppressed;
            }

            public int childCount() {
                return causes.size() + (suppressed != null ? suppressed.length : 0);
            }
        }
    }
}
