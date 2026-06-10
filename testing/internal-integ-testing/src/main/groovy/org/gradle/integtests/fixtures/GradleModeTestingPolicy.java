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
import java.util.function.Function;

/**
 * Decides whether a test should run, be skipped, or expect a failure under the active
 * {@link GradleModeTesting}, so that Spock-based and JUnit-based tests gate identically.
 *
 * @param <A> the annotation type this policy decides for
 */
@NullMarked
public final class GradleModeTestingPolicy<A extends Annotation> {

    public enum Verdict {
        RUN,
        SKIP,
        EXPECT_FAILURE
    }

    public static final GradleModeTestingPolicy<ToBeFixedForConfigurationCache> TO_BE_FIXED_FOR_CC = new GradleModeTestingPolicy<>(
        GradleModeTesting.CONFIGURATION_CACHE,
        ToBeFixedForConfigurationCache::bottomSpecs,
        ToBeFixedForConfigurationCache::iterationMatchers,
        ann -> ann.skip() == ToBeFixedForConfigurationCache.Skip.DO_NOT_SKIP ? Verdict.EXPECT_FAILURE : Verdict.SKIP,
        ann -> ann.because().isEmpty() ? ann.skip().name() : ann.because()
    );

    public static final GradleModeTestingPolicy<ToBeFixedForIsolatedProjects> TO_BE_FIXED_FOR_IP = new GradleModeTestingPolicy<>(
        GradleModeTesting.ISOLATED_PROJECTS,
        ToBeFixedForIsolatedProjects::bottomSpecs,
        ToBeFixedForIsolatedProjects::iterationMatchers,
        ann -> ann.skip() == ToBeFixedForIsolatedProjects.Skip.DO_NOT_SKIP ? Verdict.EXPECT_FAILURE : Verdict.SKIP,
        ann -> ann.because().isEmpty() ? ann.skip().getReason() : ann.because()
    );

    public static final GradleModeTestingPolicy<UnsupportedWithConfigurationCache> UNSUPPORTED_WITH_CC = new GradleModeTestingPolicy<>(
        GradleModeTesting.CONFIGURATION_CACHE,
        UnsupportedWithConfigurationCache::bottomSpecs,
        UnsupportedWithConfigurationCache::iterationMatchers,
        ann -> Verdict.SKIP,
        UnsupportedWithConfigurationCache::because
    );

    public static final GradleModeTestingPolicy<UnsupportedWithIsolatedProjects> UNSUPPORTED_WITH_IP = new GradleModeTestingPolicy<>(
        GradleModeTesting.ISOLATED_PROJECTS,
        UnsupportedWithIsolatedProjects::bottomSpecs,
        UnsupportedWithIsolatedProjects::iterationMatchers,
        ann -> Verdict.SKIP,
        UnsupportedWithIsolatedProjects::because
    );

    private final GradleModeTesting mode;
    private final Function<A, String[]> bottomSpecs;
    private final Function<A, String[]> iterationMatchers;
    private final Function<A, Verdict> verdictWhenMatched;
    private final Function<A, String> skipReason;

    private GradleModeTestingPolicy(
        GradleModeTesting mode,
        Function<A, String[]> bottomSpecs,
        Function<A, String[]> iterationMatchers,
        Function<A, Verdict> verdictWhenMatched,
        Function<A, String> skipReason
    ) {
        this.mode = mode;
        this.bottomSpecs = bottomSpecs;
        this.iterationMatchers = iterationMatchers;
        this.verdictWhenMatched = verdictWhenMatched;
        this.skipReason = skipReason;
    }

    /**
     * Iteration matchers declared on the annotation. Empty means "all iterations".
     */
    public String[] iterationMatchers(A annotation) {
        return iterationMatchers.apply(annotation);
    }

    /**
     * Reason for a {@link Verdict#SKIP} decision. Empty when no reason is associated.
     * <p>
     * Callers must only invoke this after {@link #decide} returned {@link Verdict#SKIP}.
     */
    public String skipReason(A annotation) {
        return skipReason.apply(annotation);
    }

    /**
     * What verdict to produce when the gated Gradle mode is active and the annotation
     * targets the test: either {@link Verdict#SKIP} (for {@code @UnsupportedWith*}, and
     * for {@code @ToBeFixedFor*} when {@code skip} is set) or {@link Verdict#EXPECT_FAILURE}
     * (for {@code @ToBeFixedFor*} otherwise).
     */
    public Verdict verdictWhenMatched(A annotation) {
        return verdictWhenMatched.apply(annotation);
    }

    /**
     * Decide what should happen given the {@code annotation}, the {@code bottomSpecName}
     * (simple Spock spec name, or fully-qualified JUnit class name), and the
     * {@code iterationName} (may be {@code null} at spec-level visits, before any
     * iteration is known).
     */
    public Verdict decide(A annotation, String bottomSpecName, @Nullable String iterationName) {
        if (!mode.isActive()) {
            return Verdict.RUN;
        }
        if (!matchesAll(bottomSpecs.apply(annotation), iterationMatchers.apply(annotation), bottomSpecName, iterationName)) {
            return Verdict.RUN;
        }
        return verdictWhenMatched.apply(annotation);
    }

    /**
     * Whether a spec-level {@link Verdict#RUN} verdict could still flip per iteration —
     * i.e. the mode is active, the bottom spec matches, and {@code iterationMatchers} is non-empty.
     */
    public boolean requiresPerIterationCheck(A annotation, String bottomSpecName) {
        return mode.isActive()
            && matchesBottomSpec(bottomSpecs.apply(annotation), bottomSpecName)
            && !isAllIterations(iterationMatchers.apply(annotation));
    }

    /**
     * Display name of the gated Gradle mode, e.g. {@code "Configuration Cache"}.
     * Surfaced in skip messages and expected-failure logs.
     */
    public String gradleMode() {
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

}
