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

@NonNullApi
public class DefaultInstrumentedGroovyCallsTracker implements InstrumentedGroovyCallsTracker {

    private final Stack<EntryPointCallSiteImpl> callSiteStack = new Stack<>();

    @NonNullApi
    private static class EntryPointCallSiteImpl implements EntryPointCallSite {
        private final String callableName;
        private final String callerClassName;
        private final CallKind kind;
        private boolean isMatchedAtDispatchSite = false;

        public EntryPointCallSiteImpl(String callableName, String callerClassName, CallKind kind) {
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

    /**
     * Registers the current call in the thread-specific instrumented call stack.
     * @return an entry point call site token that must later be passed to {@link InstrumentedGroovyCallsTracker#leaveCall}
     */
    @Override
    public EntryPointCallSite enterCall(String callerClassName, String callableName, CallKind callKind) {
        EntryPointCallSiteImpl entryPoint = new EntryPointCallSiteImpl(callableName, callerClassName, callKind);
        callSiteStack.push(entryPoint);
        return entryPoint;
    }

    @Override
    public void leaveCall(EntryPointCallSite entryPoint) {
        if (callSiteStack.isEmpty()) {
            throwMismatchedLeaveCallException(entryPoint);
        }
        EntryPointCallSite top = callSiteStack.peek();
        if (top != entryPoint) {
            throwMismatchedLeaveCallException(entryPoint);
        }
        callSiteStack.pop();
    }

    private void throwMismatchedLeaveCallException(EntryPointCallSite entryPoint) {
        if (!(entryPoint instanceof EntryPointCallSiteImpl)) {
            throw new IllegalArgumentException("Unexpected entry point call site.");
        }
        if (callSiteStack.isEmpty()) {
            throw new IllegalStateException("leaveCall invoked with an empty call stack");
        }

        EntryPointCallSiteImpl entryPointImpl = (EntryPointCallSiteImpl) entryPoint;
        EntryPointCallSiteImpl top = callSiteStack.peek();
        throw new IllegalStateException(
            "Illegal state of the instrumented Groovy call tracker. " +
                "Expected the call to " + entryPointImpl.getCallableName() + " from " + entryPointImpl.getCallerClassName() + " on top, " +
                "got a call to " + top.getCallableName() + " from " + top.getCallerClassName());
    }

    @Nullable
    @Override
    public String findCallerForCurrentCallIfNotIntercepted(String callableName, CallKind kind) {
        if (callSiteStack.isEmpty()) {
            return null;
        }

        EntryPointCallSiteImpl top = callSiteStack.peek();
        if (!callableName.equals(top.getCallableName())) {
            return null;
        }
        if (kind != top.getKind()) {
            return null;
        }
        if (top.isMatchedAtDispatchSite()) {
            return null;
        }
        return top.getCallerClassName();
    }

    @Override
    public void markCurrentCallAsIntercepted(String callableName, CallKind kind) {
        String result = findCallerForCurrentCallIfNotIntercepted(callableName, kind);
        if (result == null) {
            throw new IllegalStateException(
                "Failed to match the current call: tried to intercept " + callableName + ", but " +
                    (callSiteStack.empty() ? "there is no current call" : "the current call is " + callSiteStack.peek().getCallableName())
            );
        }
        callSiteStack.peek().markedAsMatchedAtDispatchSite();
    }
}
