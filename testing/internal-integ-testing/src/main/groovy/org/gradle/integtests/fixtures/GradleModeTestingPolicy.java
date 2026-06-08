/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.integtests.fixtures;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.lang.annotation.Annotation;

/**
 * Decides whether a test should run, be skipped, or expect a failure under the active
 * {@link GradleModeTesting}, so that Spock-based and JUnit-based tests gate identically.
 *
 * @param <A> the annotation type this policy decides for
 */
@NullMarked
public abstract class GradleModeTestingPolicy<A extends Annotation> {

    public enum Verdict {
        RUN,
        SKIP,
        EXPECT_FAILURE
    }

    private final GradleModeTesting mode;

    protected GradleModeTestingPolicy(GradleModeTesting mode) {
        this.mode = mode;
    }

    /**
     * Bottom-spec names declared on the annotation. Empty means "all".
     */
    protected abstract String[] bottomSpecs(A annotation);

    /**
     * Iteration matchers declared on the annotation. Empty means "all iterations".
     */
    public abstract String[] iterationMatchers(A annotation);

    /**
     * Reason for a {@link Verdict#SKIP} decision. Empty when no reason is associated.
     * <p>
     * Callers must only invoke this after {@link #decide} returned {@link Verdict#SKIP}.
     */
    public abstract String skipReason(A annotation);

    /**
     * What verdict to produce when the gated Gradle mode is active and the annotation
     * targets the test: either {@link Verdict#SKIP} (for {@code @UnsupportedWith*}, and
     * for {@code @ToBeFixedFor*} when {@code skip} is set) or {@link Verdict#EXPECT_FAILURE}
     * (for {@code @ToBeFixedFor*} otherwise).
     */
    protected abstract Verdict verdictWhenMatched(A annotation);

    /**
     * Decide what should happen given the {@code annotation}, the {@code bottomSpecName}
     * (simple Spock spec name, or fully-qualified JUnit class name), and the
     * {@code iterationName} (may be {@code null} at spec-level visits, before any
     * iteration is known).
     */
    public final Verdict decide(A annotation, String bottomSpecName, @Nullable String iterationName) {
        if (!mode.isActive()) {
            return Verdict.RUN;
        }
        if (!matchesAll(bottomSpecs(annotation), iterationMatchers(annotation), bottomSpecName, iterationName)) {
            return Verdict.RUN;
        }
        return verdictWhenMatched(annotation);
    }

    /**
     * Whether a spec-level {@link Verdict#RUN} verdict could still flip per iteration —
     * i.e. the mode is active, the bottom spec matches, and {@code iterationMatchers} is non-empty.
     */
    public final boolean requiresPerIterationCheck(A annotation, String bottomSpecName) {
        return mode.isActive()
            && matchesBottomSpec(bottomSpecs(annotation), bottomSpecName)
            && !isAllIterations(iterationMatchers(annotation));
    }

    /**
     * Display name of the gated Gradle mode, e.g. {@code "Configuration Cache"}.
     * Surfaced in skip messages and expected-failure logs.
     */
    public final String gradleMode() {
        return mode.displayName();
    }

    // --- Shared matching utilities (single source of truth) ---

    public static boolean isAllIterations(String[] matchers) {
        return matchers.length == 0;
    }

    public static boolean iterationMatches(String[] matchers, String iterationName) {
        if (isAllIterations(matchers)) {
            return true;
        }
        for (String matcher : matchers) {
            if (iterationName.matches(matcher)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether {@code candidate} matches any entry: equals it OR ends with {@code ".$entry"}.
     * Empty array means "all". Covers Spock's simple name and JUnit's fully-qualified name.
     */
    public static boolean matchesBottomSpec(String[] bottomSpecs, String candidate) {
        if (bottomSpecs.length == 0) {
            return true;
        }
        for (String entry : bottomSpecs) {
            if (candidate.equals(entry) || candidate.endsWith("." + entry)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether the test identified by {@code bottomSpecName}/{@code iterationName} matches both filters.
     * At a spec-level visit ({@code iterationName} is null), returns {@code false} when
     * {@code iterationMatchers} is non-empty so callers know to defer to per-iteration evaluation.
     */
    public static boolean matchesAll(
        String[] bottomSpecs,
        String[] iterationMatchers,
        String bottomSpecName,
        @Nullable String iterationName
    ) {
        if (!matchesBottomSpec(bottomSpecs, bottomSpecName)) {
            return false;
        }
        if (iterationName == null) {
            return isAllIterations(iterationMatchers);
        }
        return iterationMatches(iterationMatchers, iterationName);
    }

    // --- Concrete policies ---

    /**
     * Policy for {@link ToBeFixedForConfigurationCache @ToBeFixedForConfigurationCache}.
     */
    public static final class ToBeFixedForCC extends GradleModeTestingPolicy<ToBeFixedForConfigurationCache> {

        public ToBeFixedForCC() {
            super(GradleModeTesting.CONFIGURATION_CACHE);
        }

        @Override
        protected String[] bottomSpecs(ToBeFixedForConfigurationCache ann) {
            return ann.bottomSpecs();
        }

        @Override
        public String[] iterationMatchers(ToBeFixedForConfigurationCache ann) {
            return ann.iterationMatchers();
        }

        @Override
        protected Verdict verdictWhenMatched(ToBeFixedForConfigurationCache ann) {
            return ann.skip() == ToBeFixedForConfigurationCache.Skip.DO_NOT_SKIP
                ? Verdict.EXPECT_FAILURE
                : Verdict.SKIP;
        }

        @Override
        public String skipReason(ToBeFixedForConfigurationCache ann) {
            if (ann.skip() == ToBeFixedForConfigurationCache.Skip.DO_NOT_SKIP) {
                throw new IllegalStateException("skipReason called for a non-skipping annotation");
            }
            return ann.because().isEmpty() ? ann.skip().name() : ann.because();
        }
    }

    /**
     * Policy for {@link ToBeFixedForIsolatedProjects @ToBeFixedForIsolatedProjects}.
     */
    public static final class ToBeFixedForIP extends GradleModeTestingPolicy<ToBeFixedForIsolatedProjects> {

        public ToBeFixedForIP() {
            super(GradleModeTesting.ISOLATED_PROJECTS);
        }

        @Override
        protected String[] bottomSpecs(ToBeFixedForIsolatedProjects ann) {
            return ann.bottomSpecs();
        }

        @Override
        public String[] iterationMatchers(ToBeFixedForIsolatedProjects ann) {
            return ann.iterationMatchers();
        }

        @Override
        protected Verdict verdictWhenMatched(ToBeFixedForIsolatedProjects ann) {
            return ann.skip() == ToBeFixedForIsolatedProjects.Skip.DO_NOT_SKIP
                ? Verdict.EXPECT_FAILURE
                : Verdict.SKIP;
        }

        @Override
        public String skipReason(ToBeFixedForIsolatedProjects ann) {
            if (ann.skip() == ToBeFixedForIsolatedProjects.Skip.DO_NOT_SKIP) {
                throw new IllegalStateException("skipReason called for a non-skipping annotation");
            }
            return ann.because().isEmpty() ? ann.skip().getReason() : ann.because();
        }
    }

    /**
     * Policy for {@link UnsupportedWithConfigurationCache @UnsupportedWithConfigurationCache}.
     */
    public static final class UnsupportedWithCC extends GradleModeTestingPolicy<UnsupportedWithConfigurationCache> {

        public UnsupportedWithCC() {
            super(GradleModeTesting.CONFIGURATION_CACHE);
        }

        @Override
        protected String[] bottomSpecs(UnsupportedWithConfigurationCache ann) {
            return ann.bottomSpecs();
        }

        @Override
        public String[] iterationMatchers(UnsupportedWithConfigurationCache ann) {
            return ann.iterationMatchers();
        }

        @Override
        protected Verdict verdictWhenMatched(UnsupportedWithConfigurationCache ann) {
            return Verdict.SKIP;
        }

        @Override
        public String skipReason(UnsupportedWithConfigurationCache ann) {
            return ann.because();
        }
    }

    /**
     * Policy for {@link UnsupportedWithIsolatedProjects @UnsupportedWithIsolatedProjects}.
     */
    public static final class UnsupportedWithIP extends GradleModeTestingPolicy<UnsupportedWithIsolatedProjects> {

        public UnsupportedWithIP() {
            super(GradleModeTesting.ISOLATED_PROJECTS);
        }

        @Override
        protected String[] bottomSpecs(UnsupportedWithIsolatedProjects ann) {
            return ann.bottomSpecs();
        }

        @Override
        public String[] iterationMatchers(UnsupportedWithIsolatedProjects ann) {
            return ann.iterationMatchers();
        }

        @Override
        protected Verdict verdictWhenMatched(UnsupportedWithIsolatedProjects ann) {
            return Verdict.SKIP;
        }

        @Override
        public String skipReason(UnsupportedWithIsolatedProjects ann) {
            return ann.because();
        }
    }
}
