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
import java.util.Stack;

/**
 * Tracks the call stacks of instrumented Groovy methods, maintaining a call per thread. <p>
 *
 * A call is registered with {@link InstrumentedGroovyCallsTracker#enterCall} and unregistered {@link InstrumentedGroovyCallsTracker#leaveCall}, which should match exactly. <p>
 *
 * In between, a third party may query this structure with {@link InstrumentedGroovyCallsTracker#findCallerForCurrentCallIfNotIntercepted} to check if the current innermost call
 * matches a method or property and has not been intercepted yet. <p>
 *
 * If it satisfies, then the third party may mark the call as intercepted with {@link InstrumentedGroovyCallsTracker#markCurrentCallAsIntercepted}, so that other
 * parties will not be able to match the call.
 */
@NonNullApi
public class InstrumentedGroovyCallsTracker {
    private static final ThreadLocal<Stack<EntryPointCallSite>> CURRENT_CALL_STACK_PER_THREAD = ThreadLocal.withInitial(Stack::new);

    /**
     * Registers the current call in the thread-specific instrumented call stack.
     * @return an entry point call site token that must later be passed to {@link InstrumentedGroovyCallsTracker#leaveCall}
     */
    public static EntryPointCallSite enterCall(String callerClassName, String callableName, CallKind callKind) {
        EntryPointCallSite entryPoint = new EntryPointCallSite(callableName, callerClassName, callKind);
        CURRENT_CALL_STACK_PER_THREAD.get().push(entryPoint);
        return entryPoint;
    }

    /**
     * Unregisters the instrumented call site. The entry point must be the instance returned from the {@link InstrumentedGroovyCallsTracker#enterCall}
     * @throws IllegalStateException if the passed entry point token does not match the current innermost instrumented call tracked for the thread.
     */
    public static void leaveCall(EntryPointCallSite entryPoint) {
        Stack<EntryPointCallSite> currentCallStack = CURRENT_CALL_STACK_PER_THREAD.get();
        EntryPointCallSite top = currentCallStack.peek();
        if (top != entryPoint) {
            throwMismatchedCallException(entryPoint, top);
        }
        currentCallStack.pop();
    }

    private static void throwMismatchedCallException(EntryPointCallSite entryPoint, EntryPointCallSite top) {
        throw new IllegalStateException(
            "Illegal state of the instrumented Groovy call tracker. " +
                "Expected the call to " + entryPoint.getCallableName() + " from " + entryPoint.getCallerClassName() + " on top, " +
                "got a call to " + top.getCallableName() + " from " + top.getCallerClassName());
    }

    /**
     * Checks if the current thread's innermost instrumented call matches the name and call kind, and has not been marked as intercepted yet.
     *
     * @return the class name of the caller, as per {@link Class#getName()}, or null if the call does not match or has already been marked as intercepted
     */
    // TODO maybe also match the args
    @Nullable
    public static String findCallerForCurrentCallIfNotIntercepted(String callableName, CallKind kind) {
        Stack<EntryPointCallSite> callStack = CURRENT_CALL_STACK_PER_THREAD.get();
        if (callStack.isEmpty()) {
            return null;
        }

        EntryPointCallSite top = callStack.peek();
        if (!callableName.equals(top.callableName)) {
            return null;
        }
        if (kind != top.kind) {
            return null;
        }
        if (top.isMatchedAtDispatchSite()) {
            return null;
        }
        return top.callerClassName;
    }

    /**
     * Matches the current thread's innermost instrumented call as intercepted, after which it cannot be discovered with
     * {@link InstrumentedGroovyCallsTracker#findCallerForCurrentCallIfNotIntercepted}.
     *
     * @throws IllegalStateException if the provided callable name and kind do not match the current thread's innermost call, or if the call has already been marked as intercepted.
     * The caller should therefore check that {@link InstrumentedGroovyCallsTracker#findCallerForCurrentCallIfNotIntercepted} finds a match.
     */
    public static void markCurrentCallAsIntercepted(String callableName, CallKind kind) {
        String result = findCallerForCurrentCallIfNotIntercepted(callableName, kind);
        Stack<EntryPointCallSite> entryPointCallSites = CURRENT_CALL_STACK_PER_THREAD.get();
        if (result == null) {
            throw new IllegalStateException(
                "Failed to match the current call: tried to intercept " + callableName + ", but " +
                    (entryPointCallSites.empty() ? "there is no current call" : "the current call is " + entryPointCallSites.peek().callableName)
            );
        }
        entryPointCallSites.peek().markedAsMatchedAtDispatchSite();
    }

    @NonNullApi
    public static class EntryPointCallSite {
        private final String callableName;
        private final String callerClassName;
        private final CallKind kind;
        private boolean isMatchedAtDispatchSite = false;

        public EntryPointCallSite(String callableName, String callerClassName, CallKind kind) {
            this.callableName = callableName;
            this.callerClassName = callerClassName;
            this.kind = kind;
        }

        public String getCallableName() {
            return callableName;
        }

        public String getCallerClassName() {
            return callerClassName;
        }

        public CallKind getKind() {
            return kind;
        }

        public boolean isMatchedAtDispatchSite() {
            return isMatchedAtDispatchSite;
        }

        public void markedAsMatchedAtDispatchSite() {
            if (isMatchedAtDispatchSite) {
                throw new IllegalStateException("Cannot match a single call more than once");
            }
            isMatchedAtDispatchSite = true;
        }
    }

    @NonNullApi
    public enum CallKind {
        GET_PROPERTY, SET_PROPERTY, INVOKE_METHOD
    }
}
