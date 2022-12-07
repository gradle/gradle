/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.configurationcache

import java.util.Stack


/**
 * A tool for checking if a specific problem has already been reported in the current dynamic call in the dynamic calls stack.
 * A problem is identified using a key object.
 * The implementation should be thread-safe and should support tracking problems in multiple threads, each with its own call stack.
 */
interface DynamicCallProblemReporting {
    /**
     * Begin tracking a new dynamic call on the call stack, with no problems reported in it initially.
     * The [entryPoint] is stored and checked in [leaveDynamicCall] later.
     */
    fun enterDynamicCall(entryPoint: Any)

    /**
     * End tracking a dynamic call.
     * The [entryPoint] should match the one passed to [enterDynamicCall].
     */
    fun leaveDynamicCall(entryPoint: Any)

    /**
     * Checks if the problem identified by [problemKey] has already been reported in the current dynamic call.
     * Side effect: marks [problemKey] as a *reported* problem if it has not been reported yet.
     *
     * @return a value saying whether this problem has not been reported yet in the current dynamic call.
     */
    fun unreportedProblemInCurrentCall(problemKey: Any): Boolean
}


class DefaultDynamicCallProblemReporting : DynamicCallProblemReporting {
    private
    class CallEntry(val entryPoint: Any) {
        val problemsReportedInCurrentCall: MutableSet<Any> = HashSet(1)
    }

    private
    class State {
        val callStack = Stack<CallEntry>()
    }

    private
    val threadLocalState = ThreadLocal.withInitial { State() }

    override fun enterDynamicCall(entryPoint: Any) {
        currentThreadState.callStack.push(CallEntry(entryPoint))
    }

    override fun leaveDynamicCall(entryPoint: Any) {
        val innermostCall = currentThreadState.callStack.pop()
        check(entryPoint == innermostCall.entryPoint) { "Mismatched enter-leave calls in DynamicCallProjectIsolationProblemReporting" }
    }

    override fun unreportedProblemInCurrentCall(problemKey: Any): Boolean {
        val currentThreadCallStack = currentThreadState.callStack
        check(currentThreadCallStack.isNotEmpty()) { "Expected unreportedProblemInCurrentCall to be called after enterDynamicCall" }
        return currentThreadCallStack.peek().problemsReportedInCurrentCall.add(problemKey)
    }

    private
    val currentThreadState: State
        get() = threadLocalState.get()
}
