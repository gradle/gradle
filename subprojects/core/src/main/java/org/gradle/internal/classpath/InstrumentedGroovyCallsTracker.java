/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.internal.classpath;

import org.gradle.api.NonNullApi;

import javax.annotation.Nullable;

/**
 * Tracks the call stacks of instrumented Groovy methods, maintaining a call stack per thread. <p>
 *
 * A call is registered with {@link InstrumentedGroovyCallsTracker#enterCall} and unregistered {@link InstrumentedGroovyCallsTracker#leaveCall}, which should match each other exactly. <p>
 *
 * In between, a third party may query this structure with {@link InstrumentedGroovyCallsTracker#findCallerForCurrentCallIfNotIntercepted} to check if the current innermost call
 * matches a method or property and has not been intercepted yet. <p>
 *
 * If it satisfies, then the third party may mark the call as intercepted with {@link InstrumentedGroovyCallsTracker#markCurrentCallAsIntercepted}, so that other
 * parties will not be able to match the call.
 */
@NonNullApi
public interface InstrumentedGroovyCallsTracker {
    /**
     * Registers the current call in the instrumented calls stack.
     * @return an entry point call site token that must later be passed to {@link InstrumentedGroovyCallsTracker#leaveCall}
     */
    EntryPointCallSite enterCall(String callerClassName, String callableName, CallKind callKind);

    /**
     * Unregisters the instrumented call site. The entry point must be the instance returned from the {@link InstrumentedGroovyCallsTracker#enterCall}
     * @throws IllegalStateException if the passed entry point token does not match the current innermost instrumented call tracked for the thread.
     */
    void leaveCall(EntryPointCallSite entryPoint);

    /**
     * Checks if the current thread's innermost instrumented call matches the name and call kind, and has not been marked as intercepted yet.
     *
     * @return the class name of the caller, as per {@link Class#getName()}, or null if the call does not match or has already been marked as intercepted
     */
    // TODO maybe also match the args
    @Nullable
    String findCallerForCurrentCallIfNotIntercepted(String callableName, CallKind kind);

    /**
     * Matches the current thread's innermost instrumented call as intercepted, after which it cannot be discovered with
     * {@link InstrumentedGroovyCallsTracker#findCallerForCurrentCallIfNotIntercepted}.
     *
     * @throws IllegalStateException if the provided callable name and kind do not match the current thread's innermost call, or if the call has already been marked as intercepted.
     * The caller should therefore check that {@link InstrumentedGroovyCallsTracker#findCallerForCurrentCallIfNotIntercepted} finds a match.
     */
    void markCurrentCallAsIntercepted(String callableName, CallKind kind);

    @NonNullApi
    interface EntryPointCallSite {
    }

    @NonNullApi
    enum CallKind {
        GET_PROPERTY, SET_PROPERTY, INVOKE_METHOD
    }
}
