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
 * <p>The cache is keyed on the registered script the location resolves to: the first script at or below
 * the located frame (the analyser sees through non-script frames to the calling script). Captures that
 * reach no script are not cached, because a non-script frame's location depends on the frames below it,
 * so the same frame can resolve to different scripts, or to none, in different stacks. That is what keeps
 * distinct call sites sharing a non-script helper frame from being conflated.</p>
 *
 * <p>The cached value is reduced at both ends: leading Gradle machinery above the anchor is dropped, and
 * collection stops at the Gradle boundary below it. It carries the location and some surrounding context,
 * but is not a complete stack.</p>
 */
class DefaultBoundedCallerStackCapturer implements BoundedCallerStackCapturer {

    // Walk cap when no user frame. Above measured depth-to-user (~13-37 on gradle/gradle and nowinandroid).
    private static final int MAX_DEPTH = 50;

    // StackWalker buffer hint. Keep small: big hint fetches a whole batch up front, mostly wasted.
    private static final int ESTIMATE_DEPTH = 8;

    // Runaway cap. Evicted site just re-captures, still correct.
    private static final int MAX_CACHED_SITES = 10_000;

    private static final StackWalker WALKER = StackWalker.getInstance(EnumSet.noneOf(StackWalker.Option.class), ESTIMATE_DEPTH);

    private final RegisteredScripts registeredScripts;

    private final Cache<String, StackTraceElement[]> reducedStackBySite =
        CacheBuilder.newBuilder().maximumSize(MAX_CACHED_SITES).build();

    DefaultBoundedCallerStackCapturer(RegisteredScripts registeredScripts) {
        this.registeredScripts = registeredScripts;
    }

    @Nullable
    @Override
    public Throwable captureCallerStack() {
        StackTraceElement[] reducedStack = WALKER.walk(this::cachedReducedStack);
        return reducedStack == null ? null : new BoundedStackHolder(reducedStack);
    }

    @VisibleForTesting
    StackTraceElement @Nullable [] cachedReducedStack(Stream<StackFrame> frames) {
        BoundedWalk walk = new BoundedWalk();
        Iterator<StackFrame> iterator = frames.iterator();
        for (int depth = 0; depth < MAX_DEPTH && iterator.hasNext(); depth++) {
            StackTraceElement[] cached = walk.accept(iterator.next());
            if (cached != null) {
                return cached; // anchor already seen: reuse it, skip the rest of the walk
            }
            if (walk.reachedBoundary()) {
                break;
            }
        }
        return walk.reducedStack();
    }

    private boolean isRegisteredScript(StackTraceElement element) {
        String fileName = element.getFileName();
        return fileName != null && registeredScripts.scriptFor(fileName) != null;
    }

    /**
     * A single bounded walk: collects user frames from the first one down to the Gradle boundary, tracking the
     * anchor (the located frame, upgraded to the first registered script below it) the location and cache key come from.
     */
    private final class BoundedWalk {
        @Nullable
        private List<StackTraceElement> userFrames; // null until the first user frame
        private int anchorIndex = -1;               // the frame the location comes from
        private boolean anchoredOnScript;           // once true, the anchor is final
        private boolean atBoundary;

        StackTraceElement @Nullable [] accept(StackFrame frame) {
            String className = frame.getClassName();
            if (userFrames == null && InternalStackTraceClassifier.isInternal(className)) {
                return null; // leading machinery, before any user frame
            }
            StackTraceElement element = frame.toStackTraceElement();
            if (userFrames == null) {
                userFrames = new ArrayList<>(MAX_DEPTH);
            }
            userFrames.add(element);
            atBoundary = InternalStackTraceClassifier.isGradleCall(className);
            return tryAnchor(element, className);
        }

        private StackTraceElement @Nullable [] tryAnchor(StackTraceElement element, String className) {
            if (anchoredOnScript || element.getLineNumber() < 0 || InternalStackTraceClassifier.isInternal(className)) {
                return null; // anchor is final, or this is not a user frame with a line
            }
            if (anchorIndex == -1) {
                anchorIndex = userFrames.size() - 1; // tentative anchor: the located frame, used to trim the stack
            }
            if (!isRegisteredScript(element)) {
                return null; // a non-script frame resolves via the frames below it, so never cache or reuse on it
            }
            StackTraceElement[] cached = reducedStackBySite.getIfPresent(siteKey(element));
            if (cached != null) {
                return cached;
            }
            anchorIndex = userFrames.size() - 1; // definitive anchor: the script the location resolves to
            anchoredOnScript = true;
            return null;
        }

        boolean reachedBoundary() {
            return atBoundary;
        }

        StackTraceElement @Nullable [] reducedStack() {
            if (userFrames == null) {
                return null; // no user frame
            }
            int start = anchorIndex == -1 ? 0 : anchorIndex;
            StackTraceElement[] reduced = userFrames.subList(start, userFrames.size()).toArray(new StackTraceElement[0]);
            if (anchoredOnScript) {
                reducedStackBySite.put(siteKey(userFrames.get(anchorIndex)), reduced); // only script anchors are safe to cache
            }
            return reduced;
        }
    }

    private static String siteKey(StackTraceElement element) {
        return element.getClassName() + '#' + element.getMethodName() + '@' + element.getLineNumber();
    }

    @VisibleForTesting
    long cachedSiteCount() {
        return reducedStackBySite.size();
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
