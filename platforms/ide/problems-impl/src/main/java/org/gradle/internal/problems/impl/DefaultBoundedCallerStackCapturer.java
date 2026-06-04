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

package org.gradle.internal.problems.impl;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.gradle.internal.problems.BoundedCallerStackCapturer;
import org.gradle.internal.problems.failure.InternalStackTraceClassifier;
import org.jspecify.annotations.Nullable;

import java.lang.StackWalker.StackFrame;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

/**
 * {@link StackWalker}-based capture that resolves each distinct call site once and reuses the result.
 *
 * <p>Resolving stack frames is the dominant cost, and the same call site always yields the same
 * location, so the resolved prefix is cached per site. The expensive resolution is then paid once per
 * site rather than once per problem, which is what keeps capturing locations past the cap affordable.</p>
 *
 * <p>The cache is keyed on the located frame (the first user frame with a line) because that is the
 * frame the location is taken from. Keying on the nearest user frame instead would conflate distinct
 * sites that happen to share a line-less synthetic nearest frame, attributing one to the other.</p>
 */
public class DefaultBoundedCallerStackCapturer implements BoundedCallerStackCapturer {

    // Walk cap when no user frame. Above measured depth-to-user (~13-37).
    private static final int MAX_DEPTH = 50;

    // StackWalker buffer hint. Keep small: big hint fetches a whole batch up front, mostly wasted.
    private static final int ESTIMATE_DEPTH = 8;

    // Runaway cap. Evicted site just re-captures, still correct.
    private static final int MAX_CACHED_SITES = 10_000;

    private static final StackWalker WALKER = StackWalker.getInstance(EnumSet.noneOf(StackWalker.Option.class), ESTIMATE_DEPTH);

    private final Cache<String, StackTraceElement[]> prefixBySite =
        CacheBuilder.newBuilder().maximumSize(MAX_CACHED_SITES).build();

    @Nullable
    @Override
    public Throwable captureCallerStack() {
        StackTraceElement[] prefix = WALKER.walk(this::cachedBoundedPrefix);
        return prefix == null ? null : new BoundedStackHolder(prefix);
    }

    @VisibleForTesting
    StackTraceElement @Nullable [] cachedBoundedPrefix(Stream<StackFrame> frames) {
        Iterator<StackFrame> iterator = frames.iterator();
        List<StackTraceElement> prefix = null; // from first user frame
        String site = null;                    // located-frame key (first user frame with a line)
        for (int depth = 0; depth < MAX_DEPTH && iterator.hasNext(); depth++) {
            StackFrame frame = iterator.next();
            String className = frame.getClassName();
            if (prefix == null && InternalStackTraceClassifier.isInternal(className)) {
                continue; // skip leading machinery, no resolve
            }
            StackTraceElement element = frame.toStackTraceElement();
            if (prefix == null) {
                prefix = new ArrayList<>(MAX_DEPTH);
            }
            prefix.add(element);
            if (site == null && element.getLineNumber() >= 0 && !InternalStackTraceClassifier.isInternal(className)) {
                // Located frame: where the location comes from. Key on it; hit reuses its prefix.
                site = siteKey(element);
                StackTraceElement[] cached = prefixBySite.getIfPresent(site);
                if (cached != null) {
                    return cached;
                }
            }
            if (InternalStackTraceClassifier.isGradleCall(className)) {
                break; // include Gradle boundary, stop
            }
        }
        if (prefix == null) {
            return null; // no user frame
        }
        StackTraceElement[] result = prefix.toArray(new StackTraceElement[0]);
        if (site != null) {
            prefixBySite.put(site, result);
        }
        return result;
    }

    private static String siteKey(StackTraceElement element) {
        return element.getClassName() + '#' + element.getMethodName() + '@' + element.getLineNumber();
    }

    @VisibleForTesting
    long cachedSiteCount() {
        return prefixBySite.size();
    }

    // Holds a stack trace, skips costly native fillInStackTrace
    private static final class BoundedStackHolder extends Exception {
        BoundedStackHolder(StackTraceElement[] stackTrace) {
            super();
            setStackTrace(stackTrace);
        }

        @Override
        public synchronized Throwable fillInStackTrace() {
            return this;
        }
    }
}
